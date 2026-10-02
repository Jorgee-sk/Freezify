package com.freezify.mealplanning.internal;

/** Who chose the recipe of a meal. Generating again may replace what the generator chose, never what a member chose. */
public enum MealOrigin {
    MANUAL,
    GENERATED
}
