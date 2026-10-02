package com.freezify.inventory.web;

import com.freezify.common.CurrentUser;
import com.freezify.common.PageResponse;
import com.freezify.common.Today;
import com.freezify.food.FoodCatalog;
import com.freezify.food.FoodCategory;
import com.freezify.food.Quantity;
import com.freezify.food.StorageLocation;
import com.freezify.food.Unit;
import com.freezify.inventory.internal.InventoryService;
import com.freezify.inventory.internal.InventoryService.ConsumeFirst;
import com.freezify.inventory.internal.InventoryService.Filter;
import com.freezify.inventory.internal.InventoryService.ItemSort;
import com.freezify.inventory.internal.InventoryService.RecentFood;
import com.freezify.inventory.internal.InventoryService.State;
import com.freezify.inventory.internal.ItemData;
import com.freezify.inventory.internal.ItemView;
import com.freezify.inventory.internal.WasteReason;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
@RequestMapping("/api/v1/households/{householdId}/inventory")
@Tag(name = "Inventory")
class InventoryController {

    private final InventoryService inventory;
    private final FoodCatalog catalog;
    private final Today today;

    InventoryController(InventoryService inventory, FoodCatalog catalog, Today today) {
        this.inventory = inventory;
        this.catalog = catalog;
        this.today = today;
    }

    @GetMapping
    PageResponse<ItemView> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID householdId,
            @RequestParam(defaultValue = "ACTIVE") State state,
            @RequestParam(required = false) @Nullable StorageLocation location,
            @RequestParam(required = false) @Nullable FoodCategory category,
            @RequestParam(name = "q", required = false) @Nullable @Size(max = 80) String text,
            @RequestParam(defaultValue = "EXPIRATION") ItemSort sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return inventory.list(
                householdId, CurrentUser.id(jwt), new Filter(state, location, category, text), sort, page, size);
    }

    /** What should be eaten first: counts per priority level and the most pressing items. */
    @GetMapping("/consume-first")
    ConsumeFirst consumeFirst(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID householdId) {
        return inventory.consumeFirst(householdId, CurrentUser.id(jwt));
    }

    @GetMapping("/recent")
    List<RecentFood> recent(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID householdId) {
        return inventory.recent(householdId, CurrentUser.id(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ItemView create(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID householdId, @Valid @RequestBody ItemRequest request) {
        return inventory.create(householdId, CurrentUser.id(jwt), toData(request));
    }

    @GetMapping("/{itemId}")
    ItemView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID householdId, @PathVariable UUID itemId) {
        return inventory.get(householdId, CurrentUser.id(jwt), itemId);
    }

    @PutMapping("/{itemId}")
    ItemView update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID householdId,
            @PathVariable UUID itemId,
            @Valid @RequestBody ItemRequest request) {
        return inventory.update(householdId, CurrentUser.id(jwt), itemId, toData(request));
    }

    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID householdId, @PathVariable UUID itemId) {
        inventory.delete(householdId, CurrentUser.id(jwt), itemId);
    }

    @PostMapping("/{itemId}/open")
    ItemView open(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID householdId, @PathVariable UUID itemId) {
        return inventory.open(householdId, CurrentUser.id(jwt), itemId);
    }

    @PostMapping("/{itemId}/consume")
    ItemView consume(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID householdId,
            @PathVariable UUID itemId,
            @Valid @RequestBody(required = false) @Nullable ConsumeRequest request) {
        Quantity quantity = request == null ? null : toQuantity(request.quantity());
        return inventory.consume(householdId, CurrentUser.id(jwt), itemId, quantity);
    }

    @PostMapping("/{itemId}/discard")
    ItemView discard(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID householdId,
            @PathVariable UUID itemId,
            @Valid @RequestBody(required = false) @Nullable DiscardRequest request) {
        Quantity quantity = request == null ? null : toQuantity(request.quantity());
        WasteReason reason = request == null || request.reason() == null ? WasteReason.OTHER : request.reason();
        return inventory.discard(householdId, CurrentUser.id(jwt), itemId, quantity, reason);
    }

    private ItemData toData(ItemRequest request) {
        FoodCategory category = request.category();
        if (category == null) {
            // A catalog food brings its own category; anything else is filed under "other" until edited.
            category = request.foodId() == null
                    ? FoodCategory.OTHER
                    : catalog.findById(request.foodId())
                            .map(food -> food.category())
                            .orElse(FoodCategory.OTHER);
        }
        return new ItemData(
                request.foodId(),
                request.name().strip(),
                category,
                new Quantity(request.quantity().amount(), request.quantity().unit()),
                request.storageLocation(),
                request.purchaseDate() == null ? today.date() : request.purchaseDate(),
                request.expirationDate(),
                request.openedDate(),
                blankToNull(request.barcode()),
                blankToNull(request.brand()),
                request.estimatedPrice(),
                blankToNull(request.notes()));
    }

    private static @Nullable Quantity toQuantity(@Nullable QuantityRequest request) {
        return request == null ? null : new Quantity(request.amount(), request.unit());
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    record QuantityRequest(
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 9, fraction = 3) BigDecimal amount,
            @NotNull Unit unit) {}

    record ItemRequest(
            @Nullable UUID foodId,
            @NotBlank @Size(max = 120) String name,
            @Nullable FoodCategory category,
            @NotNull @Valid QuantityRequest quantity,
            @NotNull StorageLocation storageLocation,
            @Nullable LocalDate purchaseDate,
            @Nullable LocalDate expirationDate,
            @Nullable LocalDate openedDate,
            @Nullable @Size(max = 32) String barcode,
            @Nullable @Size(max = 80) String brand,
            @Nullable @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal estimatedPrice,
            @Nullable @Size(max = 500) String notes) {}

    record ConsumeRequest(@Nullable @Valid QuantityRequest quantity) {}

    record DiscardRequest(@Nullable @Valid QuantityRequest quantity, @Nullable WasteReason reason) {}
}
