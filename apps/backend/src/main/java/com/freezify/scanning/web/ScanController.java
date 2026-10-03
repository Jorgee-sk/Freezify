package com.freezify.scanning.web;

import com.freezify.common.CurrentUser;
import com.freezify.food.FoodCategory;
import com.freezify.food.Quantity;
import com.freezify.food.StorageLocation;
import com.freezify.food.Unit;
import com.freezify.scanning.internal.ReceiptScanService;
import com.freezify.scanning.internal.ReceiptScanService.ConfirmedLine;
import com.freezify.scanning.internal.ScanViews.ReceiptDraft;
import com.freezify.scanning.internal.ScanViews.Stocked;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/households/{householdId}/scans")
@Tag(name = "Scans")
class ScanController {

    private static final String LANGUAGE = "es|en";
    private static final String LANGUAGE_MESSAGE = "must be 'es' or 'en'";

    private final ReceiptScanService receipts;

    ScanController(ReceiptScanService receipts) {
        this.receipts = receipts;
    }

    /**
     * Reads the text of a receipt (the OCR runs on the phone) into a draft for the person to review. Nothing is
     * stored.
     */
    @PostMapping("/receipt")
    ReceiptDraft read(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID householdId,
            @RequestParam(defaultValue = "es") @Pattern(regexp = LANGUAGE, message = LANGUAGE_MESSAGE) String lang,
            @Valid @RequestBody ReceiptRequest request) {
        return receipts.read(householdId, CurrentUser.id(jwt), request.text(), lang);
    }

    /** Puts the reviewed products in the inventory. */
    @PostMapping("/receipt/confirm")
    Stocked confirm(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID householdId, @Valid @RequestBody ConfirmRequest request) {
        return receipts.confirm(
                householdId,
                CurrentUser.id(jwt),
                request.purchaseDate(),
                request.lines().stream().map(LineRequest::toLine).toList());
    }

    record ReceiptRequest(@NotBlank @Size(max = 20_000) String text) {}

    record QuantityRequest(
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 9, fraction = 3) BigDecimal amount,
            @NotNull Unit unit) {}

    record ConfirmRequest(
            @Nullable LocalDate purchaseDate,
            @NotNull @Size(min = 1, max = ReceiptScanService.MAX_LINES) List<@NotNull @Valid LineRequest> lines) {}

    record LineRequest(
            @Nullable @Size(max = 200) String text,
            @Nullable UUID foodId,
            @NotBlank @Size(max = 120) String name,
            @Nullable FoodCategory category,
            @NotNull @Valid QuantityRequest quantity,
            @Nullable StorageLocation storageLocation,
            @Nullable @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal price,
            @Nullable LocalDate expirationDate) {

        ConfirmedLine toLine() {
            return new ConfirmedLine(
                    text,
                    foodId,
                    name,
                    category,
                    new Quantity(quantity.amount(), quantity.unit()),
                    storageLocation,
                    price,
                    expirationDate);
        }
    }
}
