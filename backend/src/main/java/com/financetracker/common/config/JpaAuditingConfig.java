package com.financetracker.common.config;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Fills {@code @CreatedDate} / {@code @LastModifiedDate} on every entity.
 *
 * Timestamps are truncated to microseconds, the precision of PostgreSQL's TIMESTAMPTZ. Otherwise the entity in
 * memory keeps Java's nanoseconds while the database rounds them away, and a response built right after a save
 * would show a different timestamp than every later read of the same row.
 *
 * Imports {@link ClockConfig} because the provider needs the Clock: slice tests ({@code @DataJpaTest}) that import
 * only this class still get it.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
@Import(ClockConfig.class)
public class JpaAuditingConfig {

    @Bean
    DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(Instant.now(clock).truncatedTo(ChronoUnit.MICROS));
    }
}
