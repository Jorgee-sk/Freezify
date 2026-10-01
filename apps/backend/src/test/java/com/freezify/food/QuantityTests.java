package com.freezify.food;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.freezify.food.Quantity.IncompatibleUnitsException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class QuantityTests {

    @Test
    void convertsWithinADimension() {
        assertThat(Quantity.of("1.5", Unit.KILOGRAM).convertTo(Unit.GRAM)).isEqualTo(Quantity.of("1500", Unit.GRAM));
        assertThat(Quantity.of("250", Unit.GRAM).convertTo(Unit.KILOGRAM)).isEqualTo(Quantity.of("0.25", Unit.KILOGRAM));
        assertThat(Quantity.of("750", Unit.MILLILITER).convertTo(Unit.LITER)).isEqualTo(Quantity.of("0.75", Unit.LITER));
        assertThat(Quantity.of("2", Unit.LITER).convertTo(Unit.MILLILITER))
                .isEqualTo(Quantity.of("2000", Unit.MILLILITER));
        assertThat(Quantity.of("6", Unit.UNIT).convertTo(Unit.UNIT)).isEqualTo(Quantity.of("6", Unit.UNIT));
    }

    @Test
    void refusesToConvertAcrossDimensions() {
        assertThatThrownBy(() -> Quantity.of("500", Unit.GRAM).convertTo(Unit.LITER))
                .isInstanceOf(IncompatibleUnitsException.class);
        assertThatThrownBy(() -> Quantity.of("2", Unit.UNIT).convertTo(Unit.GRAM))
                .isInstanceOf(IncompatibleUnitsException.class);
        assertThatThrownBy(() -> Quantity.of("1", Unit.KILOGRAM).plus(Quantity.of("1", Unit.UNIT)))
                .isInstanceOf(IncompatibleUnitsException.class);
    }

    @Test
    void addsAndSubtractsKeepingTheUnitOfTheLeftOperand() {
        // The shopping list example of the product spec: 500 g + 300 g needed, 200 g at home.
        Quantity needed = Quantity.of("500", Unit.GRAM).plus(Quantity.of("0.3", Unit.KILOGRAM));
        assertThat(needed).isEqualTo(Quantity.of("800", Unit.GRAM));
        assertThat(needed.minus(Quantity.of("200", Unit.GRAM))).isEqualTo(Quantity.of("600", Unit.GRAM));

        assertThat(Quantity.of("1", Unit.KILOGRAM).minus(Quantity.of("250", Unit.GRAM)))
                .isEqualTo(Quantity.of("0.75", Unit.KILOGRAM));
    }

    @Test
    void cannotGoBelowZero() {
        assertThatThrownBy(() -> Quantity.of("1", Unit.UNIT).minus(Quantity.of("2", Unit.UNIT)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Quantity.of("-1", Unit.GRAM)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void comparesAcrossUnits() {
        assertThat(Quantity.of("900", Unit.GRAM).isLessThan(Quantity.of("1", Unit.KILOGRAM))).isTrue();
        assertThat(Quantity.of("1", Unit.KILOGRAM).isLessThan(Quantity.of("1000", Unit.GRAM))).isFalse();
        assertThat(Quantity.of("0", Unit.LITER).isZero()).isTrue();
    }

    @Test
    void equalAmountsAreEqualWhateverTheirScale() {
        assertThat(Quantity.of("2", Unit.UNIT)).isEqualTo(Quantity.of("2.000", Unit.UNIT));
    }

    @Test
    void keepsThreeDecimals() {
        assertThat(Quantity.of("0.3335", Unit.KILOGRAM).amount()).isEqualByComparingTo("0.334");
        assertThat(Quantity.of("1", Unit.GRAM).convertTo(Unit.KILOGRAM).amount()).isEqualByComparingTo("0.001");
    }

    @Test
    void computesTheFractionOfAWhole() {
        assertThat(Quantity.of("250", Unit.GRAM).fractionOf(Quantity.of("1", Unit.KILOGRAM)))
                .isEqualByComparingTo(new BigDecimal("0.25"));
    }
}
