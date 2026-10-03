package com.freezify.shopping.internal;

import com.freezify.common.ApiException;
import com.freezify.common.Today;
import com.freezify.food.Food;
import com.freezify.food.FoodCatalog;
import com.freezify.food.FoodCategory;
import com.freezify.food.Quantity;
import com.freezify.food.Unit;
import com.freezify.households.HouseholdAccess;
import com.freezify.mealplanning.PlanNeeds;
import com.freezify.mealplanning.PlanNeeds.Need;
import com.freezify.shopping.ShoppingEvents.ShoppingListChanged;
import com.freezify.shopping.ShoppingEvents.ShoppingListCreated;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShoppingListService {

    /** The order of the aisles: fresh food first, as a shop is usually walked. */
    static final List<FoodCategory> AISLES = List.of(
            FoodCategory.VEGETABLES,
            FoodCategory.FRUITS,
            FoodCategory.DAIRY,
            FoodCategory.MEAT,
            FoodCategory.FISH,
            FoodCategory.EGGS,
            FoodCategory.BAKERY,
            FoodCategory.PANTRY,
            FoodCategory.FROZEN,
            FoodCategory.BEVERAGES,
            FoodCategory.PREPARED,
            FoodCategory.OTHER);

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    /**
     * What a person puts on the list.
     *
     * @param foodId   a catalog food; its name and category come from the catalog
     * @param name     free text, when the food is not in the catalog
     * @param category for free text; {@code OTHER} when not given
     * @param quantity how much, when it matters
     */
    public record ItemInput(
            @Nullable UUID foodId,
            @Nullable String name,
            @Nullable FoodCategory category,
            @Nullable Quantity quantity) {}

    private final ShoppingItemRepository items;
    private final HouseholdAccess access;
    private final FoodCatalog catalog;
    private final PlanNeeds plan;
    private final ApplicationEventPublisher events;
    private final Today today;
    private final JdbcTemplate jdbc;

    ShoppingListService(
            ShoppingItemRepository items,
            HouseholdAccess access,
            FoodCatalog catalog,
            PlanNeeds plan,
            ApplicationEventPublisher events,
            Today today,
            JdbcTemplate jdbc) {
        this.items = items;
        this.access = access;
        this.catalog = catalog;
        this.plan = plan;
        this.events = events;
        this.today = today;
        this.jdbc = jdbc;
    }

    /** The list of the household, aisle by aisle and by name within each aisle. */
    @Transactional(readOnly = true)
    public ShoppingViews.ShoppingList list(UUID householdId, UUID userId, String language) {
        access.requireMember(householdId, userId);
        List<ShoppingViews.Item> views = items.findByHouseholdId(householdId).stream()
                .map(item -> view(item, language))
                .sorted(Comparator.comparing((ShoppingViews.Item item) -> AISLES.indexOf(item.category()))
                        .thenComparing(item -> FoodCatalog.normalize(item.name()))
                        .thenComparing(item -> item.id().toString()))
                .toList();
        return new ShoppingViews.ShoppingList(views);
    }

    /**
     * Puts something on the list. A catalog food that is already on it, not yet bought and added by a person, gets
     * the new amount added instead of a second line.
     */
    @Transactional
    public ShoppingViews.Item add(UUID householdId, UUID userId, ItemInput input, String language) {
        access.requireMember(householdId, userId);
        Resolved what = resolve(input);
        if (what.foodId() != null) {
            for (ShoppingItemEntity existing : items.findByHouseholdId(householdId)) {
                if (canTakeMore(existing, what)) {
                    if (what.quantity() != null) {
                        existing.addQuantity(what.quantity());
                    }
                    changed(householdId, userId);
                    return view(existing, language);
                }
            }
        }
        ShoppingItemEntity item = items.save(new ShoppingItemEntity(
                householdId,
                what.foodId(),
                what.name(),
                what.category(),
                what.quantity(),
                ShoppingItemOrigin.MANUAL,
                null,
                userId));
        changed(householdId, userId);
        return view(item, language);
    }

    /** Replaces what a line says. From then on the line belongs to people: the plan no longer changes it. */
    @Transactional
    public ShoppingViews.Item edit(UUID householdId, UUID userId, UUID itemId, ItemInput input, String language) {
        access.requireMember(householdId, userId);
        ShoppingItemEntity item = require(householdId, itemId);
        Resolved what = resolve(input);
        item.edit(what.foodId(), what.name(), what.category(), what.quantity());
        changed(householdId, userId);
        return view(item, language);
    }

    /** Marks a line as bought, or not bought after all. */
    @Transactional
    public ShoppingViews.Item check(UUID householdId, UUID userId, UUID itemId, boolean checked, String language) {
        access.requireMember(householdId, userId);
        ShoppingItemEntity item = require(householdId, itemId);
        item.check(checked, userId, today.now().toInstant());
        changed(householdId, userId);
        return view(item, language);
    }

    @Transactional
    public void remove(UUID householdId, UUID userId, UUID itemId) {
        access.requireMember(householdId, userId);
        items.delete(require(householdId, itemId));
        changed(householdId, userId);
    }

    /** Takes off the list everything already bought. */
    @Transactional
    public int removeChecked(UUID householdId, UUID userId) {
        access.requireMember(householdId, userId);
        int removed = items.deleteChecked(householdId);
        if (removed > 0) {
            changed(householdId, userId);
        }
        return removed;
    }

    /**
     * Puts on the list what the meals planned for the week that contains {@code day} lack, from today on. Doing it
     * again recomputes those lines, so the list follows the plan and the inventory. What is already covered is
     * not asked for twice: lines a person put on the list for the same food, and what was bought for that week.
     *
     * @return how many lines of the list are now there for that week's meals
     */
    @Transactional
    public ShoppingViews.FilledFromPlan fillFromPlan(UUID householdId, UUID userId, LocalDate day) {
        access.requireMember(householdId, userId);
        LocalDate weekStart = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        if (weekEnd.isBefore(today.date())) {
            throw ApiException.conflict("WEEK_IN_THE_PAST", "A week that is over cannot be planned.");
        }
        // Two members filling the list at once would each add the same lines.
        jdbc.queryForList("select pg_advisory_xact_lock(hashtext(?))", "shopping-list:" + householdId);

        List<ShoppingItemEntity> current = items.findByHouseholdId(householdId);
        boolean wasEmpty = current.isEmpty();
        List<ShoppingItemEntity> replaced = current.stream()
                .filter(item -> item.origin() == ShoppingItemOrigin.PLAN && !item.checked())
                .filter(item -> inWeek(item.neededOn(), weekStart, weekEnd))
                .toList();
        items.deleteAll(replaced);
        List<ShoppingItemEntity> kept =
                current.stream().filter(item -> !replaced.contains(item)).toList();

        int lines = 0;
        for (Need need : plan.needs(householdId, weekStart, weekEnd)) {
            Quantity missing = need.quantity();
            for (ShoppingItemEntity item : kept) {
                if (covers(item, need.foodId(), missing.unit(), weekStart, weekEnd)) {
                    Quantity onList = item.quantity().convertTo(missing.unit());
                    missing = onList.isLessThan(missing) ? missing.minus(onList) : new Quantity(BigDecimal.ZERO, missing.unit());
                }
            }
            if (missing.isZero()) {
                continue;
            }
            Food food = catalog.findById(need.foodId()).orElse(null);
            items.save(new ShoppingItemEntity(
                    householdId,
                    need.foodId(),
                    null,
                    food == null ? FoodCategory.OTHER : food.category(),
                    readable(missing),
                    ShoppingItemOrigin.PLAN,
                    need.firstNeededOn(),
                    userId));
            lines++;
        }
        if (lines > 0 || !replaced.isEmpty()) {
            changed(householdId, userId);
        }
        if (wasEmpty && lines > 0) {
            events.publishEvent(new ShoppingListCreated(householdId, userId));
        }
        return new ShoppingViews.FilledFromPlan(lines);
    }

    /**
     * Whether a line already accounts for some of what the plan lacks: one a person intends to buy, or one already
     * bought for these meals (or bought without saying for which).
     */
    private static boolean covers(
            ShoppingItemEntity item, UUID foodId, Unit unit, LocalDate weekStart, LocalDate weekEnd) {
        Quantity quantity = item.quantity();
        if (!foodId.equals(item.foodId()) || quantity == null || quantity.unit().dimension() != unit.dimension()) {
            return false;
        }
        if (!item.checked()) {
            return item.origin() == ShoppingItemOrigin.MANUAL;
        }
        return item.neededOn() == null || inWeek(item.neededOn(), weekStart, weekEnd);
    }

    private static boolean inWeek(@Nullable LocalDate day, LocalDate weekStart, LocalDate weekEnd) {
        return day != null && !day.isBefore(weekStart) && !day.isAfter(weekEnd);
    }

    private static boolean canTakeMore(ShoppingItemEntity existing, Resolved what) {
        if (existing.checked()
                || existing.origin() != ShoppingItemOrigin.MANUAL
                || !what.foodId().equals(existing.foodId())) {
            return false;
        }
        Quantity have = existing.quantity();
        Quantity more = what.quantity();
        if (have == null || more == null) {
            return have == null && more == null;
        }
        return have.isCompatibleWith(more.unit());
    }

    /** "1500 g" reads better as "1.5 kg". */
    static Quantity readable(Quantity quantity) {
        Unit larger = switch (quantity.unit()) {
            case GRAM -> Unit.KILOGRAM;
            case MILLILITER -> Unit.LITER;
            default -> null;
        };
        if (larger == null || quantity.amount().compareTo(THOUSAND) < 0) {
            return quantity;
        }
        return quantity.convertTo(larger);
    }

    private record Resolved(@Nullable UUID foodId, @Nullable String name, FoodCategory category, @Nullable Quantity quantity) {}

    private Resolved resolve(ItemInput input) {
        if (input.foodId() != null) {
            Food food = catalog.findById(input.foodId())
                    .orElseThrow(() -> ApiException.badRequest("FOOD_NOT_FOUND", "The food does not exist in the catalog."));
            return new Resolved(food.id(), null, food.category(), input.quantity());
        }
        String name = input.name() == null ? "" : input.name().strip();
        if (name.isEmpty()) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Say what to buy: a catalog food or a name.");
        }
        return new Resolved(
                null, name, input.category() == null ? FoodCategory.OTHER : input.category(), input.quantity());
    }

    private ShoppingItemEntity require(UUID householdId, UUID itemId) {
        return items.findByIdAndHouseholdId(itemId, householdId)
                .orElseThrow(() -> ApiException.notFound("SHOPPING_ITEM_NOT_FOUND", "That line is not on the list."));
    }

    private ShoppingViews.Item view(ShoppingItemEntity item, String language) {
        String name = item.foodId() == null
                ? item.name()
                : catalog.findById(item.foodId()).map(food -> food.name(language)).orElse(item.name());
        Quantity quantity = item.quantity();
        return new ShoppingViews.Item(
                item.id(),
                item.foodId(),
                name == null ? "" : name,
                item.category(),
                quantity == null ? null : new ShoppingViews.Amount(quantity.amount(), quantity.unit()),
                item.origin(),
                item.neededOn(),
                item.checked(),
                item.checkedAt());
    }

    private void changed(UUID householdId, UUID userId) {
        events.publishEvent(new ShoppingListChanged(householdId, userId));
    }
}
