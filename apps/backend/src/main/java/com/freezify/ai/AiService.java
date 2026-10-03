package com.freezify.ai;

import com.freezify.food.Unit;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The concrete things a language model is asked to do. Callers never talk to a model: they get a validated answer
 * or nothing, and must always have a way to go on without it, because the model is optional, may be down and may
 * be wrong.
 */
public interface AiService {

    /** Whether a model is configured at all. Without one every use case answers nothing. */
    boolean enabled();

    /**
     * Reads the purchased products of the text of a shopping receipt.
     *
     * @param userId who asked: each person has a daily allowance of calls
     * @return empty when no model is configured, the allowance is spent, the call failed or the answer did not
     *     pass validation; in that case the caller falls back to its own parsing
     */
    Optional<List<ReceiptLine>> readReceipt(String text, UUID userId);

    /**
     * A purchased product as the model read it. Every line quotes the text it comes from.
     *
     * @param text   the receipt text of the product, as printed
     * @param name   the product written in full ("Pechuga de pollo" for "PECH POLLO")
     * @param amount how much was bought, when the receipt says it
     * @param price  what was paid for the line, when the receipt says it
     */
    record ReceiptLine(
            String text, String name, @Nullable BigDecimal amount, @Nullable Unit unit, @Nullable BigDecimal price) {}
}
