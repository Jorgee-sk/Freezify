package com.freezify.food;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Other names of catalog foods: abbreviations that receipts use ("PECH POLLO"), shared by everyone, and the names
 * each household confirmed for a food when reviewing what it bought. A household's own names win.
 */
public interface FoodAliases {

    /** Words that do not tell foods apart ("pechuga de pollo" and "pechuga pollo" are the same). */
    Set<String> FILLERS = Set.of("de", "del", "la", "el", "en", "con", "para", "y", "al", "a");

    /** The words of a name as they are compared: normalized, without small words or bare numbers. */
    static List<String> words(String text) {
        return Arrays.stream(FoodCatalog.normalize(text).split("[^a-z0-9]+"))
                .filter(word -> !word.isEmpty() && !FILLERS.contains(word) && !word.chars().allMatch(Character::isDigit))
                .toList();
    }

    /** The form in which a name is remembered: its words, joined. */
    static String key(String text) {
        return String.join(" ", words(text));
    }

    /** The abbreviations shared by everyone, by {@link #key}. */
    Map<String, UUID> shared();

    /** What the household confirmed, by {@link #key}. Not scoped to a user: the caller checks membership. */
    Map<String, UUID> learned(UUID householdId);

    /** The food exactly this name stands for in the household, if any: its own names first, then the shared ones. */
    Optional<UUID> foodFor(UUID householdId, String name);

    /**
     * Remembers that, in the household, this name is that food; with {@code null}, that it is no catalog food,
     * which forgets an earlier choice.
     */
    void remember(UUID householdId, String name, @Nullable UUID foodId);
}
