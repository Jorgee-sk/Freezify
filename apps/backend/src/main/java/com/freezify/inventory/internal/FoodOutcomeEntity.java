package com.freezify.inventory.internal;

import com.freezify.food.FoodCategory;
import com.freezify.food.Quantity;
import com.freezify.food.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

/**
 * A record of food leaving the inventory. It copies name and category so that statistics survive the
 * deletion of the item.
 */
@Entity
@Table(name = "food_outcomes")
class FoodOutcomeEntity {

    enum Type {
        CONSUMED,
        DISCARDED
    }

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Column(name = "food_item_id")
    private @Nullable UUID foodItemId;

    @Column(name = "food_id")
    private @Nullable UUID foodId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FoodCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Enumerated(EnumType.STRING)
    private @Nullable WasteReason reason;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Unit unit;

    @Column(name = "estimated_value", precision = 10, scale = 2)
    private @Nullable BigDecimal estimatedValue;

    @Column(name = "recorded_by")
    private @Nullable UUID recordedBy;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected FoodOutcomeEntity() {}

    FoodOutcomeEntity(
            FoodItemEntity item,
            Type type,
            @Nullable WasteReason reason,
            Quantity quantity,
            @Nullable BigDecimal estimatedValue,
            UUID recordedBy,
            Instant occurredAt) {
        this.householdId = item.householdId();
        this.foodItemId = item.id();
        this.foodId = item.foodId();
        this.name = item.name();
        this.category = item.category();
        this.type = type;
        this.reason = reason;
        this.quantity = quantity.amount();
        this.unit = quantity.unit();
        this.estimatedValue = estimatedValue;
        this.recordedBy = recordedBy;
        this.occurredAt = occurredAt;
    }
}
