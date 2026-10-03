package com.freezify.shopping.web;

import com.freezify.common.CurrentUser;
import com.freezify.food.FoodCategory;
import com.freezify.food.Quantity;
import com.freezify.food.Unit;
import com.freezify.shopping.internal.ShoppingListService;
import com.freezify.shopping.internal.ShoppingListService.ItemInput;
import com.freezify.shopping.internal.ShoppingViews.FilledFromPlan;
import com.freezify.shopping.internal.ShoppingViews.Item;
import com.freezify.shopping.internal.ShoppingViews.ShoppingList;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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
@RequestMapping("/api/v1/households/{householdId}/shopping-list")
@Tag(name = "Shopping list")
class ShoppingListController {

    private static final String LANGUAGE = "es|en";
    private static final String LANGUAGE_MESSAGE = "must be 'es' or 'en'";

    private final ShoppingListService shopping;

    ShoppingListController(ShoppingListService shopping) {
        this.shopping = shopping;
    }

    /** The shared list of the household, aisle by aisle. */
    @GetMapping
    ShoppingList list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID householdId,
            @RequestParam(defaultValue = "es") @Pattern(regexp = LANGUAGE, message = LANGUAGE_MESSAGE) String lang) {
        return shopping.list(householdId, CurrentUser.id(jwt), lang);
    }

    /** Puts something on the list: a catalog food or free text, with or without a quantity. */
    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    Item add(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID householdId,
            @RequestParam(defaultValue = "es") @Pattern(regexp = LANGUAGE, message = LANGUAGE_MESSAGE) String lang,
            @Valid @RequestBody ItemRequest request) {
        return shopping.add(householdId, CurrentUser.id(jwt), request.toInput(), lang);
    }

    /** Replaces what a line says; from then on the plan no longer changes it. */
    @PutMapping("/items/{itemId}")
    Item edit(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID householdId,
            @PathVariable UUID itemId,
            @RequestParam(defaultValue = "es") @Pattern(regexp = LANGUAGE, message = LANGUAGE_MESSAGE) String lang,
            @Valid @RequestBody ItemRequest request) {
        return shopping.edit(householdId, CurrentUser.id(jwt), itemId, request.toInput(), lang);
    }

    /** Bought, or not bought after all. */
    @PutMapping("/items/{itemId}/checked")
    Item check(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID householdId,
            @PathVariable UUID itemId,
            @RequestParam(defaultValue = "es") @Pattern(regexp = LANGUAGE, message = LANGUAGE_MESSAGE) String lang,
            @Valid @RequestBody CheckRequest request) {
        return shopping.check(householdId, CurrentUser.id(jwt), itemId, request.checked(), lang);
    }

    @DeleteMapping("/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID householdId, @PathVariable UUID itemId) {
        shopping.remove(householdId, CurrentUser.id(jwt), itemId);
    }

    /** Takes off the list everything already bought. */
    @DeleteMapping("/items/checked")
    Map<String, Integer> removeChecked(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID householdId) {
        return Map.of("removed", shopping.removeChecked(householdId, CurrentUser.id(jwt)));
    }

    /** Puts on the list what the meals planned for a week lack, from today on. Doing it again recomputes it. */
    @PostMapping("/from-plan")
    FilledFromPlan fillFromPlan(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID householdId, @Valid @RequestBody FromPlanRequest request) {
        return shopping.fillFromPlan(householdId, CurrentUser.id(jwt), request.week());
    }

    record QuantityRequest(
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 9, fraction = 3) BigDecimal amount,
            @NotNull Unit unit) {}

    record ItemRequest(
            @Nullable UUID foodId,
            @Nullable @Size(max = 120) String name,
            @Nullable FoodCategory category,
            @Nullable @Valid QuantityRequest quantity) {

        ItemInput toInput() {
            return new ItemInput(
                    foodId, name, category, quantity == null ? null : new Quantity(quantity.amount(), quantity.unit()));
        }
    }

    record CheckRequest(@NotNull Boolean checked) {}

    record FromPlanRequest(@NotNull LocalDate week) {}
}
