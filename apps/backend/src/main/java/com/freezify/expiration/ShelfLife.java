package com.freezify.expiration;

import com.freezify.expiration.ExpirationEstimator.Expiration;
import com.freezify.expiration.ExpirationEstimator.Input;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * How long a food keeps in one storage place.
 *
 * @param unopenedDays days from purchase; {@code null} when it cannot be told without the date on the package
 *                     (packaged milk, cured ham)
 * @param openedDays   days once opened; {@code null} when opening does not change anything (a whole courgette)
 */
public record ShelfLife(@Nullable Integer unopenedDays, @Nullable Integer openedDays) {

    /**
     * The date by which the food should be eaten, and whether it is the user's or an estimate.
     *
     * <p>A date given by the user is kept unless opening the food makes it expire sooner. Without a user
     * date, the estimate from purchase is used, again shortened by opening when that applies. Whatever is
     * earliest wins, and on a tie the user's date does.
     *
     * @param rule the rule for this food and storage place; {@code null} when there is none
     */
    public static Optional<Expiration> resolve(Input input, @Nullable ShelfLife rule) {
        Expiration best = null;
        if (input.userDate() != null) {
            best = new Expiration(input.userDate(), ExpirationSource.USER);
        } else if (rule != null && rule.unopenedDays() != null) {
            best = new Expiration(input.purchaseDate().plusDays(rule.unopenedDays()), ExpirationSource.ESTIMATED);
        }
        if (rule != null && rule.openedDays() != null && input.openedDate() != null) {
            Expiration onceOpened =
                    new Expiration(input.openedDate().plusDays(rule.openedDays()), ExpirationSource.ESTIMATED);
            if (best == null || onceOpened.date().isBefore(best.date())) {
                best = onceOpened;
            }
        }
        return Optional.ofNullable(best);
    }
}
