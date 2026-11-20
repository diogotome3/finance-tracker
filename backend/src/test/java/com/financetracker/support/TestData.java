package com.financetracker.support;

import java.util.concurrent.atomic.AtomicInteger;

import com.financetracker.category.Category;
import com.financetracker.common.domain.TransactionType;
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

    public static CategoryBuilder aCategory() {
        return new CategoryBuilder();
    }

    public static final class CategoryBuilder {

        // No default owner: categories.user_id is a foreign key, so a test must pass a saved user's id.
        private Long userId;
        private String name = "Category " + SEQUENCE.incrementAndGet();
        private TransactionType type = TransactionType.EXPENSE;
        private String description;
        private String color = "#1A2B3C";

        private CategoryBuilder() {
        }

        public CategoryBuilder ownedBy(Long userId) {
            this.userId = userId;
            return this;
        }

        public CategoryBuilder withName(String name) {
            this.name = name;
            return this;
        }

        public CategoryBuilder withType(TransactionType type) {
            this.type = type;
            return this;
        }

        public CategoryBuilder withDescription(String description) {
            this.description = description;
            return this;
        }

        public CategoryBuilder withColor(String color) {
            this.color = color;
            return this;
        }

        public Category build() {
            Category category = new Category(userId, name, type);
            category.setDescription(description);
            category.setColor(color);
            return category;
        }
    }
}
