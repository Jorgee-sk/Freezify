package com.freezify.food;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FoodCatalog {

    Optional<Food> findById(UUID id);

    /**
     * Foods whose name in {@code language} matches what the user is typing, ignoring case and accents.
     * Names that start with the text come before names that merely contain it.
     */
    List<Food> search(String text, String language, int limit);

    /** Lower case, without accents: the form in which names are compared. */
    static String normalize(String text) {
        String decomposed = java.text.Normalizer.normalize(text.strip(), java.text.Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").toLowerCase(java.util.Locale.ROOT);
    }
}
