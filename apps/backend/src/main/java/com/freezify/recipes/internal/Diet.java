package com.freezify.recipes.internal;

import com.freezify.recipes.Recipe;
import com.freezify.food.FoodTrait;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * What is not cooked in a household: a diet, plus anything else to avoid. It is a hard filter, not a
 * preference: a recipe that contains any of it is never offered.
 */
public record Diet(Type type, Set<FoodTrait> avoided) {

    public enum Type {
        NONE(EnumSet.noneOf(FoodTrait.class)),
        VEGETARIAN(EnumSet.of(FoodTrait.MEAT, FoodTrait.PORK, FoodTrait.FISH, FoodTrait.SHELLFISH)),
        VEGAN(EnumSet.of(
                FoodTrait.MEAT, FoodTrait.PORK, FoodTrait.FISH, FoodTrait.SHELLFISH, FoodTrait.DAIRY, FoodTrait.EGG));

        private final Set<FoodTrait> excludes;

        Type(Set<FoodTrait> excludes) {
            this.excludes = excludes;
        }
    }

    public Diet {
        // Sorted, so that the same restrictions always read the same.
        avoided = Collections.unmodifiableSet(new TreeSet<>(avoided));
    }

    static Diet none() {
        return new Diet(Type.NONE, Set.of());
    }

    /** Everything the household does not eat: what the diet rules out and what it avoids besides. */
    Set<FoodTrait> excluded() {
        Set<FoodTrait> all = EnumSet.noneOf(FoodTrait.class);
        all.addAll(type.excludes);
        all.addAll(avoided);
        return all;
    }

    boolean allows(Recipe recipe) {
        return Collections.disjoint(excluded(), recipe.contains());
    }
}
