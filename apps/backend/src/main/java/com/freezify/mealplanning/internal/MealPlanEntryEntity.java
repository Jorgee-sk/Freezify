package com.freezify.mealplanning.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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

/** A recipe a household plans to eat at one meal of one day. */
@Entity
@Table(name = "meal_plan_entries")
class MealPlanEntryEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Column(name = "planned_on", nullable = false)
    private LocalDate plannedOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MealSlot slot;

    @Column(name = "recipe_id", nullable = false)
    private UUID recipeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MealOrigin origin;

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

    protected MealPlanEntryEntity() {}

    MealPlanEntryEntity(
            UUID householdId, LocalDate plannedOn, MealSlot slot, UUID recipeId, MealOrigin origin, UUID createdBy) {
        this.householdId = householdId;
        this.plannedOn = plannedOn;
        this.slot = slot;
        this.recipeId = recipeId;
        this.origin = origin;
        this.createdBy = createdBy;
    }

    UUID id() {
        return id;
    }

    LocalDate plannedOn() {
        return plannedOn;
    }

    MealSlot slot() {
        return slot;
    }

    UUID recipeId() {
        return recipeId;
    }

    MealOrigin origin() {
        return origin;
    }

    void choose(UUID recipeId, MealOrigin origin) {
        this.recipeId = recipeId;
        this.origin = origin;
    }

    void moveTo(LocalDate plannedOn, MealSlot slot) {
        this.plannedOn = plannedOn;
        this.slot = slot;
    }
}

interface MealPlanEntryRepository extends JpaRepository<MealPlanEntryEntity, UUID> {

    List<MealPlanEntryEntity> findByHouseholdIdAndPlannedOnBetween(UUID householdId, LocalDate from, LocalDate to);

    Optional<MealPlanEntryEntity> findByHouseholdIdAndPlannedOnAndSlot(
            UUID householdId, LocalDate plannedOn, MealSlot slot);

    boolean existsByHouseholdIdAndPlannedOnBetween(UUID householdId, LocalDate from, LocalDate to);
}
