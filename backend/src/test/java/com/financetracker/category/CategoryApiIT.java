package com.financetracker.category;

import static com.financetracker.common.domain.TransactionType.EXPENSE;
import static com.financetracker.common.domain.TransactionType.INCOME;
import static com.financetracker.support.TestData.aCategory;
import static com.financetracker.support.TestData.anAppUser;
import static com.financetracker.support.TestData.anExpense;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.financetracker.common.config.AppProperties;
import com.financetracker.common.domain.TransactionType;
import com.financetracker.support.TestcontainersConfiguration;
import com.financetracker.transaction.TransactionRepository;
import com.financetracker.user.AppUser;
import com.financetracker.user.AppUserRepository;

/**
 * The Categories API end to end: real HTTP to a running server, through security, controller and service, into
 * PostgreSQL with the real Flyway schema, constraints and V5 seed data.
 *
 * No rollback: each request runs and commits on a server thread, so {@code @Transactional} on the test would
 * not undo it. Instead, {@link #restoreSeedState()} deletes everything the tests added after each one, leaving
 * only what Flyway inserted. Tests never modify seeded rows (they create their own), so this is enough.
 *
 * Every request acts as the default user (DefaultUserProvider). Data the API can't create (another user's
 * category, transactions) is inserted through the repositories.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class CategoryApiIT {

    private static final String BASE = "/api/categories";

    /** ISO-8601 in UTC: the trailing Z is what proves the zone. */
    private static final String ISO_UTC = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?Z";

    /** V5__seed_default_user_and_categories.sql, sorted by type (EXPENSE < INCOME) then name. */
    private static final List<String> SEEDED_EXPENSES = List.of(
            "Entertainment", "Food & Groceries", "Health", "Housing", "Other", "Restaurants",
            "Shopping", "Sports", "Subscriptions", "Transportation", "Travel", "Utilities");
    private static final List<String> SEEDED_INCOMES = List.of(
            "Freelance", "Gifts", "Investments", "Other", "Salary");

    /** The highest seeded category id, read before the first test changes anything. */
    private static Long lastSeededCategoryId;

    @Autowired
    private RestTestClient client;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AppProperties appProperties;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private TransactionRepository transactions;

    @BeforeEach
    void rememberSeedState() {
        if (lastSeededCategoryId == null) {
            lastSeededCategoryId = jdbc.queryForObject("SELECT max(id) FROM categories", Long.class);
        }
    }

    @AfterEach
    void restoreSeedState() {
        // Foreign keys: transactions → categories → users.
        jdbc.update("DELETE FROM transactions");
        jdbc.update("DELETE FROM categories WHERE user_id <> ? OR id > ?", defaultUserId(), lastSeededCategoryId);
        jdbc.update("DELETE FROM app_users WHERE id <> ?", defaultUserId());
    }

    @Nested
    class RoundTrip {

        @Test
        void createGetUpdateDelete() {
            // Create: 201, and Location points at the new category.
            EntityExchangeResult<CategoryResponse> created = client.post().uri(BASE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"name": "Coffee", "type": "EXPENSE", "description": "Beans", "color": "#6F4E37"}
                            """)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(CategoryResponse.class).returnResult();
            CategoryResponse body = created.getResponseBody();
            URI location = created.getResponseHeaders().getLocation();
            assertThat(body.id()).isNotNull();
            assertThat(location).isNotNull();
            assertThat(location.getPath()).isEqualTo(BASE + "/" + body.id());

            // Get, by following Location.
            client.get().uri(location)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo(body.id())
                    .jsonPath("$.name").isEqualTo("Coffee")
                    .jsonPath("$.type").isEqualTo("EXPENSE")
                    .jsonPath("$.description").isEqualTo("Beans")
                    .jsonPath("$.color").isEqualTo("#6F4E37");

            // Update: every field changes, including the type (no transactions use it yet).
            CategoryResponse updated = client.put().uri(location)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"name": "Coffee Shops", "type": "INCOME", "color": "#000000"}
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(CategoryResponse.class).returnResult().getResponseBody();
            assertThat(updated.name()).isEqualTo("Coffee Shops");
            assertThat(updated.type()).isEqualTo(INCOME);
            assertThat(updated.description()).isNull();

            // The change was committed, not only returned.
            CategoryResponse reloaded = getCategory(body.id());
            assertThat(reloaded.name()).isEqualTo("Coffee Shops");
            assertThat(reloaded.type()).isEqualTo(INCOME);
            assertThat(reloaded.description()).isNull();
            assertThat(reloaded.color()).isEqualTo("#000000");

            // Delete: 204, then it's gone.
            client.delete().uri(location).exchange().expectStatus().isNoContent().expectBody().isEmpty();
            client.get().uri(location).exchange().expectStatus().isNotFound();
            assertThat(categories.existsById(body.id())).isFalse();
        }

        @Test
        void changingOnlyTheCaseOfItsOwnName_isAllowed() {
            CategoryResponse coffee = create("Coffee", EXPENSE);

            client.put().uri(BASE + "/{id}", coffee.id())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"name": "coffee", "type": "EXPENSE"}
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody().jsonPath("$.name").isEqualTo("coffee");
        }
    }

    @Nested
    class Listing {

        @Test
        void withoutType_listsTheSeededCategories_sortedByTypeThenName() {
            List<String> expected = new ArrayList<>(SEEDED_EXPENSES);
            expected.addAll(SEEDED_INCOMES);

            client.get().uri(BASE)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$[*].name").isEqualTo(expected)
                    .jsonPath("$[0].type").isEqualTo("EXPENSE")
                    .jsonPath("$[%d].type".formatted(SEEDED_EXPENSES.size())).isEqualTo("INCOME");
        }

        @Test
        void withType_listsOnlyThatType() {
            client.get().uri(BASE + "?type=INCOME")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$[*].name").isEqualTo(SEEDED_INCOMES)
                    .jsonPath("$[?(@.type != 'INCOME')]").isEmpty();
        }

        @Test
        void neverIncludesAnotherUsersCategories() {
            AppUser other = users.save(anAppUser().build());
            categories.save(aCategory().ownedBy(other.getId()).withName("Not Yours").withType(INCOME).build());

            client.get().uri(BASE + "?type=INCOME")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody().jsonPath("$[*].name").isEqualTo(SEEDED_INCOMES);
        }

        @Test
        void withUnknownType_is400() {
            client.get().uri(BASE + "?type=FOO")
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .expectBody().jsonPath("$.errors[0].field").isEqualTo("type");
        }
    }

    @Nested
    class Conflicts {

        @Test
        void creatingANameThatDiffersOnlyInCase_is409() {
            // "Salary" is seeded as INCOME.
            client.post().uri(BASE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"name": "salary", "type": "INCOME"}
                            """)
                    .exchange()
                    .expectStatus().isEqualTo(409)
                    .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .expectBody().jsonPath("$.detail").isEqualTo("An income category named 'salary' already exists");

            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM categories WHERE user_id = ? AND type = 'INCOME' AND lower(name) = 'salary'",
                    Long.class, defaultUserId())).isEqualTo(1);
        }

        @Test
        void renamingToAnotherCategorysName_is409() {
            CategoryResponse coffee = create("Coffee", EXPENSE);

            client.put().uri(BASE + "/{id}", coffee.id())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"name": "TRAVEL", "type": "EXPENSE"}
                            """)
                    .exchange()
                    .expectStatus().isEqualTo(409)
                    .expectBody().jsonPath("$.detail").isEqualTo("An expense category named 'TRAVEL' already exists");

            assertThat(getCategory(coffee.id()).name()).isEqualTo("Coffee");
        }

        @Test
        void sameNameWithTheOtherType_isAllowed() {
            // "Salary" exists as INCOME only.
            assertThat(create("Salary", EXPENSE).type()).isEqualTo(EXPENSE);
        }

        @Test
        void deletingACategoryUsedByATransaction_is409WithTheCount_andKeepsIt() {
            CategoryResponse coffee = create("Coffee", EXPENSE);
            useInTransactions(coffee.id(), 1);

            client.delete().uri(BASE + "/{id}", coffee.id())
                    .exchange()
                    .expectStatus().isEqualTo(409)
                    .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .expectBody().jsonPath("$.detail").isEqualTo("Category is used by 1 transaction.");

            client.get().uri(BASE + "/{id}", coffee.id()).exchange().expectStatus().isOk();
        }

        @Test
        void deleteConflictMessage_countsEveryTransaction() {
            CategoryResponse coffee = create("Coffee", EXPENSE);
            useInTransactions(coffee.id(), 3);

            client.delete().uri(BASE + "/{id}", coffee.id())
                    .exchange()
                    .expectStatus().isEqualTo(409)
                    .expectBody().jsonPath("$.detail").isEqualTo("Category is used by 3 transactions.");
        }

        @Test
        void changingTheTypeOfACategoryUsedByATransaction_is409_andKeepsTheType() {
            CategoryResponse coffee = create("Coffee", EXPENSE);
            useInTransactions(coffee.id(), 1);

            client.put().uri(BASE + "/{id}", coffee.id())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"name": "Coffee", "type": "INCOME"}
                            """)
                    .exchange()
                    .expectStatus().isEqualTo(409)
                    .expectBody()
                    .jsonPath("$.detail").isEqualTo("Cannot change the type of a category that has transactions");

            assertThat(getCategory(coffee.id()).type()).isEqualTo(EXPENSE);
        }

        @Test
        void renamingACategoryUsedByATransaction_isAllowed() {
            // Only the type is locked by transactions; the name, description and colour can change.
            CategoryResponse coffee = create("Coffee", EXPENSE);
            useInTransactions(coffee.id(), 1);

            client.put().uri(BASE + "/{id}", coffee.id())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"name": "Coffee & Tea", "type": "EXPENSE"}
                            """)
                    .exchange()
                    .expectStatus().isOk();
        }
    }

    @Nested
    class AnotherUsersCategory {

        private Category theirs;

        @BeforeEach
        void createIt() {
            AppUser other = users.save(anAppUser().build());
            theirs = categories.save(aCategory().ownedBy(other.getId()).withName("Theirs").withType(EXPENSE).build());
        }

        @Test
        void get_is404() {
            client.get().uri(BASE + "/{id}", theirs.getId())
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .expectBody().jsonPath("$.detail").isEqualTo("Category " + theirs.getId() + " not found");
        }

        @Test
        void put_is404_andLeavesItUnchanged() {
            client.put().uri(BASE + "/{id}", theirs.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"name": "Hijacked", "type": "INCOME"}
                            """)
                    .exchange()
                    .expectStatus().isNotFound();

            Category reloaded = categories.findById(theirs.getId()).orElseThrow();
            assertThat(reloaded.getName()).isEqualTo("Theirs");
            assertThat(reloaded.getType()).isEqualTo(EXPENSE);
        }

        @Test
        void delete_is404_andKeepsIt() {
            client.delete().uri(BASE + "/{id}", theirs.getId())
                    .exchange()
                    .expectStatus().isNotFound();

            assertThat(categories.existsById(theirs.getId())).isTrue();
        }
    }

    @Nested
    class Timestamps {

        @Test
        void areIso8601Utc_onCreateAndGet() {
            CategoryResponse coffee = create("Coffee", EXPENSE);

            client.get().uri(BASE + "/{id}", coffee.id())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.createdAt").value(String.class, s -> assertThat(s).matches(ISO_UTC))
                    .jsonPath("$.updatedAt").value(String.class, s -> assertThat(s).matches(ISO_UTC));
        }

        @Test
        void areIso8601Utc_inTheCreateResponse() {
            client.post().uri(BASE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"name": "Coffee", "type": "EXPENSE"}
                            """)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody()
                    .jsonPath("$.createdAt").value(String.class, s -> assertThat(s).matches(ISO_UTC))
                    .jsonPath("$.updatedAt").value(String.class, s -> assertThat(s).matches(ISO_UTC));
        }

        @Test
        void update_returnsTheNewUpdatedAt_andKeepsCreatedAt() {
            CategoryResponse created = create("Coffee", EXPENSE);
            CategoryResponse before = getCategory(created.id());

            CategoryResponse updated = client.put().uri(BASE + "/{id}", created.id())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"name": "Coffee Shops", "type": "EXPENSE"}
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(CategoryResponse.class).returnResult().getResponseBody();
            CategoryResponse after = getCategory(created.id());

            assertThat(after.createdAt()).isEqualTo(before.createdAt());
            assertThat(after.updatedAt()).isAfter(before.updatedAt());
            // What PUT returns must be what was stored.
            assertThat(updated.updatedAt()).isEqualTo(after.updatedAt());
        }

        @Test
        void theCreateResponse_matchesWhatWasStored() {
            CategoryResponse created = create("Coffee", EXPENSE);
            CategoryResponse stored = getCategory(created.id());

            assertThat(created.createdAt()).isEqualTo(stored.createdAt());
            assertThat(created.updatedAt()).isEqualTo(stored.updatedAt());
        }
    }

    // --- helpers ---

    private long defaultUserId() {
        return appProperties.defaultUserId();
    }

    private CategoryResponse create(String name, TransactionType type) {
        return client.post().uri(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"name": "%s", "type": "%s"}
                        """.formatted(name, type))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(CategoryResponse.class).returnResult().getResponseBody();
    }

    private CategoryResponse getCategory(long id) {
        return client.get().uri(BASE + "/{id}", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody(CategoryResponse.class).returnResult().getResponseBody();
    }

    /** There is no transactions endpoint yet, so they go straight into the repository. */
    private void useInTransactions(long categoryId, int count) {
        Category category = categories.findById(categoryId).orElseThrow();
        for (int i = 0; i < count; i++) {
            transactions.save(anExpense().in(category).build());
        }
    }
}
