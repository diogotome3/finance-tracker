package com.financetracker.category;

import static com.financetracker.support.TestData.aCategory;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.financetracker.common.domain.TransactionType;

/** Plain unit tests: the mapper has no dependencies, so no Spring context is needed. */
class CategoryMapperTest {

    private final CategoryMapper mapper = new CategoryMapper();

    @Nested
    class ToResponse {

        @Test
        void copiesEveryField() {
            Category category = aCategory()
                    .ownedBy(7L)
                    .withName("Groceries")
                    .withType(TransactionType.EXPENSE)
                    .withDescription("Food shopping")
                    .withColor("#8B5CF6")
                    .build();
            // id and timestamps are set by JPA, so BaseEntity has no setters; fill them in as a saved entity would have.
            Instant created = Instant.parse("2026-01-01T10:00:00Z");
            Instant updated = Instant.parse("2026-02-01T10:00:00Z");
            ReflectionTestUtils.setField(category, "id", 42L);
            ReflectionTestUtils.setField(category, "createdAt", created);
            ReflectionTestUtils.setField(category, "updatedAt", updated);

            CategoryResponse response = mapper.toResponse(category);

            assertThat(response).isEqualTo(new CategoryResponse(
                    42L, "Groceries", TransactionType.EXPENSE, "Food shopping", "#8B5CF6", created, updated));
        }

        @Test
        void keepsOptionalFieldsNull() {
            Category category = aCategory().ownedBy(7L).withDescription(null).withColor(null).build();

            CategoryResponse response = mapper.toResponse(category);

            assertThat(response.description()).isNull();
            assertThat(response.color()).isNull();
        }
    }

    @Nested
    class ToEntity {

        @Test
        void copiesEveryRequestField_andSetsTheGivenOwner() {
            CategoryRequest request = new CategoryRequest("Salary", TransactionType.INCOME, "Monthly pay", "#8B5CF6");

            Category category = mapper.toEntity(request, 7L);

            assertThat(category.getUserId()).isEqualTo(7L);
            assertThat(category.getName()).isEqualTo("Salary");
            assertThat(category.getType()).isEqualTo(TransactionType.INCOME);
            assertThat(category.getDescription()).isEqualTo("Monthly pay");
            assertThat(category.getColor()).isEqualTo("#8B5CF6");
        }

        @Test
        void leavesIdAndTimestampsForJpa() {
            Category category = mapper.toEntity(
                    new CategoryRequest("Salary", TransactionType.INCOME, null, null), 7L);

            assertThat(category.getId()).isNull();
            assertThat(category.getCreatedAt()).isNull();
            assertThat(category.getUpdatedAt()).isNull();
        }

        @Test
        void usesTheNormalisedRequestValues() {
            CategoryRequest request = new CategoryRequest("  Salary  ", TransactionType.INCOME, "   ", null);

            Category category = mapper.toEntity(request, 7L);

            assertThat(category.getName()).isEqualTo("Salary");
            assertThat(category.getDescription()).isNull();
        }
    }

    @Nested
    class UpdateEntity {

        @Test
        void overwritesEveryEditableField() {
            Category category = aCategory()
                    .ownedBy(7L)
                    .withName("Old name")
                    .withType(TransactionType.EXPENSE)
                    .withDescription("Old description")
                    .withColor("#000000")
                    .build();
            CategoryRequest request = new CategoryRequest("New name", TransactionType.INCOME, "New description", "#FFFFFF");

            mapper.updateEntity(category, request);

            assertThat(category.getName()).isEqualTo("New name");
            assertThat(category.getType()).isEqualTo(TransactionType.INCOME);
            assertThat(category.getDescription()).isEqualTo("New description");
            assertThat(category.getColor()).isEqualTo("#FFFFFF");
        }

        @Test
        void clearsOptionalFields_whenTheRequestOmitsThem() {
            // Update is a full replacement (PUT), not a patch: leaving a field out clears it.
            Category category = aCategory().ownedBy(7L).withDescription("Old").withColor("#000000").build();

            mapper.updateEntity(category, new CategoryRequest("Name", TransactionType.EXPENSE, null, null));

            assertThat(category.getDescription()).isNull();
            assertThat(category.getColor()).isNull();
        }

        @Test
        void keepsOwnerIdAndTimestamps() {
            Category category = aCategory().ownedBy(7L).build();
            Instant created = Instant.parse("2026-01-01T10:00:00Z");
            ReflectionTestUtils.setField(category, "id", 42L);
            ReflectionTestUtils.setField(category, "createdAt", created);

            mapper.updateEntity(category, new CategoryRequest("Name", TransactionType.EXPENSE, null, null));

            assertThat(category.getUserId()).isEqualTo(7L);
            assertThat(category.getId()).isEqualTo(42L);
            assertThat(category.getCreatedAt()).isEqualTo(created);
        }
    }
}
