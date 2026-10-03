package com.freezify.recipes.internal;

import com.freezify.ai.AiService;
import com.freezify.ai.AiService.Answer;
import com.freezify.ai.AiService.Ingredient;
import com.freezify.ai.AiService.RecipeRequest;
import com.freezify.ai.AiService.UsedIngredient;
import com.freezify.ai.AiService.WrittenRecipe;
import com.freezify.common.ApiException;
import com.freezify.common.Today;
import com.freezify.expiration.ExpirationPriority;
import com.freezify.food.Food;
import com.freezify.food.FoodCatalog;
import com.freezify.food.FoodTrait;
import com.freezify.food.Quantity;
import com.freezify.inventory.HouseholdStock;
import com.freezify.inventory.HouseholdStock.StockItem;
import com.freezify.recipes.RecipeEvents.RecipeGenerated;
import com.freezify.recipes.internal.RecipeViews.AvailableFood;
import com.freezify.recipes.internal.RecipeViews.GeneratedIngredient;
import com.freezify.recipes.internal.RecipeViews.GeneratedRecipe;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * "Create a recipe with what I have": a language model writes it, from the food the household has and does not
 * avoid, and only that. What it answers is checked twice: by the AI module against what it was given, and here
 * against the catalog, so that a recipe that names a food the household does not have (or does not eat) never
 * reaches anyone. Nothing is stored: the recipe is shown, not added to the catalog.
 */
@Service
public class RecipeGenerator {

    /** Salt, oil and water are assumed in any kitchen, as in the recipes of the catalog. */
    private static final List<String> STAPLES = List.of("salt", "olive-oil", "water");
    private static final int MAX_INGREDIENTS = 40;

    /** An ingredient offered to the model: food at home, merged by food, or a staple. */
    private record Offered(
            Ingredient ingredient, @Nullable UUID foodId, String name, @Nullable LocalDate date, boolean estimated) {}

    private final RecipeService recipes;
    private final HouseholdStock stock;
    private final FoodCatalog catalog;
    private final AiService ai;
    private final ApplicationEventPublisher events;
    private final Today today;

    RecipeGenerator(
            RecipeService recipes,
            HouseholdStock stock,
            FoodCatalog catalog,
            AiService ai,
            ApplicationEventPublisher events,
            Today today) {
        this.recipes = recipes;
        this.stock = stock;
        this.catalog = catalog;
        this.ai = ai;
        this.events = events;
        this.today = today;
    }

    /**
     * What a generated recipe may use, the most pressing first, without the staples. It is what the model is
     * given, so people can see it, and choose among the catalog foods what the recipe must use.
     */
    public List<AvailableFood> available(UUID householdId, UUID userId, String language) {
        Diet diet = recipes.dietOf(householdId, userId);
        return offer(householdId, diet, language, today.date()).stream()
                .filter(entry -> !entry.ingredient().staple())
                .map(entry -> new AvailableFood(
                        entry.foodId(),
                        entry.name(),
                        entry.ingredient().quantity().amount(),
                        entry.ingredient().quantity().unit(),
                        entry.ingredient().daysLeft(),
                        entry.estimated()))
                .toList();
    }

    /**
     * Not in a transaction: the model may take a while, and nothing is written.
     *
     * @param mustUse catalog foods the recipe has to use; each must be among the {@link #available} ones
     */
    public GeneratedRecipe generate(UUID householdId, UUID userId, String language, int servings, Set<UUID> mustUse) {
        // Checks membership too.
        Diet diet = recipes.dietOf(householdId, userId);
        if (!ai.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED", "No language model is configured.");
        }
        LocalDate day = today.date();
        List<Offered> offered = offer(householdId, diet, language, day);
        if (offered.stream().allMatch(entry -> entry.ingredient().staple())) {
            throw ApiException.conflict("NOTHING_TO_COOK_WITH", "There is no food at home that the household eats.");
        }
        List<String> mustUseKeys = new ArrayList<>();
        for (UUID foodId : mustUse) {
            Offered entry = offered.stream()
                    .filter(candidate -> !candidate.ingredient().staple() && foodId.equals(candidate.foodId()))
                    .findFirst()
                    .orElseThrow(() -> ApiException.conflict(
                            "FOOD_NOT_AVAILABLE",
                            "That food is not at home, is past its date or is not eaten in the household."));
            mustUseKeys.add(entry.ingredient().key());
        }

        RecipeRequest request = new RecipeRequest(
                language,
                servings,
                offered.stream().map(Offered::ingredient).toList(),
                diet.excluded().stream().map(trait -> trait.name().toLowerCase(Locale.ROOT)).toList(),
                mustUseKeys);
        Answer<WrittenRecipe> answer = ai.writeRecipe(request, userId);
        WrittenRecipe written = switch (answer.outcome()) {
            case OK -> answer.value();
            case NOT_CONFIGURED -> throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED", "No language model is configured.");
            case LIMIT_REACHED -> throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS, "AI_LIMIT_REACHED", "Today's allowance of AI requests is spent.");
            case FAILED -> throw unusable();
        };

        Map<String, Offered> byKey = new LinkedHashMap<>();
        offered.forEach(entry -> byKey.put(entry.ingredient().key(), entry));
        // The foods it uses, and the staples, which may season anything.
        List<Offered> allowed = offered.stream()
                .filter(entry -> entry.ingredient().staple() || written.ingredients().stream()
                        .anyMatch(used -> used.key().equals(entry.ingredient().key())))
                .toList();
        Set<UUID> allowedFoods = new HashSet<>();
        allowed.stream().filter(entry -> entry.foodId() != null).forEach(entry -> allowedFoods.add(entry.foodId()));
        List<String> allowedNames = allowed.stream().map(Offered::name).toList();
        if (FoodMentions.mentionsOtherFood(catalog.all(), allowedFoods, allowedNames, language, text(written))) {
            throw unusable();
        }

        List<GeneratedIngredient> ingredients = written.ingredients().stream()
                .map(used -> ingredient(used, byKey.get(used.key()), day))
                .sorted(Comparator.comparing(GeneratedIngredient::staple).thenComparing(GeneratedIngredient::optional))
                .toList();
        events.publishEvent(new RecipeGenerated(userId, householdId));
        return new GeneratedRecipe(
                written.title(),
                written.summary(),
                servings,
                written.minutes(),
                com.freezify.recipes.Recipe.Difficulty.valueOf(written.difficulty().name()),
                ingredients,
                written.steps());
    }

    /**
     * What the model may use: food in the house that is not past its date and that the household eats, the most
     * pressing first, plus the staples. Food that is not in the catalog has unknown contents, so it is only
     * offered to households that avoid nothing.
     */
    private List<Offered> offer(UUID householdId, Diet diet, String language, LocalDate day) {
        Set<FoodTrait> excluded = diet.excluded();
        Map<String, List<StockItem>> groups = new LinkedHashMap<>();
        for (StockItem item : stock.of(householdId)) {
            if (item.quantity().isZero() || (item.expirationDate() != null && item.expirationDate().isBefore(day))) {
                continue;
            }
            Food food = item.foodId() == null ? null : catalog.findById(item.foodId()).orElse(null);
            if (food == null ? !excluded.isEmpty() : !Collections.disjoint(excluded, food.traits())) {
                continue;
            }
            String group = (food == null ? "name:" + FoodCatalog.normalize(item.name()) : "food:" + food.id())
                    + "/" + item.quantity().unit().dimension();
            groups.computeIfAbsent(group, key -> new ArrayList<>()).add(item);
        }

        List<Offered> food = new ArrayList<>();
        for (List<StockItem> items : groups.values()) {
            StockItem first = items.getFirst();
            Quantity total = first.quantity();
            StockItem soonest = first;
            for (StockItem item : items.subList(1, items.size())) {
                total = total.plus(item.quantity());
                if (soonest.expirationDate() == null
                        || (item.expirationDate() != null && item.expirationDate().isBefore(soonest.expirationDate()))) {
                    soonest = item;
                }
            }
            String name = first.foodId() == null
                    ? first.name()
                    : catalog.findById(first.foodId()).map(found -> found.name(language)).orElse(first.name());
            Long daysLeft = soonest.expirationDate() == null ? null : ChronoUnit.DAYS.between(day, soonest.expirationDate());
            food.add(new Offered(
                    new Ingredient("", name, total, daysLeft, false),
                    first.foodId(),
                    name,
                    soonest.expirationDate(),
                    soonest.estimated()));
        }
        food.sort(Comparator.comparing((Offered entry) -> entry.ingredient().daysLeft(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Offered::name));

        List<Offered> offered = new ArrayList<>();
        for (Offered entry : food.subList(0, Math.min(food.size(), MAX_INGREDIENTS))) {
            Ingredient ingredient = entry.ingredient();
            offered.add(new Offered(
                    new Ingredient("i" + (offered.size() + 1), ingredient.name(), ingredient.quantity(), ingredient.daysLeft(), false),
                    entry.foodId(),
                    entry.name(),
                    entry.date(),
                    entry.estimated()));
        }
        int staples = 0;
        for (String slug : STAPLES) {
            Food staple = catalog.all().stream().filter(candidate -> candidate.slug().equals(slug)).findFirst().orElse(null);
            boolean atHome = staple != null && offered.stream().anyMatch(entry -> staple.id().equals(entry.foodId()));
            if (staple != null && !atHome && Collections.disjoint(excluded, staple.traits())) {
                String name = staple.name(language);
                offered.add(new Offered(
                        new Ingredient("s" + (++staples), name, Quantity.of("1", staple.defaultUnit()), null, true),
                        staple.id(),
                        name,
                        null,
                        false));
            }
        }
        return offered;
    }

    private static GeneratedIngredient ingredient(UsedIngredient used, Offered offered, LocalDate day) {
        Long daysLeft = offered.date() == null ? null : ChronoUnit.DAYS.between(day, offered.date());
        return new GeneratedIngredient(
                offered.foodId(),
                offered.name(),
                used.quantity().amount(),
                used.quantity().unit(),
                used.optional(),
                offered.ingredient().staple(),
                daysLeft,
                offered.date() == null ? null : ExpirationPriority.of(offered.date(), day),
                offered.estimated());
    }

    private static String text(WrittenRecipe written) {
        return written.title() + "\n" + written.summary() + "\n" + String.join("\n", written.steps());
    }

    private static ApiException unusable() {
        return new ApiException(
                HttpStatus.BAD_GATEWAY, "AI_UNAVAILABLE", "The language model did not give a usable recipe. Try again.");
    }
}
