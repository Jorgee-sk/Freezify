package com.freezify.scanning.internal;

import com.freezify.ai.AiService;
import com.freezify.common.ApiException;
import com.freezify.common.Today;
import com.freezify.food.Food;
import com.freezify.food.FoodAliases;
import com.freezify.food.FoodCatalog;
import com.freezify.food.FoodCategory;
import com.freezify.food.Quantity;
import com.freezify.food.StorageLocation;
import com.freezify.food.Unit;
import com.freezify.households.HouseholdAccess;
import com.freezify.inventory.InventoryIntake;
import com.freezify.scanning.ScanEvents.ReceiptScanned;
import com.freezify.scanning.internal.ScanViews.Amount;
import com.freezify.scanning.internal.ScanViews.Candidate;
import com.freezify.scanning.internal.ScanViews.Line;
import com.freezify.scanning.internal.ScanViews.ReadBy;
import com.freezify.scanning.internal.ScanViews.ReceiptDraft;
import com.freezify.scanning.internal.ScanViews.Stocked;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scanning a receipt in two steps. Reading turns its text (the phone does the OCR) into a draft that nothing
 * stores; confirming puts what the person kept, as they left it, in the inventory. Nothing read from a receipt
 * reaches the inventory without that review.
 */
@Service
public class ReceiptScanService {

    /** A receipt printed this long ago is more likely a misread date than an old receipt. */
    static final int OLDEST_PURCHASE_DAYS = 30;
    public static final int MAX_LINES = 100;
    private static final int MAX_NAME = 120;

    /**
     * What a person confirmed about one product.
     *
     * @param text the product as printed, from the draft; the household's choice of food is remembered for it
     */
    public record ConfirmedLine(
            @Nullable String text,
            @Nullable UUID foodId,
            String name,
            @Nullable FoodCategory category,
            Quantity quantity,
            @Nullable StorageLocation storageLocation,
            @Nullable BigDecimal price,
            @Nullable LocalDate expirationDate) {}

    /** A product read from the receipt, by the rules or by a model. */
    private record ReadLine(String text, @Nullable String fullName, @Nullable Quantity quantity, @Nullable BigDecimal price) {}

    private final HouseholdAccess access;
    private final FoodCatalog catalog;
    private final AiService ai;
    private final InventoryIntake inventory;
    private final FoodAliases aliases;
    private final ApplicationEventPublisher events;
    private final Today today;
    private final ReceiptParser parser = new ReceiptParser();

    ReceiptScanService(
            HouseholdAccess access,
            FoodCatalog catalog,
            AiService ai,
            InventoryIntake inventory,
            FoodAliases aliases,
            ApplicationEventPublisher events,
            Today today) {
        this.access = access;
        this.catalog = catalog;
        this.ai = ai;
        this.inventory = inventory;
        this.aliases = aliases;
        this.events = events;
        this.today = today;
    }

    /** Not in a transaction: the model may take a while, and nothing is written. */
    public ReceiptDraft read(UUID householdId, UUID userId, String text, String language) {
        access.requireMember(householdId, userId);
        ReceiptParser.Receipt byRules = parser.parse(text);

        ReadBy readBy = ReadBy.RULES;
        List<ReadLine> read = byRules.lines().stream()
                .map(line -> new ReadLine(line.text(), null, line.quantity(), line.price()))
                .toList();
        Optional<List<AiService.ReceiptLine>> byModel = ai.readReceipt(text, userId);
        if (byModel.isPresent()) {
            readBy = ReadBy.AI;
            read = byModel.get().stream().map(ReceiptScanService::fromModel).toList();
        }

        FoodMatcher matcher = new FoodMatcher(catalog.all(), aliases.shared(), aliases.learned(householdId));
        List<Line> lines = read.stream()
                .limit(MAX_LINES)
                .map(line -> draft(line, matcher, language))
                .toList();

        LocalDate day = today.date();
        LocalDate printed = byRules.date();
        boolean plausible = printed != null && !printed.isAfter(day) && !printed.isBefore(day.minusDays(OLDEST_PURCHASE_DAYS));
        return new ReceiptDraft(readBy, plausible ? printed : day, plausible, lines);
    }

    @Transactional
    public Stocked confirm(UUID householdId, UUID userId, @Nullable LocalDate purchaseDate, List<ConfirmedLine> lines) {
        access.requireMember(householdId, userId);
        LocalDate day = today.date();
        if (purchaseDate != null && purchaseDate.isAfter(day)) {
            throw ApiException.badRequest("PURCHASE_DATE_IN_FUTURE", "Food cannot have been bought after today.");
        }
        for (ConfirmedLine line : lines) {
            Food food = line.foodId() == null ? null : catalog.findById(line.foodId())
                    .orElseThrow(() -> ApiException.badRequest("FOOD_NOT_FOUND", "The food does not exist in the catalog."));
            inventory.stock(
                    householdId,
                    userId,
                    new InventoryIntake.NewItem(
                            line.foodId(),
                            line.name().strip(),
                            line.category() != null ? line.category() : food != null ? food.category() : FoodCategory.OTHER,
                            line.quantity(),
                            line.storageLocation() != null
                                    ? line.storageLocation()
                                    : food != null ? food.defaultStorage() : StorageLocation.OTHER,
                            purchaseDate,
                            line.expirationDate(),
                            line.price()));
            // The household's choice for this text wins next time; saying it is no catalog food forgets it.
            if (line.text() != null) {
                aliases.remember(householdId, line.text(), line.foodId());
            }
        }
        events.publishEvent(new ReceiptScanned(householdId, userId, lines.size()));
        return new Stocked(lines.size());
    }

    private static ReadLine fromModel(AiService.ReceiptLine line) {
        String words = ReceiptParser.productWords(line.text());
        Quantity quantity = line.amount() == null || line.unit() == null ? null : new Quantity(line.amount(), line.unit());
        return new ReadLine(words.isEmpty() ? line.text() : words, line.name(), quantity, line.price());
    }

    private Line draft(ReadLine line, FoodMatcher matcher, String language) {
        List<String> texts = line.fullName() == null ? List.of(line.text()) : List.of(line.fullName(), line.text());
        FoodMatcher.Match match = matcher.match(texts);
        Food food = match.foodId() == null ? null : catalog.findById(match.foodId()).orElse(null);
        List<Candidate> candidates = match.candidates().stream()
                .map(catalog::findById)
                .flatMap(Optional::stream)
                .map(candidate -> new Candidate(candidate.id(), candidate.name(language)))
                .toList();
        Quantity quantity = line.quantity() != null ? line.quantity() : new Quantity(BigDecimal.ONE, Unit.UNIT);
        return new Line(
                line.text(),
                food != null ? food.name(language) : sentenceCase(line.fullName() != null ? line.fullName() : line.text()),
                food == null ? null : food.id(),
                food == null ? ScanViews.MatchedBy.NONE : match.how(),
                candidates,
                food == null ? FoodCategory.OTHER : food.category(),
                food == null ? StorageLocation.OTHER : food.defaultStorage(),
                new Amount(quantity.amount(), quantity.unit()),
                line.quantity() != null,
                line.price(),
                food != null);
    }

    private static String sentenceCase(String text) {
        String lower = text.strip().toLowerCase(Locale.forLanguageTag("es"));
        String name = lower.isEmpty() ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        return name.length() > MAX_NAME ? name.substring(0, MAX_NAME).strip() : name;
    }
}
