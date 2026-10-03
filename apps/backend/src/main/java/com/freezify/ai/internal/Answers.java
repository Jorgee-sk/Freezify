package com.freezify.ai.internal;

import com.freezify.food.Unit;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

/** Reading the fields of a model's answer, where anything may be missing or of the wrong type. */
final class Answers {

    private static final Logger log = LoggerFactory.getLogger(Answers.class);

    static final BigDecimal MAX_AMOUNT = BigDecimal.valueOf(10_000);
    static final Map<String, Object> UNIT_SCHEMA = Map.of(
            "type", "string", "enum", List.of("GRAM", "KILOGRAM", "MILLILITER", "LITER", "UNIT"));
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private Answers() {}

    static <T> Optional<T> rejected(String why) {
        // Never the answer itself: it may quote personal data.
        log.warn("Answer of the language model discarded: {}", why);
        return Optional.empty();
    }

    /** A non-blank string no longer than {@code maxLength}; {@code null} otherwise. */
    static @Nullable String string(JsonNode node, String field, int maxLength) {
        JsonNode value = node.path(field);
        if (!value.isString() || value.asString().isBlank() || value.asString().length() > maxLength) {
            return null;
        }
        return value.asString().strip();
    }

    /** A number between 0 and {@link #MAX_AMOUNT}; {@code null} otherwise. */
    static @Nullable BigDecimal number(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isNumber()) {
            return null;
        }
        BigDecimal number = value.decimalValue();
        return number.signum() < 0 || number.compareTo(MAX_AMOUNT) > 0 ? null : number;
    }

    static @Nullable Unit unit(JsonNode node) {
        return constant(node, "unit", Unit.class);
    }

    static <E extends Enum<E>> @Nullable E constant(JsonNode node, String field, Class<E> type) {
        JsonNode value = node.path(field);
        if (!value.isString()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.asString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** OCR text and the model's quote may differ in case and spacing only. */
    static String comparable(String text) {
        return SPACES.matcher(text.strip()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }
}
