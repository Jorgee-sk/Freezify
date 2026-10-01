package com.freezify.food;

import java.util.UUID;

/**
 * A canonical food of the catalog ("tomato"), independent of brand or package. Inventory items, and later
 * recipe ingredients and shopping list lines, point to it so that they can be matched with each other.
 */
public record Food(
        UUID id,
        String slug,
        String nameEs,
        String nameEn,
        FoodCategory category,
        Unit defaultUnit,
        StorageLocation defaultStorage) {

    public String name(String language) {
        return "es".equals(language) ? nameEs : nameEn;
    }
}
