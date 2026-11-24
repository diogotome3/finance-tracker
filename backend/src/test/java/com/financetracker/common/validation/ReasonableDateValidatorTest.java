package com.financetracker.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;

/**
 * Plain unit test: the validator is created with {@code new} and a fixed Clock, so "today" is always 2026-06-15
 * and the upper bound is always 2027-06-15. No Spring needed.
 */
class ReasonableDateValidatorTest {

    private static final Clock TODAY_2026_06_15 = Clock.fixed(Instant.parse("2026-06-15T12:00:00Z"), ZoneOffset.UTC);

    private final ReasonableDateValidator validator = new ReasonableDateValidator(TODAY_2026_06_15);

    @ParameterizedTest(name = "{0} -> valid: {1}")
    @CsvSource({
            "1999-12-31, false",   // day before the minimum
            "2000-01-01, true",    // minimum, inclusive
            "2026-06-15, true",    // today
            "2027-06-15, true",    // today + 1 year, inclusive
            "2027-06-16, false",   // day after the maximum
    })
    void boundaries(LocalDate date, boolean expected) {
        // The context is only needed for custom messages; this validator never touches it.
        assertThat(validator.isValid(date, null)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    void missingDate_isValid(LocalDate date) {
        assertThat(validator.isValid(date, null)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> valid: {1}")
    @CsvSource({
            "2029-02-28, true",    // plusYears(1) from a leap day lands on Feb 28
            "2029-03-01, false",
    })
    void onALeapDay_oneYearLaterIsFebruary28(LocalDate date, boolean expected) {
        Clock leapDay = Clock.fixed(Instant.parse("2028-02-29T12:00:00Z"), ZoneOffset.UTC);

        assertThat(new ReasonableDateValidator(leapDay).isValid(date, null)).isEqualTo(expected);
    }
}
