package com.freezify.inventory.internal;

import com.freezify.common.ApiException;
import com.freezify.common.PageResponse;
import com.freezify.common.Today;
import com.freezify.expiration.ExpirationEstimator;
import com.freezify.expiration.ExpirationPriority;
import com.freezify.food.FoodCatalog;
import com.freezify.food.FoodCategory;
import com.freezify.food.Quantity;
import com.freezify.food.StorageLocation;
import com.freezify.households.HouseholdAccess;
import com.freezify.inventory.InventoryEvents.FoodItemAdded;
import com.freezify.inventory.InventoryEvents.FoodItemConsumed;
import com.freezify.inventory.InventoryEvents.FoodItemDiscarded;
import com.freezify.inventory.InventoryEvents.InventoryChanged;
import com.freezify.inventory.ItemStatus;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

interface FoodItemRepository extends JpaRepository<FoodItemEntity, UUID>, JpaSpecificationExecutor<FoodItemEntity> {

    /** The id breaks ties: creation times have millisecond precision and ids are time-ordered. */
    List<FoodItemEntity> findTop50ByHouseholdIdOrderByCreatedAtDescIdDesc(UUID householdId);

    List<FoodItemEntity> findByHouseholdIdAndStatusInAndExpirationDateLessThanEqualOrderByExpirationDateAscSearchNameAsc(
            UUID householdId, Collection<ItemStatus> statuses, LocalDate until, Limit limit);

    @Query("select distinct i.householdId from FoodItemEntity i"
            + " where i.status in :statuses and i.expirationDate < :today")
    List<UUID> householdsWithItemsPastTheirDate(
            @Param("statuses") Collection<ItemStatus> statuses, @Param("today") LocalDate today);

    /** "versioned" bumps the version, so a request that read the item before the sweep cannot overwrite it. */
    @Modifying
    @Query("update versioned FoodItemEntity i set i.status = :expired, i.updatedAt = :now"
            + " where i.status in :statuses and i.expirationDate < :today")
    int markPastTheirDate(
            @Param("statuses") Collection<ItemStatus> statuses,
            @Param("today") LocalDate today,
            @Param("expired") ItemStatus expired,
            @Param("now") Instant now);

    /** One entry per item still in the house; {@code null} for items without a date. */
    @Query("select i.expirationDate from FoodItemEntity i where i.householdId = :householdId and i.status in :statuses")
    List<LocalDate> expirationDates(
            @Param("householdId") UUID householdId, @Param("statuses") Collection<ItemStatus> statuses);
}

interface FoodOutcomeRepository extends JpaRepository<FoodOutcomeEntity, UUID> {}

@Service
public class InventoryService {

    private static final int RECENT_LIMIT = 10;
    private static final int CONSUME_FIRST_LIMIT = 20;
    private static final String NO_DATE = "NO_DATE";

    public enum State {
        /** Still in the house. */
        ACTIVE,
        /** Consumed or discarded. */
        FINISHED,
        ALL
    }

    public enum ItemSort {
        EXPIRATION(Sort.by(
                Sort.Order.asc("expirationDate").nullsLast(), Sort.Order.asc("searchName"), Sort.Order.asc("id"))),
        NAME(Sort.by(Sort.Order.asc("searchName"), Sort.Order.asc("id"))),
        RECENT(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        private final Sort sort;

        ItemSort(Sort sort) {
            this.sort = sort;
        }
    }

    public record Filter(
            State state, @Nullable StorageLocation location, @Nullable FoodCategory category, @Nullable String text) {}

    /**
     * What should be eaten first.
     *
     * @param counts how many items still in the house are at each priority level, plus {@code NO_DATE}
     * @param items  expired items and those with 5 days or fewer left, the most pressing first
     */
    public record ConsumeFirst(Map<String, Long> counts, List<ItemView> items) {}

    /** A food the household added before, to offer it again with one tap. */
    public record RecentFood(
            String name,
            @Nullable UUID foodId,
            FoodCategory category,
            ItemView.QuantityView quantity,
            StorageLocation storageLocation) {}

    private final FoodItemRepository items;
    private final FoodOutcomeRepository outcomes;
    private final HouseholdAccess access;
    private final FoodCatalog catalog;
    private final ExpirationEstimator estimator;
    private final ApplicationEventPublisher events;
    private final Today today;
    private final Clock clock;

    InventoryService(
            FoodItemRepository items,
            FoodOutcomeRepository outcomes,
            HouseholdAccess access,
            FoodCatalog catalog,
            ExpirationEstimator estimator,
            ApplicationEventPublisher events,
            Today today,
            Clock clock) {
        this.items = items;
        this.outcomes = outcomes;
        this.access = access;
        this.catalog = catalog;
        this.estimator = estimator;
        this.events = events;
        this.today = today;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<ItemView> list(UUID householdId, UUID userId, Filter filter, ItemSort sort, int page, int size) {
        access.requireMember(householdId, userId);
        return PageResponse.of(items.findAll(matching(householdId, filter), PageRequest.of(page, size, sort.sort))
                .map(item -> item.toView(today.date())));
    }

    @Transactional(readOnly = true)
    public List<RecentFood> recent(UUID householdId, UUID userId) {
        access.requireMember(householdId, userId);
        Map<String, RecentFood> distinct = new LinkedHashMap<>();
        for (FoodItemEntity item : items.findTop50ByHouseholdIdOrderByCreatedAtDescIdDesc(householdId)) {
            ItemView view = item.toView(today.date());
            distinct.putIfAbsent(
                    FoodCatalog.normalize(view.name()),
                    new RecentFood(
                            view.name(), view.foodId(), view.category(), view.quantity(), view.storageLocation()));
            if (distinct.size() == RECENT_LIMIT) {
                break;
            }
        }
        return List.copyOf(distinct.values());
    }

    @Transactional(readOnly = true)
    public ConsumeFirst consumeFirst(UUID householdId, UUID userId) {
        access.requireMember(householdId, userId);
        LocalDate day = today.date();
        Collection<ItemStatus> inTheHouse = EnumSet.of(ItemStatus.AVAILABLE, ItemStatus.OPENED, ItemStatus.EXPIRED);

        // Every level is reported, also with zero, so clients need no special cases.
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ExpirationPriority priority : ExpirationPriority.values()) {
            counts.put(priority.name(), 0L);
        }
        counts.put(NO_DATE, 0L);
        for (LocalDate date : items.expirationDates(householdId, inTheHouse)) {
            counts.merge(date == null ? NO_DATE : ExpirationPriority.of(date, day).name(), 1L, Long::sum);
        }

        List<ItemView> first = items
                .findByHouseholdIdAndStatusInAndExpirationDateLessThanEqualOrderByExpirationDateAscSearchNameAsc(
                        householdId, inTheHouse, ExpirationPriority.attentionHorizon(day), Limit.of(CONSUME_FIRST_LIMIT))
                .stream()
                .map(item -> item.toView(day))
                .toList();
        return new ConsumeFirst(counts, first);
    }

    @Transactional(readOnly = true)
    public ItemView get(UUID householdId, UUID userId, UUID itemId) {
        access.requireMember(householdId, userId);
        return load(householdId, itemId).toView(today.date());
    }

    @Transactional
    public ItemView create(UUID householdId, UUID userId, ItemData data) {
        access.requireMember(householdId, userId);
        requireKnownFood(data);
        FoodItemEntity item = items.saveAndFlush(withExpiration(new FoodItemEntity(householdId, userId, data)));
        events.publishEvent(new FoodItemAdded(householdId, userId, item.id()));
        events.publishEvent(new InventoryChanged(householdId, userId));
        return item.toView(today.date());
    }

    @Transactional
    public ItemView update(UUID householdId, UUID userId, UUID itemId, ItemData data) {
        access.requireMember(householdId, userId);
        requireKnownFood(data);
        FoodItemEntity item = loadActive(householdId, itemId);
        item.apply(data);
        withExpiration(item);
        events.publishEvent(new InventoryChanged(householdId, userId));
        return items.saveAndFlush(item).toView(today.date());
    }

    /** Removes an item that should never have been there. It is neither consumption nor waste. */
    @Transactional
    public void delete(UUID householdId, UUID userId, UUID itemId) {
        access.requireMember(householdId, userId);
        items.delete(load(householdId, itemId));
        events.publishEvent(new InventoryChanged(householdId, userId));
    }

    @Transactional
    public ItemView open(UUID householdId, UUID userId, UUID itemId) {
        access.requireMember(householdId, userId);
        FoodItemEntity item = loadActive(householdId, itemId);
        item.open(today.date());
        withExpiration(item);
        events.publishEvent(new InventoryChanged(householdId, userId));
        return items.saveAndFlush(item).toView(today.date());
    }

    /**
     * @param quantity how much was eaten; {@code null} means everything that is left
     */
    @Transactional
    public ItemView consume(UUID householdId, UUID userId, UUID itemId, @Nullable Quantity quantity) {
        FoodItemEntity item = takeOut(householdId, userId, itemId, quantity, FoodOutcomeEntity.Type.CONSUMED, null);
        events.publishEvent(new FoodItemConsumed(householdId, userId, itemId));
        return item.toView(today.date());
    }

    /**
     * @param quantity how much was thrown away; {@code null} means everything that is left
     */
    @Transactional
    public ItemView discard(
            UUID householdId, UUID userId, UUID itemId, @Nullable Quantity quantity, WasteReason reason) {
        FoodItemEntity item = takeOut(householdId, userId, itemId, quantity, FoodOutcomeEntity.Type.DISCARDED, reason);
        events.publishEvent(new FoodItemDiscarded(householdId, userId, itemId));
        return item.toView(today.date());
    }

    /**
     * Marks as expired every item, of any household, whose date has passed. Called by the daily sweep, not by
     * users; members who have the inventory open are told so that their screens refresh.
     *
     * @return how many items changed
     */
    @Transactional
    public int markExpired() {
        LocalDate day = today.date();
        Collection<ItemStatus> notYetExpired = EnumSet.of(ItemStatus.AVAILABLE, ItemStatus.OPENED);
        List<UUID> households = items.householdsWithItemsPastTheirDate(notYetExpired, day);
        if (households.isEmpty()) {
            return 0;
        }
        int changed = items.markPastTheirDate(notYetExpired, day, ItemStatus.EXPIRED, clock.instant());
        households.forEach(householdId -> events.publishEvent(new InventoryChanged(householdId, null)));
        return changed;
    }

    private FoodItemEntity takeOut(
            UUID householdId,
            UUID userId,
            UUID itemId,
            @Nullable Quantity requested,
            FoodOutcomeEntity.Type type,
            @Nullable WasteReason reason) {
        access.requireMember(householdId, userId);
        FoodItemEntity item = loadActive(householdId, itemId);
        Quantity available = item.quantity();

        Quantity used = available;
        if (requested != null) {
            if (!requested.isCompatibleWith(available.unit())) {
                throw ApiException.badRequest(
                        "INCOMPATIBLE_UNIT",
                        "This item is measured in " + available.unit() + "; " + requested.unit()
                                + " cannot be converted into it.");
            }
            used = requested.convertTo(available.unit());
            if (available.isLessThan(used)) {
                throw ApiException.badRequest(
                        "QUANTITY_EXCEEDS_AVAILABLE", "There is less of this item left than the quantity given.");
            }
        }

        ItemStatus finalStatus =
                type == FoodOutcomeEntity.Type.CONSUMED ? ItemStatus.CONSUMED : ItemStatus.DISCARDED;
        BigDecimal value = item.takeOut(used, finalStatus);
        outcomes.save(new FoodOutcomeEntity(item, type, reason, used, value, userId, clock.instant()));
        events.publishEvent(new InventoryChanged(householdId, userId));
        return items.saveAndFlush(item);
    }

    /**
     * Works out the date that applies to the item: the user's, or an estimate when there is none or when
     * opening the food shortens its life. Called whenever something the estimate depends on may have changed.
     */
    private FoodItemEntity withExpiration(FoodItemEntity item) {
        item.expiresOn(estimator.resolve(item.expirationInput()).orElse(null), today.date());
        return item;
    }

    private void requireKnownFood(ItemData data) {
        if (data.foodId() != null && catalog.findById(data.foodId()).isEmpty()) {
            throw ApiException.badRequest("FOOD_NOT_FOUND", "The food does not exist in the catalog.");
        }
    }

    private FoodItemEntity loadActive(UUID householdId, UUID itemId) {
        FoodItemEntity item = load(householdId, itemId);
        if (!item.status().isActive()) {
            throw ApiException.conflict("ITEM_NOT_ACTIVE", "This item was already consumed or discarded.");
        }
        return item;
    }

    /** An item of another household is reported exactly like one that does not exist. */
    private FoodItemEntity load(UUID householdId, UUID itemId) {
        return items.findById(itemId)
                .filter(item -> item.householdId().equals(householdId))
                .orElseThrow(() -> ApiException.notFound("ITEM_NOT_FOUND", "Inventory item not found."));
    }

    private static Specification<FoodItemEntity> matching(UUID householdId, Filter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("householdId"), householdId));
            if (filter.state() != State.ALL) {
                List<ItemStatus> wanted = java.util.Arrays.stream(ItemStatus.values())
                        .filter(status -> status.isActive() == (filter.state() == State.ACTIVE))
                        .toList();
                predicates.add(root.get("status").in(wanted));
            }
            if (filter.location() != null) {
                predicates.add(builder.equal(root.get("storageLocation"), filter.location()));
            }
            if (filter.category() != null) {
                predicates.add(builder.equal(root.get("category"), filter.category()));
            }
            if (filter.text() != null && !filter.text().isBlank()) {
                String escaped = FoodCatalog.normalize(filter.text())
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");
                predicates.add(builder.like(root.get("searchName"), "%" + escaped + "%", '\\'));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
