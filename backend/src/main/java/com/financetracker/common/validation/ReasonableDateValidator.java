package com.financetracker.common.validation;

import java.time.Clock;
import java.time.LocalDate;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Checks {@link ReasonableDate}: from 2000-01-01 to one year after today, both inclusive.
 *
 * Spring creates validators as beans, so the Clock bean is injected here. Tests pass a fixed Clock instead, so
 * "today" doesn't depend on the day the tests run.
 */
public class ReasonableDateValidator implements ConstraintValidator<ReasonableDate, LocalDate> {

    static final LocalDate MIN = LocalDate.of(2000, 1, 1);

    private final Clock clock;

    public ReasonableDateValidator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // null is @NotNull's job, so a missing date is valid here
        }
        // Read "today" on every call: the validator lives as long as the app, the date doesn't.
        LocalDate max = LocalDate.now(clock).plusYears(1);
        return !value.isBefore(MIN) && !value.isAfter(max);
    }
}
