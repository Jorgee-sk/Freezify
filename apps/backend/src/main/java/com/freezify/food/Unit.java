package com.freezify.food;

import java.math.BigDecimal;

public enum Unit {
    GRAM(Dimension.MASS, 1),
    KILOGRAM(Dimension.MASS, 1000),
    MILLILITER(Dimension.VOLUME, 1),
    LITER(Dimension.VOLUME, 1000),
    UNIT(Dimension.COUNT, 1);

    /** Units of different dimensions cannot be converted into each other. */
    public enum Dimension {
        MASS,
        VOLUME,
        COUNT
    }

    private final Dimension dimension;
    private final BigDecimal baseFactor;

    Unit(Dimension dimension, long baseFactor) {
        this.dimension = dimension;
        this.baseFactor = BigDecimal.valueOf(baseFactor);
    }

    public Dimension dimension() {
        return dimension;
    }

    /** How many base units (gram, milliliter, unit) one of this unit is. */
    BigDecimal baseFactor() {
        return baseFactor;
    }
}
