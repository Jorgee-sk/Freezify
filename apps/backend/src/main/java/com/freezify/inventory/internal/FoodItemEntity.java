package com.freezify.inventory.internal;

import com.freezify.food.FoodCatalog;
import com.freezify.food.FoodCategory;
import com.freezify.food.Quantity;
import com.freezify.food.StorageLocation;
import com.freezify.food.Unit;
import com.freezify.inventory.ExpirationSource;
import com.freezify.inventory.ItemStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "food_items")
class FoodItemEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Column(name = "food_id")
    private @Nullable UUID foodId;

    @Column(nullable = false)
    private String name;

    @Column(name = "search_name", nullable = false)
    private String searchName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FoodCategory category;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Unit unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_location", nullable = false)
    private StorageLocation storageLocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemStatus status;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Column(name = "expiration_date")
    private @Nullable LocalDate expirationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "expiration_source")
    private @Nullable ExpirationSource expirationSource;

    @Column(name = "opened_date")
    private @Nullable LocalDate openedDate;

    private @Nullable String barcode;

    private @Nullable String brand;

    @Column(name = "estimated_price", precision = 10, scale = 2)
    private @Nullable BigDecimal estimatedPrice;

    private @Nullable String notes;

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

    protected FoodItemEntity() {}

    FoodItemEntity(UUID householdId, UUID createdBy, ItemData data) {
        this.householdId = householdId;
        this.createdBy = createdBy;
        this.status = ItemStatus.AVAILABLE;
        apply(data);
    }

    /** Replaces everything the user can edit. */
    void apply(ItemData data) {
        this.foodId = data.foodId();
        this.name = data.name();
        this.searchName = FoodCatalog.normalize(data.name());
        this.category = data.category();
        this.quantity = data.quantity().amount();
        this.unit = data.quantity().unit();
        this.storageLocation = data.storageLocation();
        this.purchaseDate = data.purchaseDate();
        this.expirationDate = data.expirationDate();
        // Phase 2 only knows dates typed by the user; estimates arrive with the expiration engine.
        this.expirationSource = data.expirationDate() == null ? null : ExpirationSource.USER;
        this.openedDate = data.openedDate();
        this.barcode = data.barcode();
        this.brand = data.brand();
        this.estimatedPrice = data.estimatedPrice();
        this.notes = data.notes();
        if (status == ItemStatus.AVAILABLE && openedDate != null) {
            status = ItemStatus.OPENED;
        } else if (status == ItemStatus.OPENED && openedDate == null) {
            status = ItemStatus.AVAILABLE;
        }
    }

    void open(LocalDate today) {
        if (status == ItemStatus.AVAILABLE) {
            status = ItemStatus.OPENED;
        }
        if (openedDate == null) {
            openedDate = today;
        }
    }

    /**
     * Removes {@code used} from the item. When nothing is left the item takes {@code finalStatus}.
     *
     * @return the share of the estimated price that corresponds to the removed part, if a price is known
     */
    @Nullable BigDecimal takeOut(Quantity used, ItemStatus finalStatus) {
        Quantity available = quantity();
        Quantity remaining = available.minus(used);
        BigDecimal value = null;
        if (estimatedPrice != null) {
            value = remaining.isZero()
                    ? estimatedPrice
                    : estimatedPrice.multiply(used.fractionOf(available)).setScale(2, RoundingMode.HALF_UP);
            // The price describes what is left, so later uses are valued correctly.
            estimatedPrice = estimatedPrice.subtract(value);
        }
        quantity = remaining.amount();
        if (remaining.isZero()) {
            status = finalStatus;
        }
        return value;
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

    String name() {
        return name;
    }

    FoodCategory category() {
        return category;
    }

    Quantity quantity() {
        return new Quantity(quantity, unit);
    }

    ItemStatus status() {
        return status;
    }

    ItemView toView() {
        return new ItemView(
                id,
                householdId,
                foodId,
                name,
                category,
                new ItemView.QuantityView(quantity, unit),
                storageLocation,
                status,
                purchaseDate,
                expirationDate,
                expirationSource,
                openedDate,
                barcode,
                brand,
                estimatedPrice,
                notes,
                createdAt,
                updatedAt);
    }
}
