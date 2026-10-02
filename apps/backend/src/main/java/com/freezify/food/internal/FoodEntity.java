package com.freezify.food.internal;

import com.freezify.food.Food;
import com.freezify.food.FoodCategory;
import com.freezify.food.FoodTrait;
import com.freezify.food.StorageLocation;
import com.freezify.food.Unit;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
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

    // Read with the catalog, which is loaded once and outside any transaction.
    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @CollectionTable(name = "food_traits", joinColumns = @JoinColumn(name = "food_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "trait", nullable = false)
    private Set<FoodTrait> traits = EnumSet.noneOf(FoodTrait.class);

    protected FoodEntity() {}

    Food toFood() {
        return new Food(id, slug, nameEs, nameEn, category, defaultUnit, defaultStorage, traits);
    }
}
