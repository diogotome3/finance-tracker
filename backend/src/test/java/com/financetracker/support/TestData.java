package com.financetracker.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import com.financetracker.category.Category;
import com.financetracker.common.domain.TransactionType;
import com.financetracker.transaction.Transaction;
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

    public static TransactionBuilder anExpense() {
        return new TransactionBuilder(TransactionType.EXPENSE);
    }

    public static TransactionBuilder anIncome() {
        return new TransactionBuilder(TransactionType.INCOME);
    }

    /**
     * Reads like: {@code anExpense().in(food).amount("12.50").on("2026-11-03").build()}.
     * The owner defaults to the category's owner; override it with {@code ownedBy} to test the composite FK.
     */
    public static final class TransactionBuilder {

        // No default category: transactions.category_id is a foreign key, so a test must pass a saved category.
        private Category category;
        private Long userId;
        private TransactionType type;
        private String description = "Transaction " + SEQUENCE.incrementAndGet();
        private BigDecimal amount = new BigDecimal("10.00");
        private LocalDate date = LocalDate.of(2026, 1, 15);
        private String notes;

        private TransactionBuilder(TransactionType type) {
            this.type = type;
        }

        public TransactionBuilder in(Category category) {
            this.category = category;
            return this;
        }

        public TransactionBuilder ownedBy(Long userId) {
            this.userId = userId;
            return this;
        }

        public TransactionBuilder amount(String amount) {
            this.amount = new BigDecimal(amount);
            return this;
        }

        public TransactionBuilder on(String isoDate) {
            this.date = LocalDate.parse(isoDate);
            return this;
        }

        public TransactionBuilder describedAs(String description) {
            this.description = description;
            return this;
        }

        public TransactionBuilder withNotes(String notes) {
            this.notes = notes;
            return this;
        }

        public Transaction build() {
            Long owner = userId != null ? userId : category.getUserId();
            Transaction transaction = new Transaction(owner, category, type, description, amount, date);
            transaction.setNotes(notes);
            return transaction;
        }
    }
}
