package com.freezify.shopping.internal;

import com.freezify.food.FoodCategory;
import com.freezify.food.Quantity;
import com.freezify.food.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** A line of the shopping list of a household. */
@Entity
@Table(name = "shopping_list_items")
class ShoppingItemEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Column(name = "food_id")
    private @Nullable UUID foodId;

    /** Free text; for a catalog food the name comes from the catalog. */
    private @Nullable String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FoodCategory category;

    @Column(precision = 12, scale = 3)
    private @Nullable BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private @Nullable Unit unit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShoppingItemOrigin origin;

    @Column(name = "needed_on")
    private @Nullable LocalDate neededOn;

    @Column(nullable = false)
    private boolean checked;

    @Column(name = "checked_by")
    private @Nullable UUID checkedBy;

    @Column(name = "checked_at")
    private @Nullable Instant checkedAt;

    @Column(name = "created_by", updatable = false)
    private @Nullable UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected ShoppingItemEntity() {}

    ShoppingItemEntity(
            UUID householdId,
            @Nullable UUID foodId,
            @Nullable String name,
            FoodCategory category,
            @Nullable Quantity quantity,
            ShoppingItemOrigin origin,
            @Nullable LocalDate neededOn,
            UUID createdBy) {
        this.householdId = householdId;
        this.foodId = foodId;
        this.name = name;
        this.category = category;
        this.amount = quantity == null ? null : quantity.amount();
        this.unit = quantity == null ? null : quantity.unit();
        this.origin = origin;
        this.neededOn = neededOn;
        this.createdBy = createdBy;
    }

    UUID id() {
        return id;
    }

    UUID householdId() {
        return householdId;
    }

    @Nullable UUID foodId() {
        return foodId;
    }

    @Nullable String name() {
        return name;
    }

    FoodCategory category() {
        return category;
    }

    @Nullable Quantity quantity() {
        return amount == null || unit == null ? null : new Quantity(amount, unit);
    }

    ShoppingItemOrigin origin() {
        return origin;
    }

    @Nullable LocalDate neededOn() {
        return neededOn;
    }

    boolean checked() {
        return checked;
    }

    @Nullable Instant checkedAt() {
        return checkedAt;
    }

    /** What a person writes replaces the line; from then on it is theirs, and the plan no longer changes it. */
    void edit(@Nullable UUID foodId, @Nullable String name, FoodCategory category, @Nullable Quantity quantity) {
        this.foodId = foodId;
        this.name = name;
        this.category = category;
        this.amount = quantity == null ? null : quantity.amount();
        this.unit = quantity == null ? null : quantity.unit();
        this.origin = ShoppingItemOrigin.MANUAL;
        this.neededOn = null;
    }

    void addQuantity(Quantity more) {
        Quantity current = quantity();
        Quantity sum = current == null ? more : current.plus(more);
        this.amount = sum.amount();
        this.unit = sum.unit();
    }

    void check(boolean checked, UUID userId, Instant now) {
        this.checked = checked;
        this.checkedBy = checked ? userId : null;
        this.checkedAt = checked ? now : null;
    }
}

interface ShoppingItemRepository extends JpaRepository<ShoppingItemEntity, UUID> {

    List<ShoppingItemEntity> findByHouseholdId(UUID householdId);

    Optional<ShoppingItemEntity> findByIdAndHouseholdId(UUID id, UUID householdId);

    @Modifying
    @Query("delete from ShoppingItemEntity i where i.householdId = :householdId and i.checked = true")
    int deleteChecked(@Param("householdId") UUID householdId);
}
