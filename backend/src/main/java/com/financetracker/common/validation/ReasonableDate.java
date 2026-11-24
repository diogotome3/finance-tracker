package com.financetracker.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * The date must be between 2000-01-01 and one year from today (both inclusive). Catches typos like 2062 for 2026.
 *
 * {@code null} is valid; add {@code @NotNull} when the date is required.
 *
 * Use it on DTOs and controller parameters, not on JPA entities: Hibernate ORM validates entities with its own
 * validator factory, which can't inject the Clock.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})   // FIELD: DTO/record fields, PARAMETER: @RequestParam
@Retention(RetentionPolicy.RUNTIME)                   // must still exist at runtime for the validator to see it
@Constraint(validatedBy = ReasonableDateValidator.class)
public @interface ReasonableDate {

    String message() default "must be between 2000-01-01 and one year from today";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
