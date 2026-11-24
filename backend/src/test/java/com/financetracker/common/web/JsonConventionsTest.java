package com.financetracker.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import com.financetracker.common.domain.TransactionType;

import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Locks in the API's JSON contract for money, dates and enums.
 *
 * {@code @JsonTest} starts only Jackson, configured from application.yaml exactly as the real app is (no web
 * server, no database), so these tests check the mapper the controllers will actually use.
 *
 * Some rules are Jackson 3 defaults rather than our config. They are tested anyway, so a dependency upgrade
 * that changes a default breaks a test instead of silently changing the API.
 *
 * That an unknown enum becomes a 400 response is proven end to end in GlobalExceptionHandlerIT; here we only
 * prove the mapper refuses it.
 */
@JsonTest
class JsonConventionsTest {

    @Autowired
    private JsonMapper mapper;

    // --- Amounts: plain numbers, never scientific notation ---

    @Test
    void bigDecimal_withNegativeScale_isWrittenPlain() {
        // new BigDecimal("1E+3").toString() is "1E+3"; this passes only because of write-bigdecimal-as-plain.
        assertThat(mapper.writeValueAsString(new BigDecimal("1E+3"))).isEqualTo("1000");
    }

    @Test
    void bigDecimal_keepsItsCents() {
        assertThat(mapper.writeValueAsString(new BigDecimal("1000.00"))).isEqualTo("1000.00");
    }

    @Test
    void bigDecimal_isReadWithoutLosingPrecision() {
        BigDecimal amount = mapper.readValue("1000.10", BigDecimal.class);

        // isEqualTo on BigDecimal also compares the scale: 1000.10 is not 1000.1.
        assertThat(amount).isEqualTo(new BigDecimal("1000.10"));
    }

    // --- Dates: ISO-8601 strings ---

    @Test
    void localDate_isWrittenAsIsoString() {
        assertThat(mapper.writeValueAsString(LocalDate.of(2026, 11, 21))).isEqualTo("\"2026-11-21\"");
    }

    @Test
    void localDate_isReadFromIsoString() {
        assertThat(mapper.readValue("\"2026-11-21\"", LocalDate.class)).isEqualTo(LocalDate.of(2026, 11, 21));
    }

    // --- Timestamps: ISO-8601 in UTC ---

    @Test
    void instant_isWrittenAsIsoStringInUtc() {
        Instant instant = Instant.parse("2026-11-21T10:15:30Z");

        assertThat(mapper.writeValueAsString(instant)).isEqualTo("\"2026-11-21T10:15:30Z\"");
    }

    @Test
    void instant_isReadFromIsoStringWithOffset() {
        // A client in Lisbon summer time sends +01:00; it is the same moment as 09:15:30Z.
        Instant instant = mapper.readValue("\"2026-07-21T10:15:30+01:00\"", Instant.class);

        assertThat(instant).isEqualTo(Instant.parse("2026-07-21T09:15:30Z"));
    }

    // --- Enums: unknown values are rejected ---

    @Test
    void enum_isWrittenByName() {
        assertThat(mapper.writeValueAsString(TransactionType.EXPENSE)).isEqualTo("\"EXPENSE\"");
    }

    @Test
    void enum_knownValue_isRead() {
        assertThat(mapper.readValue("\"INCOME\"", TransactionType.class)).isEqualTo(TransactionType.INCOME);
    }

    @Test
    void enum_unknownValue_isRejected() {
        // Must fail, not become null: a null type would slip past and fail later with a worse error.
        assertThatThrownBy(() -> mapper.readValue("\"TRANSFER\"", TransactionType.class))
                .isInstanceOf(InvalidFormatException.class);
    }
}
