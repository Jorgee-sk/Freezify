package com.freezify.scanning.internal;

import com.freezify.food.FoodCategory;
import com.freezify.food.StorageLocation;
import com.freezify.food.Unit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A scanned receipt as clients receive it: a draft that nothing has stored yet. */
public final class ScanViews {

    private ScanViews() {}

    /** Who turned the receipt text into products. */
    public enum ReadBy {
        /** The language model, its answer validated. */
        AI,
        /** The rules alone: no model is configured, or its answer was not usable. */
        RULES
    }

    /**
     * @param purchaseDate            the date printed on the receipt when it is a plausible one, otherwise today
     * @param purchaseDateFromReceipt whether that date was read from the receipt
     */
    public record ReceiptDraft(ReadBy readBy, LocalDate purchaseDate, boolean purchaseDateFromReceipt, List<Line> lines) {}

    /**
     * Every value is a proposal for the person to check. The flags say what was read or inferred and what is
     * only a default.
     *
     * @param text                the product as printed; confirming sends it back so the household's choice is
     *     remembered
     * @param name                the catalog name when a food was found, otherwise the product as read
     * @param match               how the food was found ({@code LEARNED}, {@code ALIAS}, {@code NAME}) or
     *     {@code NONE}
     * @param candidates          foods it may be, the most likely first
     * @param quantityFromReceipt {@code false} when the receipt does not say how much and one unit is assumed
     * @param include             proposed for the inventory: only lines that are a catalog food are, to begin with
     */
    public record Line(
            String text,
            String name,
            @Nullable UUID foodId,
            MatchedBy match,
            List<Candidate> candidates,
            FoodCategory category,
            StorageLocation storageLocation,
            Amount quantity,
            boolean quantityFromReceipt,
            @Nullable BigDecimal price,
            boolean include) {}

    /** How the suggested food was found. */
    public enum MatchedBy {
        /** This household confirmed that food for exactly this text before. */
        LEARNED,
        /** Through one of the shared receipt abbreviations. */
        ALIAS,
        /** The line contains a name of the food. */
        NAME,
        /** Nothing found, or a tie the person has to settle among the candidates. */
        NONE
    }

    public record Candidate(UUID foodId, String name) {}

    public record Amount(BigDecimal amount, Unit unit) {}

    /** @param stocked how many products went into the inventory */
    public record Stocked(int stocked) {}
}
