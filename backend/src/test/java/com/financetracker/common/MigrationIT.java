package com.financetracker.common;

import static com.financetracker.common.domain.TransactionType.EXPENSE;
import static com.financetracker.common.domain.TransactionType.INCOME;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import com.financetracker.category.Category;
import com.financetracker.category.CategoryRepository;
import com.financetracker.support.TestcontainersConfiguration;
import com.financetracker.user.AppUser;
import com.financetracker.user.AppUserRepository;

/**
 * Proves a fresh database is fully migrated and seeded.
 * The seed rows are visible even though @DataJpaTest rolls back: Flyway committed them
 * when the container started, before any test ran.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class MigrationIT {

    // Matches app.default-user-id, which later phases use as the current user.
    private static final long DEFAULT_USER_ID = 1L;

    @Autowired
    private Flyway flyway;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private CategoryRepository categories;

    @Test
    void flywayIsAtVersion5_andEveryMigrationSucceeded() {
        MigrationInfoService info = flyway.info();

        assertThat(info.current().getVersion().getVersion()).isEqualTo("5");
        assertThat(info.applied())
                .hasSize(5)
                .extracting(MigrationInfo::getState)
                .containsOnly(MigrationState.SUCCESS);
    }

    @Test
    void defaultUserExistsWithId1() {
        AppUser defaultUser = users.findById(DEFAULT_USER_ID).orElseThrow();

        // Check the email too, so we know it's the seeded user and not just any row with id 1.
        assertThat(defaultUser.getEmail()).isEqualTo("default@financetracker.local");
        assertThat(defaultUser.getDisplayName()).isEqualTo("Default User");
    }

    @Test
    void defaultUserOwns12ExpenseAnd5IncomeCategories() {
        List<Category> seeded = categories.findAllByUserIdOrderByTypeAscNameAsc(DEFAULT_USER_ID);

        assertThat(seeded).hasSize(17);
        // Exact names, not just counts: a typo like "Transportaion" must fail the test.
        assertThat(seeded)
                .filteredOn(category -> category.getType() == EXPENSE)
                .extracting(Category::getName)
                .containsExactlyInAnyOrder(
                        "Food & Groceries", "Restaurants", "Transportation", "Housing", "Utilities", "Shopping",
                        "Entertainment", "Health", "Sports", "Subscriptions", "Travel", "Other");
        assertThat(seeded)
                .filteredOn(category -> category.getType() == INCOME)
                .extracting(Category::getName)
                .containsExactlyInAnyOrder("Salary", "Freelance", "Investments", "Gifts", "Other");
    }

    @Test
    void everySeededCategoryHasAColor() {
        // The CHECK constraint already guarantees the #RRGGBB format; this proves none was left NULL.
        assertThat(categories.findAllByUserIdOrderByTypeAscNameAsc(DEFAULT_USER_ID))
                .extracting(Category::getColor)
                .doesNotContainNull();
    }
}
