package com.financetracker.category;

import static com.financetracker.common.domain.TransactionType.EXPENSE;
import static com.financetracker.common.domain.TransactionType.INCOME;
import static com.financetracker.support.TestData.aCategory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
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

    private CategoryService service;

    @BeforeEach
    void setUp() {
        service = new CategoryService(categoryRepository, new CategoryMapper(), currentUserProvider);
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
}
