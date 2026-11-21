package com.financetracker.transaction;

import static com.financetracker.common.domain.TransactionType.EXPENSE;
import static com.financetracker.common.domain.TransactionType.INCOME;
import static com.financetracker.support.TestData.aCategory;
import static com.financetracker.support.TestData.anAppUser;
import static com.financetracker.support.TestData.anExpense;
import static com.financetracker.support.TestData.anIncome;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.financetracker.category.Category;
import com.financetracker.category.CategoryRepository;
import com.financetracker.common.config.JpaAuditingConfig;
import com.financetracker.support.TestcontainersConfiguration;
import com.financetracker.user.AppUser;
import com.financetracker.user.AppUserRepository;

import jakarta.persistence.EntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class TransactionRepositoryIT {

    @Autowired
    private TransactionRepository transactions;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private AppUser user;
    private AppUser otherUser;
    private Category food;      // user's EXPENSE category
    private Category salary;    // user's INCOME category

    @BeforeEach
    void setUp() {
        user = users.saveAndFlush(anAppUser().build());
        otherUser = users.saveAndFlush(anAppUser().build());
        food = categories.saveAndFlush(aCategory().ownedBy(user.getId()).withName("Food").withType(EXPENSE).build());
        salary = categories.saveAndFlush(aCategory().ownedBy(user.getId()).withName("Salary").withType(INCOME).build());
    }

    // --- Mapping: what goes in comes back out exactly ---

    @Test
    void persistsAmountExactly_andSetsAuditTimestamps() {
        Transaction saved = transactions.saveAndFlush(
                anExpense().in(food).amount("12.30").on("2026-11-03").withNotes("Lunch").build());
        // Drop the cached entity so findById really reads the row back from PostgreSQL.
        entityManager.clear();

        Transaction loaded = transactions.findById(saved.getId()).orElseThrow();

        // isEqualTo on BigDecimal also compares scale: 12.30 must come back as 12.30, not 12.3 or 12.2999...
        assertThat(loaded.getAmount()).isEqualTo(new BigDecimal("12.30"));
        assertThat(loaded.getTransactionDate()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(loaded.getType()).isEqualTo(EXPENSE);
        assertThat(loaded.getUserId()).isEqualTo(user.getId());
        assertThat(loaded.getNotes()).isEqualTo("Lunch");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void categoryIsLoadedLazily() {
        Transaction saved = transactions.saveAndFlush(anExpense().in(food).build());
        entityManager.clear();

        Transaction loaded = transactions.findById(saved.getId()).orElseThrow();

        assertThat(Hibernate.isInitialized(loaded.getCategory())).isFalse();
        assertThat(loaded.getCategory().getName()).isEqualTo("Food");   // first access triggers the SELECT
        assertThat(Hibernate.isInitialized(loaded.getCategory())).isTrue();
    }

    // --- Constraints: each bad insert must be rejected by the database, by the expected constraint ---

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-5.00"})
    void rejectsZeroOrNegativeAmount(String amount) {
        Transaction transaction = anExpense().in(food).amount(amount).build();

        assertThatThrownBy(() -> transactions.saveAndFlush(transaction))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("transactions_amount_check");
    }

    @Test
    void rejectsTypeDifferentFromCategoryType() {
        Transaction incomeInExpenseCategory = anIncome().in(food).build();

        assertThatThrownBy(() -> transactions.saveAndFlush(incomeInExpenseCategory))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_transactions_category");
    }

    @Test
    void rejectsCategoryOfAnotherUser() {
        Transaction intoSomeoneElsesCategory = anExpense().in(food).ownedBy(otherUser.getId()).build();

        assertThatThrownBy(() -> transactions.saveAndFlush(intoSomeoneElsesCategory))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_transactions_category");
    }

    @Test
    void rejectsUnknownType() {
        // The Java enum can't hold an invalid type, so bypass JPA to prove the CHECK constraint itself.
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO transactions (user_id, category_id, type, description, amount, transaction_date)
                VALUES (?, ?, 'TRANSFER', 'X', 1.00, DATE '2026-01-01')
                """, user.getId(), food.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("transactions_type_check");
    }

    @ParameterizedTest
    @ValueSource(strings = {"category_id", "description", "transaction_date"})
    void rejectsMissingRequiredColumn(String column) {
        // Plain SQL so we test the NOT NULL in the schema, not Hibernate's own null checks.
        String categoryId = column.equals("category_id") ? "NULL" : food.getId().toString();
        String description = column.equals("description") ? "NULL" : "'X'";
        String date = column.equals("transaction_date") ? "NULL" : "DATE '2026-01-01'";

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO transactions (user_id, category_id, type, description, amount, transaction_date) "
                        + "VALUES (?, " + categoryId + ", 'EXPENSE', " + description + ", 1.00, " + date + ")",
                user.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("null value in column \"" + column + "\"");
    }

    @Test
    void rejectsDeletingCategoryStillInUse() {
        transactions.saveAndFlush(anExpense().in(food).build());
        // Otherwise Hibernate itself refuses (managed transaction -> removed category) before any SQL runs,
        // and we want to prove the *database* rejects it, like in a fresh request.
        entityManager.clear();

        categories.deleteById(food.getId());

        assertThatThrownBy(() -> categories.flush())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_transactions_category");
    }

    @Test
    void deletingUserDeletesTheirTransactions() {
        Transaction saved = transactions.saveAndFlush(anExpense().in(food).build());

        users.delete(user);
        users.flush();
        // The database cascaded the delete; clear Hibernate's cache so the next query really hits the DB.
        entityManager.clear();

        assertThat(transactions.findById(saved.getId())).isEmpty();
    }

    // --- Repository methods ---

    @Test
    void findByIdAndUserId_returnsTransactionOnlyForItsOwner() {
        Transaction saved = transactions.saveAndFlush(anIncome().in(salary).build());

        assertThat(transactions.findByIdAndUserId(saved.getId(), user.getId())).contains(saved);
        assertThat(transactions.findByIdAndUserId(saved.getId(), otherUser.getId())).isEmpty();
    }

    @Test
    void existsAndCountByCategoryId_reflectWhetherCategoryIsInUse() {
        transactions.saveAndFlush(anExpense().in(food).build());
        transactions.saveAndFlush(anExpense().in(food).build());

        assertThat(transactions.existsByCategoryId(food.getId())).isTrue();
        assertThat(transactions.countByCategoryId(food.getId())).isEqualTo(2);

        assertThat(transactions.existsByCategoryId(salary.getId())).isFalse();
        assertThat(transactions.countByCategoryId(salary.getId())).isZero();
    }
}
