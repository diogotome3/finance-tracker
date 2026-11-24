package com.financetracker.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * On a class implementing {@link DateRange}: {@code from} must not be after {@code to}.
 *
 * Either bound may be missing (an open range such as "everything since March"); the same day for both is valid.
 * Being class-level, a violation has no field, so the API reports it with {@code "field": null}.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DateRangeValidator.class)
public @interface ValidDateRange {

    String message() default "from must not be after to";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
