package com.freezify.scanning.internal;

import com.freezify.food.Food;
import com.freezify.food.FoodAliases;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.freezify.scanning.internal.ScanViews.MatchedBy;
import org.jspecify.annotations.Nullable;

/**
 * Finds the catalog food a receipt line is about, by words: a food matches when every word of one of its names
 * (or of an alias) is in the line, plurals included. The most specific match wins ("tomate triturado" over
 * "tomate"); between equally specific ones, the food named first in the line wins, because Spanish receipts put
 * the product before what describes it ("CHOCOLATE CON LECHE"). Nothing is decided when that still leaves two
 * foods: the person chooses among the candidates.
 */
final class FoodMatcher {

    /**
     * @param foodId     the suggested food, if any
     * @param candidates foods the line may be about, the most likely first, the suggested one included
     */
    record Match(@Nullable UUID foodId, MatchedBy how, List<UUID> candidates) {}

    private record Phrase(UUID foodId, List<String> words, MatchedBy how) {}

    private record Hit(UUID foodId, int words, int position, MatchedBy how) {}

    private static final int MAX_CANDIDATES = 4;

    private final List<Phrase> phrases = new ArrayList<>();
    private final Map<String, UUID> learned;

    /**
     * @param shared  the shared aliases, text to food
     * @param learned what the household confirmed, text (as {@link #key}) to food
     */
    FoodMatcher(List<Food> foods, Map<String, UUID> shared, Map<String, UUID> learned) {
        for (Food food : foods) {
            phrases.add(new Phrase(food.id(), words(food.nameEs()), MatchedBy.NAME));
            phrases.add(new Phrase(food.id(), words(food.nameEn()), MatchedBy.NAME));
        }
        shared.forEach((text, foodId) -> phrases.add(new Phrase(foodId, words(text), MatchedBy.ALIAS)));
        this.learned = learned;
    }

    /** The form in which a line's text is remembered: its words, normalized. */
    static String key(String text) {
        return FoodAliases.key(text);
    }

    /**
     * @param texts the ways the line was read, the most reliable first (a model's full name, then the printed
     *     text); the first one that matches wins
     */
    Match match(List<String> texts) {
        for (String text : texts) {
            UUID known = learned.get(key(text));
            if (known != null) {
                return new Match(known, MatchedBy.LEARNED, List.of(known));
            }
        }
        List<UUID> partial = new ArrayList<>();
        for (String text : texts) {
            List<String> line = words(text);
            List<Hit> hits = new ArrayList<>();
            for (Phrase phrase : phrases) {
                int position = positionIn(phrase.words(), line);
                if (position >= 0) {
                    hits.add(new Hit(phrase.foodId(), phrase.words().size(), position, phrase.how()));
                } else if (phrase.words().stream().anyMatch(word -> line.stream().anyMatch(other -> same(word, other)))) {
                    partial.add(phrase.foodId());
                }
            }
            if (hits.isEmpty()) {
                continue;
            }
            hits.sort(Comparator.comparingInt(Hit::words).reversed().thenComparingInt(Hit::position));
            Hit best = hits.getFirst();
            boolean tie = hits.stream()
                    .anyMatch(hit -> !hit.foodId().equals(best.foodId())
                            && hit.words() == best.words()
                            && hit.position() == best.position());
            List<UUID> candidates = distinct(hits.stream().map(Hit::foodId).toList(), partial);
            return tie ? new Match(null, MatchedBy.NONE, candidates) : new Match(best.foodId(), best.how(), candidates);
        }
        return new Match(null, MatchedBy.NONE, distinct(List.of(), partial));
    }

    private static List<UUID> distinct(List<UUID> first, List<UUID> then) {
        Map<UUID, Boolean> ordered = new LinkedHashMap<>();
        first.forEach(id -> ordered.putIfAbsent(id, true));
        then.forEach(id -> ordered.putIfAbsent(id, true));
        return ordered.keySet().stream().limit(MAX_CANDIDATES).toList();
    }

    /** Where the first word of the phrase is in the line, when all its words are there; -1 otherwise. */
    private static int positionIn(List<String> phrase, List<String> line) {
        if (phrase.isEmpty()) {
            return -1;
        }
        int first = -1;
        for (String word : phrase) {
            int at = -1;
            for (int i = 0; i < line.size(); i++) {
                if (same(word, line.get(i))) {
                    at = i;
                    break;
                }
            }
            if (at < 0) {
                return -1;
            }
            if (first < 0) {
                first = at;
            }
        }
        return first;
    }

    /** Equal, or one is the plural of the other ("tomates", "champinones"). */
    private static boolean same(String a, String b) {
        if (a.equals(b)) {
            return true;
        }
        String shorter = a.length() < b.length() ? a : b;
        String longer = a.length() < b.length() ? b : a;
        return shorter.length() >= 3
                && (longer.equals(shorter + "s") || longer.equals(shorter + "es"));
    }

    private static List<String> words(String text) {
        return FoodAliases.words(text);
    }
}
