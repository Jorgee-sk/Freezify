package com.freezify.scanning.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.freezify.food.Quantity;
import com.freezify.food.Unit;
import com.freezify.scanning.internal.ReceiptParser.Line;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReceiptParserTests {

    private final ReceiptParser parser = new ReceiptParser();

    @Test
    void readsTheProductsOfAReceiptAndLeavesOutEverythingElse() {
        ReceiptParser.Receipt receipt = parser.parse("""
                MERCADONA, S.A. A-46103834
                C/ MAYOR, 12 46001 VALENCIA
                TELEFONO: 963000000
                12/05/2026 18:23  OP: 104578
                FACTURA SIMPLIFICADA: 2345-012-123456
                Descripción          P. Unit   Importe
                1 LECHE ENTERA 1L                0,89
                2 YOGUR NATURAL       0,45       0,90
                1 TOMATE PERA
                0,856 kg   2,10 €/kg            1,80
                1 HUEVOS L 12 UDS                2,35
                TOTAL (€)                        5,94
                TARJETA BANCARIA                 5,94
                IVA  BASE IMPONIBLE (€)  CUOTA (€)
                4%   5,71                0,23
                """);

        assertThat(receipt.date()).isEqualTo(LocalDate.of(2026, 5, 12));
        assertThat(receipt.lines()).containsExactly(
                new Line("LECHE ENTERA", quantity("1", Unit.LITER), money("0.89")),
                new Line("YOGUR NATURAL", quantity("2", Unit.UNIT), money("0.90")),
                new Line("TOMATE PERA", quantity("0.856", Unit.KILOGRAM), money("1.80")),
                new Line("HUEVOS L", quantity("12", Unit.UNIT), money("2.35")));
    }

    @Test
    void readsReceiptsThatPrintTheCountUnderTheProduct() {
        ReceiptParser.Receipt receipt = parser.parse("""
                LIDL SUPERMERCADOS S.A.U.
                EUR
                Leche semidesnatada 6x1L      5,34 A
                Pan de molde                  1,15 A
                Yogur griego 4x125g           1,29 A
                  3 x 1,29                    3,87 A
                Plátano de Canarias           1,89 A
                  0,945 kg x 2,00 EUR/kg      1,89 A
                Descuento Lidl Plus          -0,50
                Bolsa                         0,10 B
                Total                        12,95
                """);

        assertThat(receipt.date()).isNull();
        assertThat(receipt.lines()).containsExactly(
                new Line("Leche semidesnatada", quantity("6", Unit.LITER), money("5.34")),
                new Line("Pan de molde", null, money("1.15")),
                // Three packs of four 125 g yogurts.
                new Line("Yogur griego", quantity("1500", Unit.GRAM), money("3.87")),
                new Line("Plátano de Canarias", quantity("0.945", Unit.KILOGRAM), money("1.89")),
                new Line("Bolsa", null, money("0.10")));
    }

    @Test
    void aProductWithoutPriceWaitsForTheLineThatSaysHowMuchWasBought() {
        ReceiptParser.Receipt receipt = parser.parse("""
                PECH POLLO
                2 x 3,50     7,00
                CERV TOSTADA 33CL   0,79
                """);

        assertThat(receipt.lines()).containsExactly(
                new Line("PECH POLLO", quantity("2", Unit.UNIT), money("7.00")),
                new Line("CERV TOSTADA", quantity("330", Unit.MILLILITER), money("0.79")));
    }

    @Test
    void textWithoutProductsGivesNoLines() {
        assertThat(parser.parse("Gracias por su visita\n\n  \nwww.example.com").lines()).isEmpty();
        // An impossible date is not a date.
        assertThat(parser.parse("31/02/2026").date()).isNull();
    }

    @Test
    void theWordsOfAProductReadElsewhereLoseSizesAndPrices() {
        assertThat(ReceiptParser.productWords("LECHE ENTERA 1L 0,89")).isEqualTo("LECHE ENTERA");
        assertThat(ReceiptParser.productWords("YOGUR 4X125G")).isEqualTo("YOGUR");
        assertThat(ReceiptParser.productWords("HUEVOS 12 UDS")).isEqualTo("HUEVOS");
    }

    private static Quantity quantity(String amount, Unit unit) {
        return Quantity.of(amount, unit);
    }

    private static BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }
}
