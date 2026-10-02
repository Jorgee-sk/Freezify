package com.freezify.expiration;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * How soon a food should be eaten, from most to least pressing. This is not only a label: the order is what
 * recipe recommendations and the meal planner use to decide what to use first.
 */
public enum ExpirationPriority {
    /** The date has passed. */
    EXPIRED,
    /** Expires today. */
    TODAY,
    /** 1 or 2 days left. */
    URGENT,
    /** 3 to 5 days left. */
    SOON,
    /** 6 to 10 days left. */
    UPCOMING,
    /** More than 10 days left. */
    OK;

    public static ExpirationPriority of(LocalDate expirationDate, LocalDate today) {
        return ofDaysLeft(daysLeft(expirationDate, today));
    }

    public static ExpirationPriority ofDaysLeft(long daysLeft) {
        if (daysLeft < 0) {
            return EXPIRED;
        }
        if (daysLeft == 0) {
            return TODAY;
        }
        if (daysLeft <= 2) {
            return URGENT;
        }
        if (daysLeft <= 5) {
            return SOON;
        }
        if (daysLeft <= 10) {
            return UPCOMING;
        }
        return OK;
    }

    /** Negative when the date has passed. */
    public static long daysLeft(LocalDate expirationDate, LocalDate today) {
        return ChronoUnit.DAYS.between(today, expirationDate);
    }

    /** Whether the food belongs in "eat this first": expired, or 5 days or fewer left. */
    public boolean needsAttention() {
        return compareTo(SOON) <= 0;
    }

    /** The last day that still {@linkplain #needsAttention() needs attention}, seen from {@code today}. */
    public static LocalDate attentionHorizon(LocalDate today) {
        return today.plusDays(5);
    }
}
