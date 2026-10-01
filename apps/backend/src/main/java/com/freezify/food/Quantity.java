package com.freezify.food;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An amount of food in a unit. Amounts are kept to three decimals, which is one gram when working in kilograms.
 */
public record Quantity(BigDecimal amount, Unit unit) {

    public static final int SCALE = 3;

    public Quantity {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(unit, "unit");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("A quantity cannot be negative");
        }
        amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Quantity of(String amount, Unit unit) {
        return new Quantity(new BigDecimal(amount), unit);
    }

    public boolean isCompatibleWith(Unit other) {
        return unit.dimension() == other.dimension();
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    /**
     * @throws IncompatibleUnitsException when the target unit measures something else (grams into liters)
     */
    public Quantity convertTo(Unit target) {
        if (!isCompatibleWith(target)) {
            throw new IncompatibleUnitsException(unit, target);
        }
        if (unit == target) {
            return this;
        }
        BigDecimal converted =
                amount.multiply(unit.baseFactor()).divide(target.baseFactor(), SCALE, RoundingMode.HALF_UP);
        return new Quantity(converted, target);
    }

    /** The result keeps the unit of this quantity. */
    public Quantity plus(Quantity other) {
        return new Quantity(amount.add(other.convertTo(unit).amount()), unit);
    }

    /**
     * The result keeps the unit of this quantity.
     *
     * @throws IllegalArgumentException when {@code other} is larger than this quantity
     */
    public Quantity minus(Quantity other) {
        return new Quantity(amount.subtract(other.convertTo(unit).amount()), unit);
    }

    public boolean isLessThan(Quantity other) {
        return amount.compareTo(other.convertTo(unit).amount()) < 0;
    }

    /** This quantity as a fraction of {@code whole}, between 0 and 1 when it is a part of it. */
    public BigDecimal fractionOf(Quantity whole) {
        return amount.divide(whole.convertTo(unit).amount(), 6, RoundingMode.HALF_UP);
    }

    public static class IncompatibleUnitsException extends IllegalArgumentException {
        public IncompatibleUnitsException(Unit from, Unit to) {
            super("Cannot convert " + from + " into " + to);
        }
    }
}
