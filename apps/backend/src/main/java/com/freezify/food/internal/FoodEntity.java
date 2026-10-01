package com.freezify.food.internal;

import com.freezify.food.Food;
import com.freezify.food.FoodCategory;
import com.freezify.food.StorageLocation;
import com.freezify.food.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Catalog rows are created by migrations only. */
@Entity
@Immutable
@Table(name = "foods")
class FoodEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String slug;

    @Column(name = "name_es", nullable = false)
    private String nameEs;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FoodCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_unit", nullable = false)
    private Unit defaultUnit;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_storage", nullable = false)
    private StorageLocation defaultStorage;

    protected FoodEntity() {}

    Food toFood() {
        return new Food(id, slug, nameEs, nameEn, category, defaultUnit, defaultStorage);
    }
}
