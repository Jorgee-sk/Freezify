package com.freezify.inventory.internal;

import com.freezify.food.FoodCategory;
import com.freezify.food.StorageLocation;
import com.freezify.food.Unit;
import com.freezify.inventory.ExpirationSource;
import com.freezify.inventory.ItemStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ItemView(
        UUID id,
        UUID householdId,
        @Nullable UUID foodId,
        String name,
        FoodCategory category,
        QuantityView quantity,
        StorageLocation storageLocation,
        ItemStatus status,
        LocalDate purchaseDate,
        @Nullable LocalDate expirationDate,
        @Nullable ExpirationSource expirationSource,
        @Nullable LocalDate openedDate,
        @Nullable String barcode,
        @Nullable String brand,
        @Nullable BigDecimal estimatedPrice,
        @Nullable String notes,
        Instant createdAt,
        Instant updatedAt) {

    public record QuantityView(BigDecimal amount, Unit unit) {}
}
