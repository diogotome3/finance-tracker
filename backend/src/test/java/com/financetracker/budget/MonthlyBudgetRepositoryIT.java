package com.financetracker.budget;

import static com.financetracker.support.TestData.aMonthlyBudget;
import static com.financetracker.support.TestData.anAppUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

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

import com.financetracker.common.config.JpaAuditingConfig;
import com.financetracker.support.TestcontainersConfiguration;
import com.financetracker.user.AppUser;
import com.financetracker.user.AppUserRepository;

import jakarta.persistence.EntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class MonthlyBudgetRepositoryIT {

    @Autowired
    private MonthlyBudgetRepository budgets;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private AppUser user;
    private AppUser otherUser;

    @BeforeEach
    void setUp() {
        user = users.saveAndFlush(anAppUser().build());
        otherUser = users.saveAndFlush(anAppUser().build());
    }

    // --- Mapping: what goes in comes back out exactly ---

    @Test
    void persistsBudgetExactly_andSetsAuditTimestamps() {
        MonthlyBudget saved = budgets.saveAndFlush(
                aMonthlyBudget().ownedBy(user.getId()).forYear(2026).forMonth(11).withAmount("1500.50").build());
        // Drop the cached entity so findById really reads the row back from PostgreSQL.
        entityManager.clear();

        MonthlyBudget loaded = budgets.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getUserId()).isEqualTo(user.getId());
        assertThat(loaded.getYear()).isEqualTo(2026);
        assertThat(loaded.getMonth()).isEqualTo(11);
        // isEqualTo on BigDecimal also compares scale: 1500.50 must come back as 1500.50.
        assertThat(loaded.getAmount()).isEqualTo(new BigDecimal("1500.50"));
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }

    // --- Constraints: each bad insert must be rejected by the database, by the expected constraint ---

    @Test
    void rejectsSecondBudgetForSameUserAndMonth() {
        budgets.saveAndFlush(aMonthlyBudget().ownedBy(user.getId()).forYear(2026).forMonth(11).build());
        MonthlyBudget duplicate = aMonthlyBudget().ownedBy(user.getId()).forYear(2026).forMonth(11).build();

        assertThatThrownBy(() -> budgets.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_monthly_budgets_user_period");
    }

    @Test
    void allowsSameMonthInAnotherYear_andSameMonthForAnotherUser() {
        budgets.saveAndFlush(aMonthlyBudget().ownedBy(user.getId()).forYear(2026).forMonth(11).build());

        MonthlyBudget nextYear = budgets.saveAndFlush(
                aMonthlyBudget().ownedBy(user.getId()).forYear(2027).forMonth(11).build());
        MonthlyBudget otherUsers = budgets.saveAndFlush(
                aMonthlyBudget().ownedBy(otherUser.getId()).forYear(2026).forMonth(11).build());

        assertThat(nextYear.getId()).isNotNull();
        assertThat(otherUsers.getId()).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 13})
    void rejectsMonthOutsideOneToTwelve(int month) {
        MonthlyBudget budget = aMonthlyBudget().ownedBy(user.getId()).forMonth(month).build();

        assertThatThrownBy(() -> budgets.saveAndFlush(budget))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("monthly_budgets_budget_month_check");
    }

    @ParameterizedTest
    @ValueSource(ints = {1999, 2101})
    void rejectsYearOutside2000To2100(int year) {
        MonthlyBudget budget = aMonthlyBudget().ownedBy(user.getId()).forYear(year).build();

        assertThatThrownBy(() -> budgets.saveAndFlush(budget))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("monthly_budgets_budget_year_check");
    }

    @ParameterizedTest
    @ValueSource(ints = {2000, 2100})
    void allowsBoundaryYears(int year) {
        // BETWEEN is inclusive: guard against someone "fixing" it to > / <.
        MonthlyBudget saved = budgets.saveAndFlush(aMonthlyBudget().ownedBy(user.getId()).forYear(year).build());

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void rejectsNegativeAmount() {
        MonthlyBudget budget = aMonthlyBudget().ownedBy(user.getId()).withAmount("-0.01").build();

        assertThatThrownBy(() -> budgets.saveAndFlush(budget))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("monthly_budgets_amount_check");
    }

    @Test
    void allowsZeroAmount() {
        // 0 is a real budget: "no spending planned this month".
        MonthlyBudget saved = budgets.saveAndFlush(aMonthlyBudget().ownedBy(user.getId()).withAmount("0").build());

        assertThat(saved.getId()).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"budget_year", "budget_month", "amount"})
    void rejectsMissingRequiredColumn(String column) {
        // The entity uses primitives, which can't be null, so bypass JPA to prove the NOT NULL itself.
        String year = column.equals("budget_year") ? "NULL" : "2026";
        String month = column.equals("budget_month") ? "NULL" : "1";
        String amount = column.equals("amount") ? "NULL" : "100.00";

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO monthly_budgets (user_id, budget_year, budget_month, amount) "
                        + "VALUES (?, " + year + ", " + month + ", " + amount + ")",
                user.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("null value in column \"" + column + "\"");
    }

    @Test
    void rejectsUnknownUser() {
        MonthlyBudget budget = aMonthlyBudget().ownedBy(999_999L).build();

        assertThatThrownBy(() -> budgets.saveAndFlush(budget))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("monthly_budgets_user_id_fkey");
    }

    @Test
    void deletingUserDeletesTheirBudgets() {
        MonthlyBudget saved = budgets.saveAndFlush(aMonthlyBudget().ownedBy(user.getId()).build());

        users.delete(user);
        users.flush();
        // The database cascaded the delete; clear Hibernate's cache so the next query really hits the DB.
        entityManager.clear();

        assertThat(budgets.findById(saved.getId())).isEmpty();
    }

    // --- Repository methods ---

    @Test
    void findByUserIdAndYearAndMonth_returnsOnlyThatUsersBudgetForThatMonth() {
        MonthlyBudget november = budgets.saveAndFlush(
                aMonthlyBudget().ownedBy(user.getId()).forYear(2026).forMonth(11).build());
        // Decoys: each differs from the target in exactly one of user / year / month.
        budgets.saveAndFlush(aMonthlyBudget().ownedBy(user.getId()).forYear(2026).forMonth(10).build());
        budgets.saveAndFlush(aMonthlyBudget().ownedBy(user.getId()).forYear(2025).forMonth(11).build());
        budgets.saveAndFlush(aMonthlyBudget().ownedBy(otherUser.getId()).forYear(2026).forMonth(11).build());

        assertThat(budgets.findByUserIdAndYearAndMonth(user.getId(), 2026, 11)).contains(november);
        // No budget set for that month -> empty, not null.
        assertThat(budgets.findByUserIdAndYearAndMonth(user.getId(), 2026, 12)).isEmpty();
    }
}
