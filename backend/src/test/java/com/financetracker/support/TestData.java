package com.financetracker.support;

import java.util.concurrent.atomic.AtomicInteger;

import com.financetracker.user.AppUser;

/**
 * Test data builders: create valid entities in one line and override only what a test cares about.
 */
public final class TestData {

    // Shared across all builders so every default email is unique, even within one test.
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private TestData() {
        // static helpers only
    }

    public static AppUserBuilder anAppUser() {
        return new AppUserBuilder();
    }

    public static final class AppUserBuilder {

        private String email = "user" + SEQUENCE.incrementAndGet() + "@test.com";
        private String displayName = "Test User";

        private AppUserBuilder() {
        }

        public AppUserBuilder withEmail(String email) {
            this.email = email;
            return this;
        }

        public AppUserBuilder withDisplayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public AppUser build() {
            return new AppUser(email, displayName);
        }
    }
}
