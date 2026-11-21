package com.financetracker.common.security;

import org.springframework.stereotype.Component;

import com.financetracker.common.config.AppProperties;


/**
 * MVP implementation: every request acts as the configured default user (app.default-user-id).
 * Replace with an implementation backed by Spring Security when authentication is added.
 */
@Component
public class DefaultUserProvider implements CurrentUserProvider {

    private final long defaultUserId;

    public DefaultUserProvider(AppProperties appProperties) {
        this.defaultUserId = appProperties.defaultUserId();
    }

    @Override
    public long currentUserId() {
        return defaultUserId;
    }
}
