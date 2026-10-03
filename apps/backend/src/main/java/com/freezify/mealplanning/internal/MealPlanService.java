package com.freezify.mealplanning.internal;

import com.freezify.common.ApiException;
import com.freezify.common.Today;
import com.freezify.food.Food;
import com.freezify.food.FoodCatalog;
import com.freezify.food.Quantity;
import com.freezify.food.Unit;
import com.freezify.households.HouseholdAccess;
import com.freezify.inventory.HouseholdStock;
import com.freezify.inventory.HouseholdStock.StockItem;
import com.freezify.mealplanning.MealPlanEvents.MealPlanChanged;
import com.freezify.mealplanning.MealPlanEvents.MealPlanCreated;
import com.freezify.mealplanning.PlanNeeds;
import com.freezify.mealplanning.internal.MealPlanViews.Generated;
import com.freezify.mealplanning.internal.MealPlanViews.MealPlanView;
import com.freezify.mealplanning.internal.MealPlanViews.PlannedIngredient;
import com.freezify.mealplanning.internal.MealPlanViews.PlannedMeal;
import com.freezify.mealplanning.internal.MealPlanViews.PlannedRecipe;
import com.freezify.mealplanning.internal.MealPlanViews.UnusedFood;
import com.freezify.mealplanning.internal.Pantry.Lot;
import com.freezify.mealplanning.internal.PlanGenerator.Choice;
import com.freezify.mealplanning.internal.PlanGenerator.Meal;
import com.freezify.mealplanning.internal.PlanGenerator.Planned;
import com.freezify.recipes.Recipe;
import com.freezify.recipes.RecipeCatalog;
import com.freezify.recipes.RecipeScorer;
import com.freezify.recipes.RecipeScorer.Availability;
import com.freezify.recipes.RecipeScorer.IngredientMatch;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@ConfigurationProperties("freezify.meal-planning")
record MealPlanningProperties(PlanGenerator.Weights weights) {}

@Service
public class MealPlanService implements PlanNeeds {

    /** Meals can be planned this far from today, in either direction. */
    static final int MAX_DAYS_AWAY = 366;

    private static final Comparator<MealPlanEntryEntity> IN_ORDER =
            Comparator.comparing(MealPlanEntryEntity::plannedOn).thenComparing(MealPlanEntryEntity::slot);

    private final MealPlanEntryRepository entries;
    private final HouseholdAccess access;
    private final HouseholdStock stock;
    private final RecipeCatalog recipes;
    private final FoodCatalog foods;
    private final PlanGenerator generator;
    private final ApplicationEventPublisher events;
    private final Today today;

    MealPlanService(
            MealPlanEntryRepository entries,
            HouseholdAccess access,
            HouseholdStock stock,
            RecipeCatalog recipes,
            FoodCatalog foods,
            MealPlanningProperties properties,
            ApplicationEventPublisher events,
            Today today) {
        this.entries = entries;
        this.access = access;
        this.stock = stock;
        this.recipes = recipes;
        this.foods = foods;
        this.generator = new PlanGenerator(
                properties.weights(),
                foodId -> foods.findById(foodId).map(Food::category).orElse(null));
        this.events = events;
        this.today = today;
    }

    /**
     * The week that contains {@code day}. Every meal from today on says what the household would have of each
     * ingredient on its day, once the meals planned before it have taken theirs.
     */
    @Transactional(readOnly = true)
    public MealPlanView week(UUID householdId, UUID userId, LocalDate day, String language) {
        access.requireMember(householdId, userId);
        requireNear(day);
        LocalDate weekStart = mondayOf(day);
        LocalDate weekEnd = weekStart.plusDays(6);
        LocalDate now = today.date();

        // Meals between today and the week take their ingredients too, so they are replayed with it.
        LocalDate from = weekStart.isBefore(now) ? weekStart : now;
        List<MealPlanEntryEntity> planned =
                new ArrayList<>(entries.findByHouseholdIdAndPlannedOnBetween(householdId, from, weekEnd));
        planned.sort(IN_ORDER);

        List<StockItem> items = stock.of(householdId);
        Pantry pantry = pantryOf(items, now);
        Set<RecipeCatalog.Cooked> cooked = recipes.cookedBetween(householdId, weekStart, weekEnd);
        // The first day each food is in a planned meal that uses an amount of it that cannot be counted.
        Map<UUID, LocalDate> uncountedUse = new HashMap<>();
        List<PlannedMeal> meals = new ArrayList<>();
        for (MealPlanEntryEntity entry : planned) {
            Recipe recipe = recipes.find(entry.recipeId()).orElse(null);
            if (recipe == null) {
                continue;
            }
            List<PlannedIngredient> ingredients = List.of();
            if (!entry.plannedOn().isBefore(now)) {
                List<IngredientMatch> matches = RecipeScorer.assess(
                                recipe, pantry.on(entry.plannedOn()), null, entry.plannedOn())
                        .ingredients();
                pantry.cook(recipe, entry.plannedOn());
                matches.stream()
                        .filter(match -> match.availability() == Availability.UNKNOWN_QUANTITY)
                        .forEach(match -> uncountedUse.putIfAbsent(match.ingredient().foodId(), entry.plannedOn()));
                ingredients = matches.stream()
                        .map(match -> toIngredient(match, language))
                        .toList();
            }
            if (!entry.plannedOn().isBefore(weekStart)) {
                meals.add(new PlannedMeal(
                        entry.id(),
                        entry.plannedOn(),
                        entry.slot(),
                        entry.origin(),
                        new PlannedRecipe(
                                recipe.id(),
                                recipe.name(language),
                                recipe.servings(),
                                recipe.totalMinutes(),
                                recipe.difficulty(),
                                recipe.contains().stream().sorted().toList()),
                        ingredients,
                        cooked.contains(new RecipeCatalog.Cooked(recipe.id(), entry.plannedOn()))));
            }
        }
        return new MealPlanView(
                weekStart, weekEnd, now, meals, unusedExpiring(items, uncountedUse, pantry, now, weekEnd));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Need> needs(UUID householdId, LocalDate from, LocalDate to) {
        LocalDate now = today.date();
        // The meals between today and `from` are cooked too: they take what they need first.
        List<MealPlanEntryEntity> planned =
                new ArrayList<>(entries.findByHouseholdIdAndPlannedOnBetween(householdId, now, to));
        planned.sort(IN_ORDER);
        Pantry pantry = pantryOf(stock.of(householdId), now);
        Map<UUID, Map<Unit, Quantity>> amounts = new HashMap<>();
        Map<UUID, LocalDate> firstNeeded = new HashMap<>();
        for (MealPlanEntryEntity entry : planned) {
            Recipe recipe = recipes.find(entry.recipeId()).orElse(null);
            if (recipe == null) {
                continue;
            }
            Map<UUID, Quantity> lacking = pantry.cook(recipe, entry.plannedOn());
            if (entry.plannedOn().isBefore(from)) {
                continue;
            }
            lacking.forEach((foodId, quantity) -> {
                amounts.computeIfAbsent(foodId, id -> new HashMap<>()).merge(quantity.unit(), quantity, Quantity::plus);
                firstNeeded.putIfAbsent(foodId, entry.plannedOn());
            });
        }
        List<Need> needs = new ArrayList<>();
        amounts.forEach((foodId, byUnit) -> byUnit.values()
                .forEach(quantity -> needs.add(new Need(foodId, quantity, firstNeeded.get(foodId)))));
        needs.sort(Comparator.comparing(Need::firstNeededOn).thenComparing(need -> need.foodId().toString()));
        return needs;
    }

    /** Puts a recipe in a meal, in place of whatever was there. */
    @Transactional
    public void choose(UUID householdId, UUID userId, LocalDate date, MealSlot slot, UUID recipeId) {
        access.requireMember(householdId, userId);
        requireNear(date);
        if (recipes.find(recipeId).isEmpty()) {
            throw ApiException.notFound("RECIPE_NOT_FOUND", "Recipe not found.");
        }
        boolean firstOfTheWeek = isEmptyWeek(householdId, date);
        entries.chooseManually(
                UUID.randomUUID(), householdId, date, slot.name(), recipeId, userId, today.now().toInstant());
        changed(householdId, userId, firstOfTheWeek);
    }

    @Transactional
    public void remove(UUID householdId, UUID userId, LocalDate date, MealSlot slot) {
        access.requireMember(householdId, userId);
        entries.delete(require(householdId, date, slot));
        changed(householdId, userId, false);
    }

    /** Moves a meal to another day or slot. If something is planned there, the two swap places. */
    @Transactional
    public void move(
            UUID householdId, UUID userId, LocalDate date, MealSlot slot, LocalDate toDate, MealSlot toSlot) {
        access.requireMember(householdId, userId);
        requireNear(toDate);
        MealPlanEntryEntity source = require(householdId, date, slot);
        if (date.equals(toDate) && slot == toSlot) {
            return;
        }
        boolean firstOfTheWeek = isEmptyWeek(householdId, toDate);
        entries.findByHouseholdIdAndPlannedOnAndSlot(householdId, toDate, toSlot)
                .ifPresentOrElse(
                        target -> {
                            // The rows stay where they are and exchange what they hold: no moment with two meals
                            // in the same slot.
                            UUID recipeId = target.recipeId();
                            MealOrigin origin = target.origin();
                            target.choose(source.recipeId(), source.origin());
                            source.choose(recipeId, origin);
                        },
                        () -> source.moveTo(toDate, toSlot));
        changed(householdId, userId, firstOfTheWeek);
    }

    /**
     * Fills the empty meals of the week that contains {@code day}, from today on. What members chose is never
     * touched; what the generator chose before is only replaced when asked to.
     */
    @Transactional
    public Generated generate(UUID householdId, UUID userId, LocalDate day, boolean replaceGenerated) {
        access.requireMember(householdId, userId);
        requireNear(day);
        LocalDate weekStart = mondayOf(day);
        LocalDate weekEnd = weekStart.plusDays(6);
        LocalDate now = today.date();
        if (weekEnd.isBefore(now)) {
            throw ApiException.conflict("WEEK_IN_THE_PAST", "A week that is over cannot be planned.");
        }
        boolean firstOfTheWeek = isEmptyWeek(householdId, weekStart);

        // The day before and the day after count for variety; meals between today and the week take their
        // ingredients before the week is planned.
        LocalDate from = weekStart.minusDays(1).isBefore(now) ? weekStart.minusDays(1) : now;
        List<MealPlanEntryEntity> around =
                entries.findByHouseholdIdAndPlannedOnBetween(householdId, from, weekEnd.plusDays(1));
        if (replaceGenerated) {
            List<MealPlanEntryEntity> replaced = around.stream()
                    .filter(entry -> entry.origin() == MealOrigin.GENERATED)
                    .filter(entry -> !entry.plannedOn().isBefore(weekStart)
                            && !entry.plannedOn().isAfter(weekEnd)
                            && !entry.plannedOn().isBefore(now))
                    .toList();
            entries.deleteAll(replaced);
            // The rows that take their place are inserted in this same transaction.
            entries.flush();
            around = around.stream().filter(entry -> !replaced.contains(entry)).toList();
        }

        Set<Meal> taken = new HashSet<>();
        List<Planned> kept = new ArrayList<>();
        for (MealPlanEntryEntity entry : around) {
            taken.add(new Meal(entry.plannedOn(), entry.slot()));
            recipes.find(entry.recipeId())
                    .ifPresent(recipe -> kept.add(new Planned(new Meal(entry.plannedOn(), entry.slot()), recipe)));
        }
        List<Meal> empty = new ArrayList<>();
        for (LocalDate date = weekStart.isBefore(now) ? now : weekStart; !date.isAfter(weekEnd); date = date.plusDays(1)) {
            for (MealSlot slot : MealSlot.values()) {
                Meal meal = new Meal(date, slot);
                if (!taken.contains(meal)) {
                    empty.add(meal);
                }
            }
        }
        // Lunch and dinner are main dishes: breakfasts and desserts are not planned.
        List<Recipe> candidates = recipes.eatenBy(householdId).stream()
                .filter(recipe -> recipe.course() == Recipe.Course.MAIN)
                .toList();

        List<Choice> choices = generator.generate(
                empty, kept, candidates, pantryOf(stock.of(householdId), now), recipes.lastCooked(householdId), now);
        int filled = 0;
        for (Choice choice : choices) {
            filled += entries.fillIfEmpty(
                    UUID.randomUUID(),
                    householdId,
                    choice.meal().date(),
                    choice.meal().slot().name(),
                    choice.recipe().id(),
                    userId,
                    today.now().toInstant());
        }
        if (replaceGenerated || filled > 0) {
            changed(householdId, userId, firstOfTheWeek && filled > 0);
        }
        return new Generated(filled, empty.size() - filled);
    }

    private void changed(UUID householdId, UUID userId, boolean firstOfTheWeek) {
        events.publishEvent(new MealPlanChanged(householdId, userId));
        if (firstOfTheWeek) {
            events.publishEvent(new MealPlanCreated(householdId, userId));
        }
    }

    private boolean isEmptyWeek(UUID householdId, LocalDate day) {
        LocalDate weekStart = mondayOf(day);
        return !entries.existsByHouseholdIdAndPlannedOnBetween(householdId, weekStart, weekStart.plusDays(6));
    }

    private MealPlanEntryEntity require(UUID householdId, LocalDate date, MealSlot slot) {
        return entries.findByHouseholdIdAndPlannedOnAndSlot(householdId, date, slot)
                .orElseThrow(() -> ApiException.notFound("MEAL_NOT_FOUND", "Nothing is planned for that meal."));
    }

    private void requireNear(LocalDate date) {
        if (Math.abs(ChronoUnit.DAYS.between(today.date(), date)) > MAX_DAYS_AWAY) {
            throw ApiException.badRequest("VALIDATION_ERROR", "The date is too far from today.");
        }
    }

    /**
     * What the household can still eat, by catalog food. Food past its date is never planned with; an item that
     * is not in the catalog cannot be matched with any recipe.
     */
    private Pantry pantryOf(List<StockItem> items, LocalDate now) {
        Map<UUID, List<Lot>> lots = new HashMap<>();
        for (StockItem item : items) {
            UUID foodId = foodIdOf(item);
            if (foodId == null || (item.expirationDate() != null && item.expirationDate().isBefore(now))) {
                continue;
            }
            Unit base = item.quantity().unit().baseUnit();
            lots.computeIfAbsent(foodId, id -> new ArrayList<>())
                    .add(new Lot(
                            item.itemId(),
                            base.dimension(),
                            item.quantity().convertTo(base).amount(),
                            item.expirationDate(),
                            item.estimated()));
        }
        return new Pantry(lots);
    }

    private @Nullable UUID foodIdOf(StockItem item) {
        return item.foodId() != null
                ? item.foodId()
                : foods.findByName(item.name()).map(Food::id).orElse(null);
    }

    /**
     * What is at home, expires between today and the end of the week, and the meals planned up to its date do
     * not use or do not use up: the food this plan would let go to waste.
     *
     * @param uncountedUse the first day each food is in a planned meal in a way that cannot be counted (the recipe
     *                     asks for pieces and the household has it by weight)
     * @param pantry       what is left once every planned meal has taken its ingredients
     */
    private List<UnusedFood> unusedExpiring(
            List<StockItem> items,
            Map<UUID, LocalDate> uncountedUse,
            Pantry pantry,
            LocalDate now,
            LocalDate weekEnd) {
        List<UnusedFood> unused = new ArrayList<>();
        for (StockItem item : items) {
            LocalDate expires = item.expirationDate();
            if (expires == null || expires.isBefore(now) || expires.isAfter(weekEnd)) {
                continue;
            }
            UUID foodId = foodIdOf(item);
            // An item that is not in the catalog is in no recipe: all of it is left.
            Quantity left = item.quantity();
            if (foodId != null) {
                // Meals after its date took nothing from it, so what is left now is what is left on its date.
                Unit base = item.quantity().unit().baseUnit();
                left = new Quantity(pantry.left(item.itemId()), base).convertTo(item.quantity().unit());
                LocalDate uncounted = uncountedUse.get(foodId);
                // In a meal, but how much of it the meal uses is not known: nothing is claimed about it.
                if (left.isZero() || (uncounted != null && !uncounted.isAfter(expires))) {
                    continue;
                }
            }
            unused.add(new UnusedFood(item.name(), left.amount(), left.unit(), expires, item.estimated()));
        }
        unused.sort(Comparator.comparing(UnusedFood::expirationDate).thenComparing(UnusedFood::name));
        return unused;
    }

    private PlannedIngredient toIngredient(IngredientMatch match, String language) {
        return new PlannedIngredient(
                match.ingredient().foodId(),
                foods.findById(match.ingredient().foodId())
                        .map(food -> food.name(language))
                        .orElse(""),
                match.ingredient().quantity().amount(),
                match.ingredient().quantity().unit(),
                match.ingredient().staple(),
                match.availability(),
                match.expirationDate(),
                match.estimated());
    }

    private static LocalDate mondayOf(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
