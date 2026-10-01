package com.freezify.food.web;

import com.freezify.food.Food;
import com.freezify.food.FoodCatalog;
import com.freezify.food.FoodCategory;
import com.freezify.food.StorageLocation;
import com.freezify.food.Unit;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/foods")
@Tag(name = "Foods")
class FoodController {

    private final FoodCatalog catalog;

    FoodController(FoodCatalog catalog) {
        this.catalog = catalog;
    }

    /** Autocomplete for the "add food" form. */
    @GetMapping
    List<FoodView> search(
            @RequestParam(name = "q") @Size(max = 80) String text,
            @RequestParam(defaultValue = "es") @Pattern(regexp = "es|en", message = "must be 'es' or 'en'") String lang,
            @RequestParam(defaultValue = "8") @Min(1) @Max(20) int limit) {
        return catalog.search(text, lang, limit).stream()
                .map(food -> FoodView.of(food, lang))
                .toList();
    }

    record FoodView(UUID id, String name, FoodCategory category, Unit defaultUnit, StorageLocation defaultStorage) {
        static FoodView of(Food food, String language) {
            return new FoodView(
                    food.id(), food.name(language), food.category(), food.defaultUnit(), food.defaultStorage());
        }
    }
}
