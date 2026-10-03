package com.freezify.shopping.internal;

/** Why a line is on the list. */
public enum ShoppingItemOrigin {
    /** Someone put it there, or changed it. Only people change it. */
    MANUAL,
    /** The meal plan lacks it. Filling the list from the plan again recomputes it. */
    PLAN
}
