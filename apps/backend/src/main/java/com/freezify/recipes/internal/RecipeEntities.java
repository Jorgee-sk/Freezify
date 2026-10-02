package com.freezify.recipes.internal;

import com.freezify.food.Quantity;
import com.freezify.food.Unit;
import com.freezify.recipes.internal.Recipe.Course;
import com.freezify.recipes.internal.Recipe.Difficulty;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.ListIndexBase;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Recipe rows are created by migrations only. */
@Entity
@Immutable
@Table(name = "recipes")
class RecipeEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String slug;

    @Column(name = "name_es", nullable = false)
    private String nameEs;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "description_es", nullable = false)
    private String descriptionEs;

    @Column(name = "description_en", nullable = false)
    private String descriptionEn;

    @Column(nullable = false)
    private int servings;

    @Column(name = "prep_minutes", nullable = false)
    private int prepMinutes;

    @Column(name = "cook_minutes", nullable = false)
    private int cookMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Course course;

    @Column(name = "steps_es", nullable = false)
    private String stepsEs;

    @Column(name = "steps_en", nullable = false)
    private String stepsEn;

    @ElementCollection
    @CollectionTable(name = "recipe_ingredients", joinColumns = @JoinColumn(name = "recipe_id"))
    @OrderColumn(name = "position")
    @ListIndexBase(1)
    @BatchSize(size = 100)
    private List<RecipeIngredientRow> ingredients = new ArrayList<>();

    protected RecipeEntity() {}

    Recipe toRecipe() {
        return new Recipe(
                id,
                slug,
                nameEs,
                nameEn,
                descriptionEs,
                descriptionEn,
                servings,
                prepMinutes,
                cookMinutes,
                difficulty,
                course,
                ingredients.stream()
                        .map(row -> new Recipe.Ingredient(
                                row.foodId(), new Quantity(row.amount(), row.unit()), row.staple()))
                        .toList(),
                stepsEs.lines().toList(),
                stepsEn.lines().toList());
    }
}

@Embeddable
record RecipeIngredientRow(
        @Column(name = "food_id", nullable = false) UUID foodId,
        @Column(nullable = false, precision = 12, scale = 3) BigDecimal amount,
        @Enumerated(EnumType.STRING) @Column(nullable = false) Unit unit,
        @Column(nullable = false) boolean staple) {}

/** A household said it cooked a recipe on a given day. */
@Entity
@Table(name = "cooked_recipes")
class CookedRecipeEntity {

    @Id
    private UUID id;

    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Column(name = "recipe_id", nullable = false, updatable = false)
    private UUID recipeId;

    @Column(name = "cooked_by", updatable = false)
    private @Nullable UUID cookedBy;

    @Column(name = "cooked_on", nullable = false, updatable = false)
    private LocalDate cookedOn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CookedRecipeEntity() {}
}

interface RecipeRepository extends JpaRepository<RecipeEntity, UUID> {}

interface CookedRecipeRepository extends JpaRepository<CookedRecipeEntity, UUID> {

    /**
     * @return 1 when the row was added, 0 when the household had already cooked the recipe that day
     */
    @Modifying
    @Query(
            nativeQuery = true,
            value = "insert into cooked_recipes (id, household_id, recipe_id, cooked_by, cooked_on, created_at)"
                    + " values (gen_random_uuid(), :householdId, :recipeId, :cookedBy, :cookedOn, :createdAt)"
                    + " on conflict (household_id, recipe_id, cooked_on) do nothing")
    int recordOnce(
            @Param("householdId") UUID householdId,
            @Param("recipeId") UUID recipeId,
            @Param("cookedBy") UUID cookedBy,
            @Param("cookedOn") LocalDate cookedOn,
            @Param("createdAt") Instant createdAt);

    @Query("select c.recipeId as recipeId, max(c.cookedOn) as lastCooked from CookedRecipeEntity c"
            + " where c.householdId = :householdId group by c.recipeId")
    Collection<LastCooked> lastCookedByRecipe(@Param("householdId") UUID householdId);

    interface LastCooked {
        UUID getRecipeId();

        LocalDate getLastCooked();
    }
}
