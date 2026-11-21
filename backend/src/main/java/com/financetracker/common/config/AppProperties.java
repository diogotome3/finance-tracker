package com.financetracker.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotEmpty;

import java.util.Currency;
import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Validated          // without this, the constraints below are ignored
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    @NotNull @Positive Long defaultUserId,
    @NotNull Currency currency,
    @Valid @NotNull Cors cors) {
        public record Cors(@NotEmpty List<String> allowedOrigins) {}
}


