package com.freezify.recipes.internal;

import com.freezify.recipes.RecipeScorer;
import com.freezify.recipes.Recipe;
import com.freezify.recipes.RecipeCatalog;
import com.freezify.common.ApiException;
import com.freezify.common.PageResponse;
import com.freezify.common.Today;
import com.freezify.expiration.ExpirationPriority;
import com.freezify.food.Food;
import com.freezify.food.FoodCatalog;
import com.freezify.food.Unit;
import com.freezify.households.HouseholdAccess;
import com.freezify.inventory.HouseholdStock;
import com.freezify.inventory.HouseholdStock.StockItem;
import com.freezify.recipes.RecipeEvents.RecipeCooked;
import com.freezify.recipes.RecipeEvents.RecipeViewed;
import com.freezify.recipes.Recipe.Course;
import com.freezify.recipes.Recipe.Difficulty;
import com.freezify.recipes.RecipeScorer.FoodStock;
import com.freezify.recipes.RecipeScorer.IngredientMatch;
import com.freezify.recipes.RecipeScorer.Scored;
import com.freezify.recipes.internal.RecipeViews.IngredientView;
import com.freezify.recipes.internal.RecipeViews.MatchedIngredient;
import com.freezify.recipes.internal.RecipeViews.Recommendation;
import com.freezify.recipes.internal.RecipeViews.RecipeDetail;
import com.freezify.recipes.internal.RecipeViews.RecipeSummary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@ConfigurationProperties("freezify.recipes")
record RecipeProperties(RecipeScorer.Weights weights) {}

@Service
public class RecipeService implements RecipeCatalog {

    /**
     * Which recipes of the catalog to list.
     *
     * @param householdId when given, what that household does not eat is left out
     */
    public record Filter(
            @Nullable String text,
            @Nullable Integer maxMinutes,
            @Nullable Difficulty difficulty,
            @Nullable Course course,
            @Nullable UUID householdId) {}

    private final RecipeBook book;
    private final CookedRecipeRepository cooked;
    private final HouseholdDietRepository diets;
    private final HouseholdAccess access;
    private final HouseholdStock stock;
    private final FoodCatalog catalog;
    private final RecipeScorer scorer;
    private final ApplicationEventPublisher events;
    private final Today today;
    private final Clock clock;

    RecipeService(
            RecipeBook book,
            CookedRecipeRepository cooked,
            HouseholdDietRepository diets,
            HouseholdAccess access,
            HouseholdStock stock,
            FoodCatalog catalog,
            RecipeProperties properties,
            ApplicationEventPublisher events,
            Today today,
            Clock clock) {
        this.book = book;
        this.cooked = cooked;
        this.diets = diets;
        this.access = access;
        this.stock = stock;
        this.catalog = catalog;
        this.scorer = new RecipeScorer(properties.weights());
        this.events = events;
        this.today = today;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<RecipeSummary> list(Filter filter, UUID userId, String language, int page, int size) {
        Diet diet = filter.householdId() == null ? Diet.none() : dietOf(filter.householdId(), userId);
        String wanted = filter.text() == null ? "" : FoodCatalog.normalize(filter.text());
        List<Recipe> matching = book.all().stream()
                .filter(diet::allows)
                .filter(recipe -> wanted.isEmpty()
                        || FoodCatalog.normalize(recipe.name(language)).contains(wanted))
                .filter(recipe -> filter.maxMinutes() == null || recipe.totalMinutes() <= filter.maxMinutes())
                .filter(recipe -> filter.difficulty() == null || recipe.difficulty() == filter.difficulty())
                .filter(recipe -> filter.course() == null || recipe.course() == filter.course())
                .sorted(Comparator.comparing(recipe -> FoodCatalog.normalize(recipe.name(language))))
                .toList();
        List<RecipeSummary> items = matching.stream()
                .skip((long) page * size)
                .limit(size)
                .map(recipe -> RecipeSummary.of(recipe, language))
                .toList();
        return new PageResponse<>(items, page, size, matching.size(), (matching.size() + size - 1) / size);
    }

    @Transactional(readOnly = true)
    public RecipeDetail get(UUID recipeId, UUID userId, String language) {
        Recipe recipe = require(recipeId);
        events.publishEvent(new RecipeViewed(userId, recipeId));
        return new RecipeDetail(
                RecipeSummary.of(recipe, language),
                recipe.ingredients().stream()
                        .map(ingredient -> new IngredientView(
                                ingredient.foodId(),
                                foodName(ingredient.foodId(), language),
                                ingredient.quantity().amount(),
                                ingredient.quantity().unit(),
                                ingredient.staple()))
                        .toList(),
                recipe.steps(language));
    }

    /**
     * The recipes that suit what the household has today, the best first. A recipe is only offered when the
     * household has at least one of its ingredients; food past its date is never counted as available.
     */
    @Transactional(readOnly = true)
    public List<Recommendation> recommend(UUID householdId, UUID userId, String language, int limit) {
        Diet diet = dietOf(householdId, userId);
        LocalDate day = today.date();
        Map<UUID, FoodStock> available = availableFood(householdId, day);
        Map<UUID, LocalDate> lastCooked = lastCooked(householdId);

        return book.all().stream()
                // A hard filter, applied before anything is scored: what the household does not eat is never offered.
                .filter(diet::allows)
                .map(recipe -> scorer.score(recipe, available, lastCooked.get(recipe.id()), day))
                .filter(Scored::usesSomethingAtHome)
                // The name breaks ties, so that the order never depends on chance.
                .sorted(Comparator.comparingDouble(Scored::score)
                        .reversed()
                        .thenComparing(scored -> scored.recipe().name(language)))
                .limit(limit)
                .map(scored -> toRecommendation(scored, language, day))
                .toList();
    }

    @Override
    public Optional<Recipe> find(UUID recipeId) {
        return book.find(recipeId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Recipe> eatenBy(UUID householdId) {
        Diet diet = diets.findById(householdId).map(HouseholdDietEntity::toDiet).orElseGet(Diet::none);
        return book.all().stream().filter(diet::allows).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, LocalDate> lastCooked(UUID householdId) {
        return cooked.lastCookedByRecipe(householdId).stream()
                .collect(Collectors.toMap(
                        CookedRecipeRepository.LastCooked::getRecipeId,
                        CookedRecipeRepository.LastCooked::getLastCooked));
    }

    /** What the household does not eat. Any member can see it. */
    @Transactional(readOnly = true)
    public Diet dietOf(UUID householdId, UUID userId) {
        access.requireMember(householdId, userId);
        return diets.findById(householdId).map(HouseholdDietEntity::toDiet).orElseGet(Diet::none);
    }

    /** Any member can change it: it describes the shared kitchen, not a person. */
    @Transactional
    public Diet updateDiet(UUID householdId, UUID userId, Diet diet) {
        access.requireMember(householdId, userId);
        HouseholdDietEntity entity = diets.findById(householdId).orElseGet(() -> new HouseholdDietEntity(householdId));
        return diets.save(entity.apply(diet, userId, clock.instant())).toDiet();
    }

    /**
     * Records that the household cooked the recipe today. It does not touch the inventory: what was used is
     * still consumed item by item, by the people who know how much they used.
     */
    @Transactional
    public void markCooked(UUID householdId, UUID userId, UUID recipeId) {
        access.requireMember(householdId, userId);
        require(recipeId);
        // Saying it twice on the same day, or two members saying it at once, counts once.
        if (cooked.recordOnce(householdId, recipeId, userId, today.date(), clock.instant()) == 1) {
            events.publishEvent(new RecipeCooked(userId, householdId, recipeId));
        }
    }

    /** What the household can still eat, by catalog food. Items that are not in the catalog cannot be matched. */
    private Map<UUID, FoodStock> availableFood(UUID householdId, LocalDate day) {
        Map<UUID, FoodStock> byFood = new HashMap<>();
        for (StockItem item : stock.of(householdId)) {
            if (item.expirationDate() != null && item.expirationDate().isBefore(day)) {
                continue;
            }
            UUID foodId = item.foodId() != null
                    ? item.foodId()
                    : catalog.findByName(item.name()).map(Food::id).orElse(null);
            if (foodId == null) {
                continue;
            }
            byFood.merge(foodId, stockOf(item), RecipeService::plus);
        }
        return byFood;
    }

    private static FoodStock stockOf(StockItem item) {
        Unit base = item.quantity().unit().baseUnit();
        Map<Unit.Dimension, BigDecimal> amounts = new EnumMap<>(Unit.Dimension.class);
        amounts.put(base.dimension(), item.quantity().convertTo(base).amount());
        return new FoodStock(amounts, item.expirationDate(), item.estimated());
    }

    private static FoodStock plus(FoodStock one, FoodStock other) {
        Map<Unit.Dimension, BigDecimal> amounts = new EnumMap<>(one.amounts());
        other.amounts().forEach((dimension, amount) -> amounts.merge(dimension, amount, BigDecimal::add));
        boolean otherIsSooner = other.soonestExpiration() != null
                && (one.soonestExpiration() == null || other.soonestExpiration().isBefore(one.soonestExpiration()));
        FoodStock soonest = otherIsSooner ? other : one;
        return new FoodStock(amounts, soonest.soonestExpiration(), soonest.soonestEstimated());
    }

    private Recommendation toRecommendation(Scored scored, String language, LocalDate day) {
        return new Recommendation(
                RecipeSummary.of(scored.recipe(), language),
                round(scored.score()),
                new RecipeScorer.Factors(
                        round(scored.factors().ingredientMatch()),
                        round(scored.factors().expiryUrgency()),
                        round(scored.factors().convenience()),
                        round(scored.factors().novelty())),
                scored.ingredients().stream()
                        .map(match -> toMatchedIngredient(match, language, day))
                        .toList(),
                scored.daysSinceCooked());
    }

    private MatchedIngredient toMatchedIngredient(IngredientMatch match, String language, LocalDate day) {
        LocalDate expires = match.expirationDate();
        Long daysLeft = expires == null ? null : ExpirationPriority.daysLeft(expires, day);
        return new MatchedIngredient(
                match.ingredient().foodId(),
                foodName(match.ingredient().foodId(), language),
                match.ingredient().quantity().amount(),
                match.ingredient().quantity().unit(),
                match.ingredient().staple(),
                match.availability(),
                expires,
                match.estimated(),
                daysLeft,
                daysLeft == null ? null : ExpirationPriority.ofDaysLeft(daysLeft));
    }

    private String foodName(UUID foodId, String language) {
        return catalog.findById(foodId).map(food -> food.name(language)).orElse("");
    }

    private Recipe require(UUID recipeId) {
        return book.find(recipeId).orElseThrow(() -> ApiException.notFound("RECIPE_NOT_FOUND", "Recipe not found."));
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }
}
