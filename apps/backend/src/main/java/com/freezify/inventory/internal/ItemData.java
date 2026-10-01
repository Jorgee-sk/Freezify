package com.freezify.inventory.internal;

import com.freezify.food.FoodCategory;
import com.freezify.food.Quantity;
import com.freezify.food.StorageLocation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Everything a user can set on an inventory item, already validated and with defaults resolved. */
public record ItemData(
        @Nullable UUID foodId,
        String name,
        FoodCategory category,
        Quantity quantity,
        StorageLocation storageLocation,
        LocalDate purchaseDate,
        @Nullable LocalDate expirationDate,
        @Nullable LocalDate openedDate,
        @Nullable String barcode,
        @Nullable String brand,
        @Nullable BigDecimal estimatedPrice,
        @Nullable String notes) {}
