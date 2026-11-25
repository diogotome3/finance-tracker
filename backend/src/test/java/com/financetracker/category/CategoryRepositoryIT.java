package com.financetracker.category;

import static com.financetracker.common.domain.TransactionType.EXPENSE;
import static com.financetracker.common.domain.TransactionType.INCOME;
import static com.financetracker.support.TestData.aCategory;
import static com.financetracker.support.TestData.anAppUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
class CategoryRepositoryIT {

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

    @BeforeEach
    void setUp() {
        user = users.saveAndFlush(anAppUser().build());
        otherUser = users.saveAndFlush(anAppUser().build());
    }

    // --- Constraints: each bad insert must be rejected by the database, by the expected constraint ---

    @Test
    void rejectsSameNameDifferingOnlyInCase_forSameUserAndType() {
        categories.saveAndFlush(aCategory().ownedBy(user.getId()).withName("Food").withType(EXPENSE).build());
        Category duplicate = aCategory().ownedBy(user.getId()).withName("food").withType(EXPENSE).build();

        assertThatThrownBy(() -> categories.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_categories_user_type_name");
    }

    @Test
    void allowsSameNameWithDifferentType() {
        categories.saveAndFlush(aCategory().ownedBy(user.getId()).withName("Other").withType(EXPENSE).build());

        Category income = categories.saveAndFlush(
                aCategory().ownedBy(user.getId()).withName("Other").withType(INCOME).build());

        assertThat(income.getId()).isNotNull();
    }

    @Test
    void allowsSameNameAndTypeForDifferentUsers() {
        categories.saveAndFlush(aCategory().ownedBy(user.getId()).withName("Food").build());

        Category other = categories.saveAndFlush(aCategory().ownedBy(otherUser.getId()).withName("Food").build());

        assertThat(other.getId()).isNotNull();
    }

    @Test
    void rejectsInvalidColor() {
        Category category = aCategory().ownedBy(user.getId()).withColor("red").build();

        assertThatThrownBy(() -> categories.saveAndFlush(category))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("categories_color_check");
    }

    @Test
    void allowsMissingColor() {
        Category saved = categories.saveAndFlush(aCategory().ownedBy(user.getId()).withColor(null).build());

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void rejectsNameLongerThan50() {
        Category category = aCategory().ownedBy(user.getId()).withName("x".repeat(51)).build();

        assertThatThrownBy(() -> categories.saveAndFlush(category))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("value too long for type character varying(50)");
    }

    @Test
    void rejectsDescriptionLongerThan255() {
        Category category = aCategory().ownedBy(user.getId()).withDescription("x".repeat(256)).build();

        assertThatThrownBy(() -> categories.saveAndFlush(category))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("value too long for type character varying(255)");
    }

    @Test
    void rejectsUnknownUser() {
        Category category = aCategory().ownedBy(999_999L).build();

        assertThatThrownBy(() -> categories.saveAndFlush(category))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("categories_user_id_fkey");
    }

    @Test
    void rejectsUnknownType() {
        // The Java enum can't hold an invalid type, so bypass JPA to prove the CHECK constraint itself.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO categories (user_id, name, type) VALUES (?, 'X', 'TRANSFER')", user.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("categories_type_check");
    }

    @Test
    void deletingUserDeletesTheirCategories() {
        categories.saveAndFlush(aCategory().ownedBy(user.getId()).build());

        users.delete(user);
        users.flush();
        // The database cascaded the delete; clear Hibernate's cache so the next query really hits the DB.
        entityManager.clear();

        assertThat(categories.findAllByUserIdOrderByTypeAscNameAsc(user.getId())).isEmpty();
    }

    // --- Repository methods ---

    @Test
    void findByIdAndUserId_returnsCategoryOnlyForItsOwner() {
        Category saved = categories.saveAndFlush(aCategory().ownedBy(user.getId()).build());

        assertThat(categories.findByIdAndUserId(saved.getId(), user.getId())).contains(saved);
        assertThat(categories.findByIdAndUserId(saved.getId(), otherUser.getId())).isEmpty();
    }

    @Test
    void findAllByUserId_returnsOnlyUsersCategories_sortedByTypeThenName() {
        categories.saveAndFlush(aCategory().ownedBy(user.getId()).withName("Salary").withType(INCOME).build());
        categories.saveAndFlush(aCategory().ownedBy(user.getId()).withName("Transport").withType(EXPENSE).build());
        categories.saveAndFlush(aCategory().ownedBy(user.getId()).withName("Food").withType(EXPENSE).build());
        categories.saveAndFlush(aCategory().ownedBy(otherUser.getId()).withName("Books").withType(EXPENSE).build());

        assertThat(categories.findAllByUserIdOrderByTypeAscNameAsc(user.getId()))
                .extracting(Category::getName)
                .containsExactly("Food", "Transport", "Salary");   // EXPENSE sorts before INCOME
    }

    @Test
    void findAllByUserIdAndType_returnsOnlyUsersCategoriesOfThatType_sortedByName() {
        categories.saveAndFlush(aCategory().ownedBy(user.getId()).withName("Transport").withType(EXPENSE).build());
        categories.saveAndFlush(aCategory().ownedBy(user.getId()).withName("Food").withType(EXPENSE).build());
        categories.saveAndFlush(aCategory().ownedBy(user.getId()).withName("Salary").withType(INCOME).build());
        categories.saveAndFlush(aCategory().ownedBy(otherUser.getId()).withName("Books").withType(EXPENSE).build());

        assertThat(categories.findAllByUserIdAndTypeOrderByNameAsc(user.getId(), EXPENSE))
                .extracting(Category::getName)
                .containsExactly("Food", "Transport");
        assertThat(categories.findAllByUserIdAndTypeOrderByNameAsc(user.getId(), INCOME))
                .extracting(Category::getName)
                .containsExactly("Salary");
    }

    @Test
    void existsByUserIdAndTypeAndNameIgnoreCase_matchesIgnoringCase_onlyForSameUserAndType() {
        categories.saveAndFlush(aCategory().ownedBy(user.getId()).withName("Food").withType(EXPENSE).build());

        assertThat(categories.existsByUserIdAndTypeAndNameIgnoreCase(user.getId(), EXPENSE, "FOOD")).isTrue();
        assertThat(categories.existsByUserIdAndTypeAndNameIgnoreCase(user.getId(), INCOME, "Food")).isFalse();
        assertThat(categories.existsByUserIdAndTypeAndNameIgnoreCase(otherUser.getId(), EXPENSE, "Food")).isFalse();
    }

    @Test
    void existsByUserIdAndTypeAndNameIgnoreCaseAndIdNot_ignoresTheCategoryItself() {
        Category coffee = categories.saveAndFlush(
                aCategory().ownedBy(user.getId()).withName("Coffee").withType(EXPENSE).build());
        Category food = categories.saveAndFlush(
                aCategory().ownedBy(user.getId()).withName("Food").withType(EXPENSE).build());

        // Its own name, in any case, is not a conflict...
        assertThat(categories.existsByUserIdAndTypeAndNameIgnoreCaseAndIdNot(
                user.getId(), EXPENSE, "coffee", coffee.getId())).isFalse();
        // ...another category's name is, ignoring case...
        assertThat(categories.existsByUserIdAndTypeAndNameIgnoreCaseAndIdNot(
                user.getId(), EXPENSE, "FOOD", coffee.getId())).isTrue();
        // ...but only within the same type and the same user.
        assertThat(categories.existsByUserIdAndTypeAndNameIgnoreCaseAndIdNot(
                user.getId(), INCOME, "Food", coffee.getId())).isFalse();
        assertThat(categories.existsByUserIdAndTypeAndNameIgnoreCaseAndIdNot(
                otherUser.getId(), EXPENSE, "Food", coffee.getId())).isFalse();
        assertThat(food.getId()).isNotEqualTo(coffee.getId());
    }
}
