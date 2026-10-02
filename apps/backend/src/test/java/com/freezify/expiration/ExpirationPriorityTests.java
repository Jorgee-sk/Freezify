package com.freezify.expiration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ExpirationPriorityTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);

    @ParameterizedTest
    @CsvSource({
        "-30, EXPIRED",
        "-1, EXPIRED",
        "0, TODAY",
        "1, URGENT",
        "2, URGENT",
        "3, SOON",
        "5, SOON",
        "6, UPCOMING",
        "10, UPCOMING",
        "11, OK",
        "365, OK",
    })
    void levelDependsOnTheDaysLeft(int daysLeft, ExpirationPriority expected) {
        assertThat(ExpirationPriority.of(TODAY.plusDays(daysLeft), TODAY)).isEqualTo(expected);
        assertThat(ExpirationPriority.ofDaysLeft(daysLeft)).isEqualTo(expected);
    }

    @Test
    void countsCalendarDaysAcrossMonthsAndYears() {
        assertThat(ExpirationPriority.daysLeft(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 31)))
                .isEqualTo(1);
        assertThat(ExpirationPriority.daysLeft(LocalDate.of(2027, 1, 1), LocalDate.of(2026, 12, 31)))
                .isEqualTo(1);
        assertThat(ExpirationPriority.daysLeft(LocalDate.of(2026, 9, 30), TODAY)).isEqualTo(-2);
    }

    @Test
    void levelsAreOrderedFromMostToLeastPressing() {
        assertThat(ExpirationPriority.values())
                .containsExactly(
                        ExpirationPriority.EXPIRED,
                        ExpirationPriority.TODAY,
                        ExpirationPriority.URGENT,
                        ExpirationPriority.SOON,
                        ExpirationPriority.UPCOMING,
                        ExpirationPriority.OK);
    }

    @Test
    void attentionCoversEverythingUpToFiveDays() {
        assertThat(ExpirationPriority.EXPIRED.needsAttention()).isTrue();
        assertThat(ExpirationPriority.TODAY.needsAttention()).isTrue();
        assertThat(ExpirationPriority.URGENT.needsAttention()).isTrue();
        assertThat(ExpirationPriority.SOON.needsAttention()).isTrue();
        assertThat(ExpirationPriority.UPCOMING.needsAttention()).isFalse();
        assertThat(ExpirationPriority.OK.needsAttention()).isFalse();

        // The horizon and the levels must agree, or the "eat first" list would miss or include a day.
        LocalDate horizon = ExpirationPriority.attentionHorizon(TODAY);
        assertThat(ExpirationPriority.of(horizon, TODAY).needsAttention()).isTrue();
        assertThat(ExpirationPriority.of(horizon.plusDays(1), TODAY).needsAttention()).isFalse();
    }
}
