package com.financetracker.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Plain unit test of the from/to rule. Empty CSV cells become null, i.e. a missing bound. */
class DateRangeValidatorTest {

    /** The smallest possible DateRange: a record's from()/to() accessors implement the interface. */
    record Range(LocalDate from, LocalDate to) implements DateRange {
    }

    private final DateRangeValidator validator = new DateRangeValidator();

    @ParameterizedTest(name = "from={0}, to={1} -> valid: {2}")
    @CsvSource({
            "2026-03-01, 2026-03-01, true",    // same day
            "2026-03-01, 2026-03-02, true",    // from before to
            "2026-03-02, 2026-03-01, false",   // from after to
            "          , 2026-03-01, true",    // no from
            "2026-03-01,           , true",    // no to
            "          ,           , true",    // no bounds
    })
    void rule(LocalDate from, LocalDate to, boolean expected) {
        assertThat(validator.isValid(new Range(from, to), null)).isEqualTo(expected);
    }

    @Test
    void nullObject_isValid() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
