package com.freezify.recipes.internal;

import com.freezify.food.Food;
import com.freezify.food.FoodCatalog;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Finds foods named in a text, such as the steps of a generated recipe. A model told to use only some
 * ingredients may still write "add the cream"; if cream is not one of them, the recipe cannot be trusted.
 *
 * <p>It knows the foods of the catalog and, besides, a hand-written list of foods that are not in it, chosen
 * because they are among the allergens that must be declared in the European Union (nuts, sesame, soy,
 * molluscs, crustaceans, fish, mustard, celery, lupin, gluten) or are meats someone may not eat. A recipe that
 * names one of them without using it is not trusted either.
 */
final class FoodMentions {

    private static final Set<String> FILLERS = Set.of(
            "de", "del", "la", "el", "en", "con", "para", "y", "al", "a", "los", "las", "of", "the", "and", "with");

    /** Foods outside the catalog, by language. Plurals are recognised; spellings without accents are enough. */
    private static final Map<String, List<String>> OTHER_FOODS = Map.of(
            "es",
            List.of(
                    "almendra", "nuez", "nueces", "avellana", "cacahuete", "pistacho", "anacardo", "pinon",
                    "frutos secos", "sesamo", "tahini", "soja", "tofu", "mejillon", "almeja", "calamar",
                    "sepia", "pulpo", "berberecho", "ostra", "langostino", "cangrejo", "bogavante", "bacalao",
                    "sardina", "anchoa", "boqueron", "atun", "mostaza", "apio", "altramuz", "pan rallado",
                    "cuscus", "seitan", "jamon", "pollo", "cerdo", "ternera", "pavo", "cordero"),
            "en",
            List.of(
                    "almond", "walnut", "hazelnut", "peanut", "pistachio", "cashew", "pine nut", "nuts",
                    "sesame", "tahini", "soy", "tofu", "mussel", "clam", "squid", "octopus", "oyster",
                    "crab", "lobster", "cod", "sardine", "anchovy", "tuna", "mustard", "celery", "lupin",
                    "breadcrumbs", "couscous", "seitan", "ham", "chicken", "pork", "beef", "turkey", "lamb"));

    private FoodMentions() {}

    /**
     * @param allowed      the catalog foods the text may name; a name inside the name of one of them ("tomate" in
     *     "tomate triturado") does not count
     * @param allowedNames the names of everything the recipe uses: a food outside the catalog is fine when one of
     *     them contains it ("pollo" when it uses "Pechuga de pollo")
     */
    static boolean mentionsOtherFood(
            Collection<Food> catalog, Set<UUID> allowed, Collection<String> allowedNames, String language, String text) {
        List<String> words = words(text);
        List<String> used = allowedNames.stream().flatMap(name -> words(name).stream()).toList();
        for (String other : OTHER_FOODS.getOrDefault(language, List.of())) {
            List<String> name = words(other);
            boolean usesIt = name.stream().allMatch(word -> used.stream().anyMatch(usedWord -> same(word, usedWord)));
            if (!usesIt && find(name, words, 0) >= 0) {
                return true;
            }
        }
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
