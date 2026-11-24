package com.financetracker.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * The unit tests call {@code new ReasonableDateValidator(clock)} themselves. This one proves Spring's Validator
 * can build it too (injecting the Clock bean) and that both annotations are picked up on a real record.
 * Without Spring's factory, Hibernate Validator would fail: the validator has no no-argument constructor.
 */
class DateConstraintsWiringTest {

    @Configuration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-06-15T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @ValidDateRange
    record Filter(@ReasonableDate LocalDate from, @ReasonableDate LocalDate to) implements DateRange {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(FixedClockConfig.class);

    @Test
    void validFilter_hasNoViolations() {
        runner.run(context -> {
            Validator validator = context.getBean(Validator.class);

            assertThat(validator.validate(new Filter(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))).isEmpty();
        });
    }

    @Test
    void unreasonableDate_isReportedOnItsField() {
        runner.run(context -> {
            Validator validator = context.getBean(Validator.class);

            // 2027-06-16 is one day past the fixed Clock's today + 1 year, so this also proves the bean Clock was used.
            Set<ConstraintViolation<Filter>> violations =
                    validator.validate(new Filter(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 6, 16)));

            assertThat(violations).singleElement().satisfies(v -> {
                assertThat(v.getPropertyPath()).hasToString("to");
                assertThat(v.getMessage()).isEqualTo("must be between 2000-01-01 and one year from today");
            });
        });
    }

    @Test
    void reversedRange_isReportedOnTheClass() {
        runner.run(context -> {
            Validator validator = context.getBean(Validator.class);

            Set<ConstraintViolation<Filter>> violations =
                    validator.validate(new Filter(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)));

            assertThat(violations).singleElement().satisfies(v -> {
                assertThat(v.getPropertyPath()).hasToString("");   // class-level: no field
                assertThat(v.getMessage()).isEqualTo("from must not be after to");
            });
        });
    }
}
