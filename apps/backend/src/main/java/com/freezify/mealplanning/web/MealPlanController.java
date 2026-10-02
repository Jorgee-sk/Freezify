package com.freezify.mealplanning.web;

import com.freezify.common.CurrentUser;
import com.freezify.common.Today;
import com.freezify.mealplanning.internal.MealPlanService;
import com.freezify.mealplanning.internal.MealPlanViews.Generated;
import com.freezify.mealplanning.internal.MealPlanViews.MealPlanView;
import com.freezify.mealplanning.internal.MealSlot;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/households/{householdId}/meal-plan")
@Tag(name = "Meal plan")
class MealPlanController {

    private final MealPlanService plans;
    private final Today today;

    MealPlanController(MealPlanService plans, Today today) {
        this.plans = plans;
        this.today = today;
    }

    /** The week (Monday to Sunday) that contains {@code week}; this week when it is not given. */
    @GetMapping
    MealPlanView week(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID householdId,
            @RequestParam(required = false) @Nullable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
            @RequestParam(defaultValue = "es") @Pattern(regexp = "es|en", message = "must be 'es' or 'en'")
                    String lang) {
        return plans.week(householdId, CurrentUser.id(jwt), week == null ? today.date() : week, lang);
    }

    /** Puts a recipe in a meal, in place of whatever was planned there. */
    @PutMapping("/{date}/{slot}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void choose(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID householdId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable MealSlot slot,
            @Valid @RequestBody ChooseRequest request) {
        plans.choose(householdId, CurrentUser.id(jwt), date, slot, request.recipeId());
    }

    record ChooseRequest(@NotNull UUID recipeId) {}

    @DeleteMapping("/{date}/{slot}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID householdId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable MealSlot slot) {
        plans.remove(householdId, CurrentUser.id(jwt), date, slot);
    }

    /** Moves a meal to another day or slot. If something is planned there, the two swap places. */
    @PostMapping("/{date}/{slot}/move")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void move(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID householdId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable MealSlot slot,
            @Valid @RequestBody MoveRequest request) {
        plans.move(householdId, CurrentUser.id(jwt), date, slot, request.date(), request.slot());
    }

    record MoveRequest(@NotNull LocalDate date, @NotNull MealSlot slot) {}

    /**
     * Fills the empty meals of a week, from today on, using first what expires first. What members chose is
     * kept; what was generated before is only replaced when {@code replaceGenerated} says so.
     */
    @PostMapping("/generate")
    Generated generate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID householdId,
            @Valid @RequestBody GenerateRequest request) {
        return plans.generate(householdId, CurrentUser.id(jwt), request.week(), Boolean.TRUE.equals(request.replaceGenerated()));
    }

    /**
     * @param replaceGenerated when absent, nothing that is already planned is replaced
     */
    record GenerateRequest(@NotNull LocalDate week, @Nullable Boolean replaceGenerated) {}
}
