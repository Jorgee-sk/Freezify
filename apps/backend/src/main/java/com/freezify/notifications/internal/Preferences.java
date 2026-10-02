package com.freezify.notifications.internal;

import com.freezify.expiration.ExpirationPriority;
import com.freezify.food.FoodCategory;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * How a user wants to be told about food that is running out of time.
 *
 * @param expirationAlerts whether to tell them at all
 * @param deliveryHour     hour of the day (0–23, in the application's time zone) from which they are told
 * @param mutedCategories  food they do not want to hear about
 */
public record Preferences(
        boolean expirationAlerts,
        int deliveryHour,
        Frequency frequency,
        Threshold threshold,
        Set<FoodCategory> mutedCategories) {

    /** How often, at most, a user hears about the same household. */
    public enum Frequency {
        DAILY(1),
        EVERY_THREE_DAYS(3),
        WEEKLY(7);

        private final int days;

        Frequency(int days) {
            this.days = days;
        }

        int days() {
            return days;
        }
    }

    /** How close to its date a food has to be before it is worth a notification. Expired food always is. */
    public enum Threshold {
        /** Expires today. */
        TODAY(ExpirationPriority.TODAY, 0),
        /** 2 days or fewer left. */
        URGENT(ExpirationPriority.URGENT, 2),
        /** 5 days or fewer left. */
        SOON(ExpirationPriority.SOON, 5);

        private final ExpirationPriority priority;
        private final int daysAhead;

        Threshold(ExpirationPriority priority, int daysAhead) {
            this.priority = priority;
            this.daysAhead = daysAhead;
        }

        boolean includes(LocalDate expirationDate, LocalDate today) {
            return ExpirationPriority.of(expirationDate, today).compareTo(priority) <= 0;
        }

        /** The last day any threshold cares about, seen from {@code today}. */
        static LocalDate widestHorizon(LocalDate today) {
            return today.plusDays(SOON.daysAhead);
        }
    }

    public Preferences {
        // Sorted, so that the same preferences always read the same.
        mutedCategories = Collections.unmodifiableSet(new TreeSet<>(mutedCategories));
    }

    /** Few and relevant: once a day in the morning, about what expires within two days. */
    static Preferences defaults() {
        return new Preferences(true, 9, Frequency.DAILY, Threshold.URGENT, Set.of());
    }
}
