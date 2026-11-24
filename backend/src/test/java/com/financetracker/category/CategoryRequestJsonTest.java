package com.financetracker.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import com.financetracker.common.domain.TransactionType;

import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link CategoryRequest} as Jackson reads it from a request body, using the app's real mapper.
 *
 * Covers what the validator can't see: an unknown {@code type} fails during deserialization, and normalisation
 * runs because Jackson builds records through their canonical constructor.
 */
@JsonTest
class CategoryRequestJsonTest {

    @Autowired
    private JsonMapper mapper;

    private CategoryRequest read(String json) {
        return mapper.readValue(json, CategoryRequest.class);
    }

    @Test
    void fullBody_isRead() {
        CategoryRequest request = read("""
                {"name": "Salary", "type": "INCOME", "description": "Monthly pay", "color": "#8B5CF6"}
                """);

        assertThat(request).isEqualTo(new CategoryRequest("Salary", TransactionType.INCOME, "Monthly pay", "#8B5CF6"));
    }

    @Test
    void missingOptionalFields_becomeNull() {
        CategoryRequest request = read("""
                {"name": "Groceries", "type": "EXPENSE"}
                """);

        assertThat(request.description()).isNull();
        assertThat(request.color()).isNull();
    }

    @Test
    void missingType_becomesNull_soValidationCanRejectIt() {
        assertThat(read("""
                {"name": "Groceries"}
                """).type()).isNull();
    }

    @Test
    void unknownType_isRefused() {
        assertThatThrownBy(() -> read("""
                {"name": "Groceries", "type": "SAVINGS"}
                """))
                .isInstanceOf(InvalidFormatException.class);
    }

    @Test
    void lowercaseType_isRefused() {
        // Enum names are case-sensitive: the API accepts exactly INCOME or EXPENSE.
        assertThatThrownBy(() -> read("""
                {"name": "Groceries", "type": "expense"}
                """))
                .isInstanceOf(InvalidFormatException.class);
    }

    // --- Normalisation happens on deserialization ---

    @Test
    void nameAndDescription_areTrimmed() {
        CategoryRequest request = read("""
                {"name": "  Groceries  ", "type": "EXPENSE", "description": "  Food shopping  "}
                """);

        assertThat(request.name()).isEqualTo("Groceries");
        assertThat(request.description()).isEqualTo("Food shopping");
    }

    @Test
    void blankDescription_becomesNull() {
        CategoryRequest request = read("""
                {"name": "Groceries", "type": "EXPENSE", "description": "   "}
                """);

        assertThat(request.description()).isNull();
    }
}
