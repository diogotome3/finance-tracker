package com.financetracker.category;

import static com.financetracker.common.domain.TransactionType.EXPENSE;
import static com.financetracker.common.domain.TransactionType.INCOME;
import static com.financetracker.support.TestData.aCategory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.financetracker.common.domain.TransactionType;
import com.financetracker.common.exception.ConflictException;
import com.financetracker.common.exception.ResourceNotFoundException;
import com.financetracker.common.security.CurrentUserProvider;
import com.financetracker.transaction.TransactionRepository;

/**
 * Business rules of {@link CategoryService}, with the repository and current user mocked.
 *
 * The mapper is the real one: it is a pure function, and mocking it would only add stubbing. Called directly
 * like this, the service has no Spring proxy, so {@code @Transactional} is not exercised here. The queries
 * themselves (ownership filter, sorting, case-insensitive match) are proven against PostgreSQL in
 * {@link CategoryRepositoryIT}.
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private static final long USER_ID = 7L;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private TransactionRepository transactionRepository;

    private CategoryService service;

    @BeforeEach
    void setUp() {
        service = new CategoryService(categoryRepository, new CategoryMapper(), currentUserProvider,
                transactionRepository);
        // Every service method asks for the current user, so this stub is used by every test (strict stubs).
        when(currentUserProvider.currentUserId()).thenReturn(USER_ID);
    }

    /** A category as a saved entity would look: with an id. */
    private static Category savedCategory(long id, long ownerId, String name, TransactionType type) {
        Category category = aCategory().ownedBy(ownerId).withName(name).withType(type).build();
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    @Nested
    class List_ {

        @Test
        void withoutFilter_returnsAllOfTheCurrentUsersCategories_inRepositoryOrder() {
            when(categoryRepository.findAllByUserIdOrderByTypeAscNameAsc(USER_ID)).thenReturn(List.of(
                    savedCategory(1L, USER_ID, "Food", EXPENSE),
                    savedCategory(2L, USER_ID, "Salary", INCOME)));

            List<CategoryResponse> result = service.list(null);

            assertThat(result).extracting(CategoryResponse::name).containsExactly("Food", "Salary");
            verify(categoryRepository, never()).findAllByUserIdAndTypeOrderByNameAsc(anyLong(), any());
        }

        @Test
        void withFilter_queriesOnlyThatType() {
            when(categoryRepository.findAllByUserIdAndTypeOrderByNameAsc(USER_ID, INCOME)).thenReturn(List.of(
                    savedCategory(2L, USER_ID, "Salary", INCOME)));

            List<CategoryResponse> result = service.list(INCOME);

            assertThat(result).singleElement().satisfies(c -> {
                assertThat(c.name()).isEqualTo("Salary");
                assertThat(c.type()).isEqualTo(INCOME);
            });
            verify(categoryRepository, never()).findAllByUserIdOrderByTypeAscNameAsc(anyLong());
        }

        @Test
        void whenTheUserHasNoCategories_returnsAnEmptyList() {
            when(categoryRepository.findAllByUserIdOrderByTypeAscNameAsc(USER_ID)).thenReturn(List.of());

            assertThat(service.list(null)).isEmpty();
        }
    }

    @Nested
    class Get {

        @Test
        void found_returnsTheCategory() {
            when(categoryRepository.findByIdAndUserId(42L, USER_ID))
                    .thenReturn(Optional.of(savedCategory(42L, USER_ID, "Food", EXPENSE)));

            CategoryResponse result = service.get(42L);

            assertThat(result.id()).isEqualTo(42L);
            assertThat(result.name()).isEqualTo("Food");
        }

        @Test
        void notFound_throwsResourceNotFound() {
            when(categoryRepository.findByIdAndUserId(42L, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.get(42L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Category 42 not found");
        }

        @Test
        void ownedByAnotherUser_behavesExactlyLikeNotFound() {
            // Category 42 exists, but belongs to someone else. The service must look it up scoped to the
            // current user (so the repository finds nothing), never by id alone.
            // (The filtering itself is proven against the database in CategoryRepositoryIT.)
            when(categoryRepository.findByIdAndUserId(42L, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.get(42L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Category 42 not found");   // same message: no hint that 42 exists
            verify(categoryRepository).findByIdAndUserId(42L, USER_ID);
            verify(categoryRepository, never()).findById(any());
        }
    }

    @Nested
    class Create {

        @Test
        void savesWithTheCurrentUsersId_andTheNormalisedInput() {
            when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

            service.create(new CategoryRequest("  Groceries  ", EXPENSE, "   ", "#8B5CF6"));

            ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
            verify(categoryRepository).save(captor.capture());
            Category saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getName()).isEqualTo("Groceries");
            assertThat(saved.getType()).isEqualTo(EXPENSE);
            assertThat(saved.getDescription()).isNull();
            assertThat(saved.getColor()).isEqualTo("#8B5CF6");
        }

        @Test
        void checksForDuplicates_withTheTrimmedName() {
            when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

            service.create(new CategoryRequest("  Groceries  ", EXPENSE, null, null));

            verify(categoryRepository).existsByUserIdAndTypeAndNameIgnoreCase(USER_ID, EXPENSE, "Groceries");
        }

        @Test
        void returnsTheSavedCategory() {
            when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
                Category category = inv.getArgument(0);
                ReflectionTestUtils.setField(category, "id", 42L);   // what the database would assign
                return category;
            });

            CategoryResponse result = service.create(new CategoryRequest("Groceries", EXPENSE, "Food", "#8B5CF6"));

            assertThat(result.id()).isEqualTo(42L);
            assertThat(result.name()).isEqualTo("Groceries");
            assertThat(result.description()).isEqualTo("Food");
        }

        @Test
        void duplicateNameInDifferentCase_throwsConflict_andSavesNothing() {
            // The repository's case-insensitive match is proven in CategoryRepositoryIT; here it reports a match.
            when(categoryRepository.existsByUserIdAndTypeAndNameIgnoreCase(USER_ID, EXPENSE, "groceries"))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.create(new CategoryRequest("groceries", EXPENSE, null, null)))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("An expense category named 'groceries' already exists");

            verify(categoryRepository, never()).save(any());
        }

        @Test
        void sameNameWithTheOtherType_isAllowed() {
            when(categoryRepository.existsByUserIdAndTypeAndNameIgnoreCase(USER_ID, INCOME, "Groceries"))
                    .thenReturn(false);
            when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

            CategoryResponse result = service.create(new CategoryRequest("Groceries", INCOME, null, null));

            assertThat(result.type()).isEqualTo(INCOME);
        }
    }

    @Nested
    class Update {

        @Test
        void notFound_throwsResourceNotFound_andChecksNothingElse() {
            when(categoryRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(5L, new CategoryRequest("Food", EXPENSE, null, null)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Category 5 not found");
            verifyNoInteractions(transactionRepository);
        }

        @Test
        void appliesAllFields_andReturnsTheUpdatedCategory() {
            Category category = savedCategory(5L, USER_ID, "Food", EXPENSE);
            when(categoryRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(category));

            CategoryResponse result = service.update(5L,
                    new CategoryRequest("  Groceries  ", EXPENSE, "Weekly shop", "#8B5CF6"));

            assertThat(result.id()).isEqualTo(5L);
            assertThat(result.name()).isEqualTo("Groceries");
            assertThat(result.type()).isEqualTo(EXPENSE);
            assertThat(result.description()).isEqualTo("Weekly shop");
            assertThat(result.color()).isEqualTo("#8B5CF6");
            // The managed entity itself was changed: dirty checking writes it at commit.
            assertThat(category.getName()).isEqualTo("Groceries");
        }

        @Test
        void nameTakenByAnotherCategoryOfTheSameType_throwsConflict_andChangesNothing() {
            Category category = savedCategory(5L, USER_ID, "Food", EXPENSE);
            when(categoryRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(category));
            when(categoryRepository.existsByUserIdAndTypeAndNameIgnoreCaseAndIdNot(USER_ID, EXPENSE, "transport", 5L))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.update(5L, new CategoryRequest("transport", EXPENSE, null, null)))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("An expense category named 'transport' already exists");
            assertThat(category.getName()).isEqualTo("Food");
        }

        @Test
        void duplicateCheck_usesTheNewType() {
            // Moving "Food" to INCOME must clash with an existing INCOME category, not an EXPENSE one.
            Category category = savedCategory(5L, USER_ID, "Food", EXPENSE);
            when(categoryRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(category));
            when(transactionRepository.existsByCategoryId(5L)).thenReturn(false);
            when(categoryRepository.existsByUserIdAndTypeAndNameIgnoreCaseAndIdNot(USER_ID, INCOME, "Salary", 5L))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.update(5L, new CategoryRequest("Salary", INCOME, null, null)))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("An income category named 'Salary' already exists");
        }

        @Test
        void changingOnlyTheCaseOfItsOwnName_isAllowed() {
            // The query excludes the category being updated, so its own name is no conflict (verified in the IT).
            Category category = savedCategory(5L, USER_ID, "Coffee", EXPENSE);
            when(categoryRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(category));

            CategoryResponse result = service.update(5L, new CategoryRequest("coffee", EXPENSE, null, null));

            assertThat(result.name()).isEqualTo("coffee");
            verify(categoryRepository).existsByUserIdAndTypeAndNameIgnoreCaseAndIdNot(USER_ID, EXPENSE, "coffee", 5L);
            verify(categoryRepository, never()).existsByUserIdAndTypeAndNameIgnoreCase(anyLong(), any(), any());
        }

        @Test
        void changingTheType_ofACategoryWithTransactions_throwsConflict_andChangesNothing() {
            Category category = savedCategory(5L, USER_ID, "Food", EXPENSE);
            when(categoryRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(category));
            when(transactionRepository.existsByCategoryId(5L)).thenReturn(true);

            assertThatThrownBy(() -> service.update(5L, new CategoryRequest("Food", INCOME, null, null)))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Cannot change the type of a category that has transactions");
            assertThat(category.getType()).isEqualTo(EXPENSE);
        }

        @Test
        void changingTheType_ofAnUnusedCategory_isAllowed() {
            Category category = savedCategory(5L, USER_ID, "Other", EXPENSE);
            when(categoryRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(category));
            when(transactionRepository.existsByCategoryId(5L)).thenReturn(false);

            CategoryResponse result = service.update(5L, new CategoryRequest("Other", INCOME, null, null));

            assertThat(result.type()).isEqualTo(INCOME);
        }

        @Test
        void keepingTheType_neverChecksTransactions() {
            Category category = savedCategory(5L, USER_ID, "Food", EXPENSE);
            when(categoryRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(category));

            service.update(5L, new CategoryRequest("Groceries", EXPENSE, null, null));

            verifyNoInteractions(transactionRepository);
        }
    }

    @Nested
    class Delete {

        @Test
        void notFound_throwsResourceNotFound_andDeletesNothing() {
            when(categoryRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(5L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Category 5 not found");
            verifyNoInteractions(transactionRepository);
            verify(categoryRepository, never()).delete(any());
            verify(categoryRepository, never()).deleteById(any());
        }

        @Test
        void inUse_throwsConflictWithTheCount_andNeverDeletes() {
            Category category = savedCategory(5L, USER_ID, "Food", EXPENSE);
            when(categoryRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(category));
            when(transactionRepository.countByCategoryId(5L)).thenReturn(3L);

            assertThatThrownBy(() -> service.delete(5L))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Category is used by 3 transactions.");
            verify(categoryRepository, never()).delete(any());
            verify(categoryRepository, never()).deleteById(any());
        }

        @Test
        void inUseByOneTransaction_usesTheSingular() {
            Category category = savedCategory(5L, USER_ID, "Food", EXPENSE);
            when(categoryRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(category));
            when(transactionRepository.countByCategoryId(5L)).thenReturn(1L);

            assertThatThrownBy(() -> service.delete(5L))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Category is used by 1 transaction.");
            verify(categoryRepository, never()).delete(any());
        }

        @Test
        void notInUse_deletesTheLoadedCategory() {
            Category category = savedCategory(5L, USER_ID, "Food", EXPENSE);
            when(categoryRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(category));
            when(transactionRepository.countByCategoryId(5L)).thenReturn(0L);

            service.delete(5L);

            verify(categoryRepository).delete(category);
        }
    }
}
