package com.financetracker.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Currency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * Binds AppProperties in a tiny Spring context (no web server, no database), so each case is fast.
 * Proves the app refuses to start when an app.* setting is missing or invalid.
 */
class AppPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(AppProperties.class)
    static class Config {
    }

    private static final String VALID_USER_ID = "app.default-user-id=1";
    private static final String VALID_CURRENCY = "app.currency=EUR";
    private static final String VALID_ORIGINS = "app.cors.allowed-origins=http://localhost:5173";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            // Needed for @Validated to have a validator to run.
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    @Test
    void bindsValidProperties() {
        runner.withPropertyValues(
                        VALID_USER_ID,
                        VALID_CURRENCY,
                        "app.cors.allowed-origins=http://localhost:5173,http://localhost:3000")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    AppProperties properties = context.getBean(AppProperties.class);
                    assertThat(properties.defaultUserId()).isEqualTo(1L);
                    assertThat(properties.currency()).isEqualTo(Currency.getInstance("EUR"));
                    // A comma-separated value becomes a list.
                    assertThat(properties.cors().allowedOrigins())
                            .containsExactly("http://localhost:5173", "http://localhost:3000");
                });
    }

    @Test
    void failsWhenDefaultUserIdIsMissing() {
        runner.withPropertyValues(VALID_CURRENCY, VALID_ORIGINS)
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void failsWhenDefaultUserIdIsNotPositive(String userId) {
        runner.withPropertyValues("app.default-user-id=" + userId, VALID_CURRENCY, VALID_ORIGINS)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenCurrencyIsMissing() {
        runner.withPropertyValues(VALID_USER_ID, VALID_ORIGINS)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenCurrencyIsNotAnIsoCode() {
        // Fails while converting "XYZ" to java.util.Currency, before validation even runs.
        runner.withPropertyValues(VALID_USER_ID, "app.currency=XYZ", VALID_ORIGINS)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenAllowedOriginsAreMissing() {
        runner.withPropertyValues(VALID_USER_ID, VALID_CURRENCY)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenAllowedOriginsAreEmpty() {
        // Only caught because of @Valid on the nested Cors record.
        runner.withPropertyValues(VALID_USER_ID, VALID_CURRENCY, "app.cors.allowed-origins=")
                .run(context -> assertThat(context).hasFailed());
    }
}
