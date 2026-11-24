package com.financetracker.common.validation;

import java.time.LocalDate;

/**
 * Something with an optional start and end date, checked by {@link ValidDateRange}.
 *
 * A record with {@code from} and {@code to} components implements this just by declaring it, because its accessors
 * are already named {@code from()} and {@code to()}.
 */
public interface DateRange {

    LocalDate from();

    LocalDate to();
}
