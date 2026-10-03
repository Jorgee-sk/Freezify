package com.freezify.shopping.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.freezify.food.Quantity;
import com.freezify.food.Unit;
import org.junit.jupiter.api.Test;

class ShoppingListServiceTests {

    @Test
    void largeAmountsAreWrittenInTheLargerUnit() {
        assertThat(ShoppingListService.readable(Quantity.of("1500", Unit.GRAM))).isEqualTo(Quantity.of("1.5", Unit.KILOGRAM));
        assertThat(ShoppingListService.readable(Quantity.of("1000", Unit.MILLILITER))).isEqualTo(Quantity.of("1", Unit.LITER));
        assertThat(ShoppingListService.readable(Quantity.of("999", Unit.GRAM))).isEqualTo(Quantity.of("999", Unit.GRAM));
        assertThat(ShoppingListService.readable(Quantity.of("1200", Unit.UNIT))).isEqualTo(Quantity.of("1200", Unit.UNIT));
    }
}
