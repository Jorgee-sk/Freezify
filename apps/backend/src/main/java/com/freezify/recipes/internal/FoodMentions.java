package com.freezify.recipes.internal;

import com.freezify.food.Food;
import com.freezify.food.FoodCatalog;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Finds catalog foods named in a text, such as the steps of a generated recipe. A model told to use only some
 * ingredients may still write "add the cream"; if cream is not one of them, the recipe cannot be trusted.
 */
final class FoodMentions {

    private static final Set<String> FILLERS = Set.of(
            "de", "del", "la", "el", "en", "con", "para", "y", "al", "a", "los", "las", "of", "the", "and", "with");

    private FoodMentions() {}

    /**
     * @param allowed the foods the text may name; a name inside the name of one of them ("tomate" in "tomate
     *     triturado") does not count
     */
    static boolean mentionsOtherFood(Collection<Food> catalog, Set<UUID> allowed, String language, String text) {
        List<String> words = words(text);
        boolean[] covered = new boolean[words.size()];
        for (Food food : catalog) {
            if (allowed.contains(food.id())) {
                List<String> name = words(food.name(language));
                for (int at = find(name, words, 0); at >= 0; at = find(name, words, at + 1)) {
                    Arrays.fill(covered, at, at + name.size(), true);
                }
            }
        }
        for (Food food : catalog) {
            if (allowed.contains(food.id())) {
                continue;
            }
            List<String> name = words(food.name(language));
            for (int at = find(name, words, 0); at >= 0; at = find(name, words, at + 1)) {
                for (int i = at; i < at + name.size(); i++) {
                    if (!covered[i]) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Where {@code name} appears in {@code words} at or after {@code from}, word after word; -1 if it does not. */
    private static int find(List<String> name, List<String> words, int from) {
        if (name.isEmpty()) {
            return -1;
        }
        for (int start = from; start + name.size() <= words.size(); start++) {
            boolean all = true;
            for (int i = 0; i < name.size() && all; i++) {
                all = same(name.get(i), words.get(start + i));
            }
            if (all) {
                return start;
            }
        }
        return -1;
    }

    /** Equal, or one is the plural of the other. */
    private static boolean same(String a, String b) {
        if (a.equals(b)) {
            return true;
        }
        String shorter = a.length() < b.length() ? a : b;
        String longer = a.length() < b.length() ? b : a;
        return shorter.length() >= 3 && (longer.equals(shorter + "s") || longer.equals(shorter + "es"));
    }

    private static List<String> words(String text) {
        return Arrays.stream(FoodCatalog.normalize(text).split("[^a-z0-9]+"))
                .filter(word -> !word.isEmpty() && !FILLERS.contains(word))
                .toList();
    }
}
