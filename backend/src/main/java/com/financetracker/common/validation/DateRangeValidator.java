package com.financetracker.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Checks {@link ValidDateRange}. */
public class DateRangeValidator implements ConstraintValidator<ValidDateRange, DateRange> {

    @Override
    public boolean isValid(DateRange range, ConstraintValidatorContext context) {
        if (range == null || range.from() == null || range.to() == null) {
            return true; // an open range has nothing to compare
        }
        return !range.from().isAfter(range.to());
    }
}
