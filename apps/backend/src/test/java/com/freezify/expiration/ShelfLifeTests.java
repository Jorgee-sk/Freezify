package com.freezify.expiration;

import static org.assertj.core.api.Assertions.assertThat;

import com.freezify.expiration.ExpirationEstimator.Expiration;
import com.freezify.expiration.ExpirationEstimator.Input;
import com.freezify.food.FoodCategory;
import com.freezify.food.StorageLocation;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ShelfLifeTests {

    private static final LocalDate PURCHASED = LocalDate.of(2026, 10, 1);
    private static final LocalDate OPENED = LocalDate.of(2026, 10, 4);

    /** Fresh food: keeps a week, opening changes nothing. */
    private static final ShelfLife FRESH = new ShelfLife(7, null);

    /** Packaged food: only its label tells how long it keeps, but once opened it lasts three days. */
    private static final ShelfLife PACKAGED = new ShelfLife(null, 3);

    /** Keeps three days either way. */
    private static final ShelfLife BOTH = new ShelfLife(3, 3);

    @Test
    void withoutAUserDateOrARuleThereIsNoDate() {
        assertThat(ShelfLife.resolve(input(null, null), null)).isEmpty();
        assertThat(ShelfLife.resolve(input(null, OPENED), null)).isEmpty();
    }

    @Test
    void theUserDateIsKeptWhenNothingShortensIt() {
        LocalDate onThePackage = LocalDate.of(2026, 12, 24);

        assertThat(ShelfLife.resolve(input(onThePackage, null), null)).contains(user(onThePackage));
        assertThat(ShelfLife.resolve(input(onThePackage, null), FRESH)).contains(user(onThePackage));
        assertThat(ShelfLife.resolve(input(onThePackage, null), PACKAGED)).contains(user(onThePackage));
        // Opened, but for this food opening changes nothing.
        assertThat(ShelfLife.resolve(input(onThePackage, OPENED), FRESH)).contains(user(onThePackage));
    }

    @Test
    void freshFoodWithoutADateIsEstimatedFromThePurchase() {
        assertThat(ShelfLife.resolve(input(null, null), FRESH)).contains(estimated(LocalDate.of(2026, 10, 8)));
        assertThat(ShelfLife.resolve(input(null, OPENED), FRESH)).contains(estimated(LocalDate.of(2026, 10, 8)));
    }

    @Test
    void packagedFoodWithoutADateHasNoneUntilItIsOpened() {
        assertThat(ShelfLife.resolve(input(null, null), PACKAGED)).isEmpty();
        assertThat(ShelfLife.resolve(input(null, OPENED), PACKAGED)).contains(estimated(LocalDate.of(2026, 10, 7)));
    }

    @Test
    void openingShortensADateThatIsFurtherAway() {
        // The carton says December, but opened milk lasts three days.
        assertThat(ShelfLife.resolve(input(LocalDate.of(2026, 12, 24), OPENED), PACKAGED))
                .contains(estimated(LocalDate.of(2026, 10, 7)));
    }

    @Test
    void openingNeverExtendsADate() {
        LocalDate tomorrow = OPENED.plusDays(1);

        assertThat(ShelfLife.resolve(input(tomorrow, OPENED), PACKAGED)).contains(user(tomorrow));
        // Bought on the 1st and good for three days: opening it on the 4th does not buy three more.
        assertThat(ShelfLife.resolve(input(null, OPENED), BOTH)).contains(estimated(LocalDate.of(2026, 10, 4)));
    }

    @Test
    void onATieTheUserDateWins() {
        LocalDate sameDay = OPENED.plusDays(3);

        assertThat(ShelfLife.resolve(input(sameDay, OPENED), PACKAGED)).contains(user(sameDay));
    }

    private static Input input(LocalDate userDate, LocalDate openedDate) {
        return new Input(null, FoodCategory.DAIRY, StorageLocation.REFRIGERATOR, PURCHASED, openedDate, userDate);
    }

    private static Expiration user(LocalDate date) {
        return new Expiration(date, ExpirationSource.USER);
    }

    private static Expiration estimated(LocalDate date) {
        return new Expiration(date, ExpirationSource.ESTIMATED);
    }
}
