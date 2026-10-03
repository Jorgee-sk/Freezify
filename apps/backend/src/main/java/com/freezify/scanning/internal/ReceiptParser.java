package com.freezify.scanning.internal;

import com.freezify.food.FoodCatalog;
import com.freezify.food.Quantity;
import com.freezify.food.Unit;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Reads the products of a supermarket receipt from its OCR text with rules, no model involved. It knows the usual
 * shapes of Spanish receipts: a product with its price at the end of the line, an optional number of units before
 * it, sizes such as "1L" or "6X125G" in the name, a weighed product followed by a "0,456 kg x 2,10 €/kg" line, and
 * an "N x price" line under a product bought several times. Header, totals, taxes and payment are left out.
 *
 * <p>It does not try to be clever: whatever it gets wrong is corrected in the review, which always comes before
 * anything reaches the inventory.
 */
final class ReceiptParser {

    /**
     * A product read from the receipt.
     *
     * @param text     the product words as printed, without count, size or prices ("TOMATE PERA")
     * @param quantity how much was bought, when the receipt says it
     * @param price    what was paid for the line, when the receipt says it
     */
    record Line(String text, @Nullable Quantity quantity, @Nullable BigDecimal price) {}

    /** @param date the date printed on the receipt, when one was found */
    record Receipt(List<Line> lines, @Nullable LocalDate date) {}

    private static final String MONEY = "(\\d{1,4}[.,]\\d{2})";
    /** A product line: optional count, words, optional unit price, price, optional euro sign and tax letter. */
    private static final Pattern PRICED = Pattern.compile(
            "^(?:(\\d{1,2})\\s*[x×]?\\s+)?(.*?\\p{L}.*?)\\s+(?:" + MONEY + "\\s+)?(-?)" + MONEY
                    + "\\s*(?:€|eur)?(?:\\s+[a-z]{1,2})?$",
            Pattern.CASE_INSENSITIVE);
    /** "0,456 kg x 2,10 €/kg 0,96" under a weighed product. */
    private static final Pattern WEIGHED = Pattern.compile(
            "^(\\d{1,3}[.,]\\d{1,3})\\s*kg\\b.*?/\\s*kg\\s+" + MONEY + "\\s*(?:€|eur)?(?:\\s+[a-z]{1,2})?$",
            Pattern.CASE_INSENSITIVE);
    /** "3 x 0,89" or "3 x 0,89 2,67" under a product bought several times. */
    private static final Pattern MULTIPLIED = Pattern.compile(
            "^(\\d{1,2})\\s*[x×]\\s*" + MONEY + "(?:\\s*(?:€|eur))?(?:\\s+" + MONEY + ")?.*$",
            Pattern.CASE_INSENSITIVE);
    /** A product line whose price is on the next lines, or a header line. */
    private static final Pattern UNPRICED = Pattern.compile("^(?:(\\d{1,2})\\s*[x×]?\\s+)?(\\p{L}.*)$");
    /** "6X125G", "4 x 1L": a pack of several units of a size. */
    private static final Pattern PACK = Pattern.compile(
            "\\b(\\d{1,2})\\s*[x×]\\s*(\\d{1,4}(?:[.,]\\d{1,3})?)\\s*(kg|grs?|g|ml|cl|lt?)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SIZE =
            Pattern.compile("\\b(\\d{1,4}(?:[.,]\\d{1,3})?)\\s*(kg|grs?|g|ml|cl|lt?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNITS = Pattern.compile("\\b(\\d{1,2})\\s*(?:uds?|unidades|u)\\b\\.?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE = Pattern.compile("\\b(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4}|\\d{2})\\b");

    /** Lines from which on nothing is a product. */
    private static final List<String> END = List.of(
            "total", "subtotal", "importe total", "a pagar", "entregado", "efectivo", "tarjeta", "cambio",
            "base imponible", "iva", "forma de pago");
    /** Words that only appear in headers and column titles. */
    private static final Pattern NOISE = Pattern.compile(
            "\\b(cif|nif|n\\.i\\.f|factura|tel[eé]fono|telf?|tlf|ticket|descripci[oó]n|art[ií]culo|p\\.?\\s?unit|importe"
                    + "|cantidad|www|s\\.a\\.?|s\\.l\\.?|c/|avda|calle|op|cajero|hora)\\b",
            Pattern.CASE_INSENSITIVE);

    Receipt parse(String text) {
        List<Line> lines = new ArrayList<>();
        LocalDate date = null;
        Pending pending = null;
        // The product as printed, sizes included, of the last line read: a count under it may scale its size.
        String lastProduct = null;
        for (String raw : text.split("\\R")) {
            String line = raw.strip().replaceAll("\\s+", " ");
            if (line.isEmpty()) {
                continue;
            }
            if (date == null) {
                date = date(line);
            }
            String lower = FoodCatalog.normalize(line);
            if (END.stream().anyMatch(word -> lower.equals(word) || lower.startsWith(word + " ")
                    || lower.startsWith(word + ":") || lower.startsWith(word + "("))) {
                break;
            }

            Matcher weighed = WEIGHED.matcher(line);
            if (weighed.matches()) {
                Quantity weight = new Quantity(money(weighed.group(1)), Unit.KILOGRAM);
                BigDecimal price = money(weighed.group(2));
                if (pending != null) {
                    lines.add(new Line(words(pending.text()), weight, price));
                    lastProduct = pending.text();
                    pending = null;
                } else if (!lines.isEmpty()) {
                    Line previous = lines.removeLast();
                    lines.add(new Line(previous.text(), weight, price));
                }
                continue;
            }

            Matcher multiplied = MULTIPLIED.matcher(line);
            if (multiplied.matches()) {
                int count = Integer.parseInt(multiplied.group(1));
                BigDecimal unitPrice = money(multiplied.group(2));
                BigDecimal total = multiplied.group(3) == null
                        ? unitPrice.multiply(BigDecimal.valueOf(count))
                        : money(multiplied.group(3));
                if (pending != null) {
                    lines.add(read(pending.text(), count, total));
                    lastProduct = pending.text();
                    pending = null;
                } else if (lastProduct != null) {
                    // The line above already had a price, for one unit; this one has the total.
                    lines.removeLast();
                    lines.add(read(lastProduct, count, total));
                }
                continue;
            }

            if (NOISE.matcher(line).find() || date(line) != null) {
                pending = null;
                continue;
            }

            Matcher priced = PRICED.matcher(line);
            if (priced.matches()) {
                pending = null;
                if (!priced.group(4).isEmpty()) {
                    // A discount: it changes what was paid, not what was bought.
                    continue;
                }
                int count = priced.group(1) == null ? 0 : Integer.parseInt(priced.group(1));
                lines.add(read(priced.group(2), count, money(priced.group(5))));
                lastProduct = priced.group(2);
                continue;
            }

            Matcher unpriced = UNPRICED.matcher(line);
            pending = unpriced.matches() ? new Pending(unpriced.group(2)) : null;
        }
        return new Receipt(List.copyOf(lines), date);
    }

    /** The product words of a line read by someone else (a model), without sizes and prices. */
    static String productWords(String text) {
        String words = SIZE.matcher(PACK.matcher(text).replaceAll(" ")).replaceAll(" ");
        words = UNITS.matcher(words).replaceAll(" ");
        words = words.replaceAll("-?\\d{1,4}[.,]\\d{2}\\s*(?:€|eur)?", " ");
        return words.replaceAll("[*#]+", " ").strip().replaceAll("\\s+", " ");
    }

    private record Pending(String text) {}

    /**
     * @param count units bought as printed before the product, or 0 when none was printed
     */
    private static Line read(String product, int count, BigDecimal price) {
        Quantity size = null;
        int bought = count == 0 ? 1 : count;

        Matcher pack = PACK.matcher(product);
        Matcher single = SIZE.matcher(product);
        Matcher units = UNITS.matcher(product);
        if (pack.find()) {
            size = scaled(amount(pack.group(2), pack.group(3)), Integer.parseInt(pack.group(1)));
        } else if (single.find()) {
            size = amount(single.group(1), single.group(2));
        } else if (units.find()) {
            size = new Quantity(BigDecimal.valueOf(Integer.parseInt(units.group(1))), Unit.UNIT);
        }

        Quantity quantity;
        if (size != null) {
            quantity = scaled(size, bought);
        } else {
            quantity = count == 0 ? null : new Quantity(BigDecimal.valueOf(count), Unit.UNIT);
        }
        return new Line(words(product), quantity, price);
    }

    private static String words(String product) {
        String words = productWords(product);
        return words.isEmpty() ? product.strip() : words;
    }

    private static Quantity amount(String number, String unit) {
        BigDecimal value = money(number);
        return switch (unit.toLowerCase(Locale.ROOT)) {
            case "kg" -> new Quantity(value, Unit.KILOGRAM);
            case "g", "gr", "grs" -> new Quantity(value, Unit.GRAM);
            case "ml" -> new Quantity(value, Unit.MILLILITER);
            case "cl" -> new Quantity(value.multiply(BigDecimal.TEN), Unit.MILLILITER);
            default -> new Quantity(value, Unit.LITER);
        };
    }

    private static Quantity scaled(Quantity quantity, int times) {
        return new Quantity(quantity.amount().multiply(BigDecimal.valueOf(times)), quantity.unit());
    }

    private static BigDecimal money(String number) {
        return new BigDecimal(number.replace(',', '.'));
    }

    private static @Nullable LocalDate date(String line) {
        Matcher matcher = DATE.matcher(line);
        if (!matcher.find()) {
            return null;
        }
        int year = Integer.parseInt(matcher.group(3));
        try {
            return LocalDate.of(year < 100 ? 2000 + year : year, Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(1)));
        } catch (DateTimeException e) {
            return null;
        }
    }
}
