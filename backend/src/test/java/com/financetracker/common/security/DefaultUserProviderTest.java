package com.financetracker.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Currency;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.financetracker.common.config.AppProperties;

class DefaultUserProviderTest {

    @Test
    void returnsTheConfiguredDefaultUserId() {
        // 42, not the real default of 1: proves the id comes from the configuration, not a hardcoded value.
        AppProperties properties = new AppProperties(
                42L,
                Currency.getInstance("EUR"),
                new AppProperties.Cors(List.of("http://localhost:5173")));

        // Typed as the interface, the way services will see it.
        CurrentUserProvider provider = new DefaultUserProvider(properties);

        assertThat(provider.currentUserId()).isEqualTo(42L);
    }
}
