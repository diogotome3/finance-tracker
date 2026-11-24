package com.financetracker.category;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.financetracker.common.domain.TransactionType;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Each validation rule on {@link CategoryRequest}, tested at its boundaries.
 *
 * No Spring needed: these are standard constraints, so a plain Hibernate Validator behaves exactly like the one
 * the controllers use. An unknown {@code type} can't be tested here, because Jackson refuses it before validation
 * runs; see {@link CategoryRequestJsonTest}.
 */
class CategoryRequestValidationTest {

    private static final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private static final Validator validator = factory.getValidator();

    @AfterAll
    static void closeFactory() {
        factory.close();
    }

    private static CategoryRequest request(String name, TransactionType type, String description, String color) {
        return new CategoryRequest(name, type, description, color);
    }

    private static Set<ConstraintViolation<CategoryRequest>> validate(CategoryRequest request) {
        return validator.validate(request);
    }

    /** Asserts there is exactly one violation, and that it is on the given field. */
    private static void assertSingleViolationOn(CategoryRequest request, String field) {
        assertThat(validate(request))
                .singleElement()
                .satisfies(v -> assertThat(v.getPropertyPath()).hasToString(field));
    }

    @Test
    void minimalRequest_isValid() {
        assertThat(validate(request("Groceries", TransactionType.EXPENSE, null, null))).isEmpty();
    }

    @Test
    void fullRequest_isValid() {
        assertThat(validate(request("Salary", TransactionType.INCOME, "Monthly pay", "#8B5CF6"))).isEmpty();
    }

    // --- name: required, 1–50 characters ---

    @Test
    void name_null_isRejected() {
        assertSingleViolationOn(request(null, TransactionType.EXPENSE, null, null), "name");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n"})
    void name_blank_isRejected(String name) {
        assertSingleViolationOn(request(name, TransactionType.EXPENSE, null, null), "name");
    }

    @Test
    void name_ofOneChar_isValid() {
        assertThat(validate(request("x", TransactionType.EXPENSE, null, null))).isEmpty();
    }

    @Test
    void name_of50Chars_isValid() {
        assertThat(validate(request("x".repeat(50), TransactionType.EXPENSE, null, null))).isEmpty();
    }

    @Test
    void name_of51Chars_isRejected() {
        assertSingleViolationOn(request("x".repeat(51), TransactionType.EXPENSE, null, null), "name");
    }

    @Test
    void name_of50CharsPlusSurroundingSpaces_isValid() {
        // Proves trimming happens before @Size is checked: the raw input is 54 characters.
        assertThat(validate(request("  " + "x".repeat(50) + "  ", TransactionType.EXPENSE, null, null))).isEmpty();
    }

    // --- type: required ---

    @Test
    void type_null_isRejected() {
        assertSingleViolationOn(request("Groceries", null, null, null), "type");
    }

    // --- description: optional, at most 255 characters ---

    @Test
    void description_of255Chars_isValid() {
        assertThat(validate(request("Groceries", TransactionType.EXPENSE, "d".repeat(255), null))).isEmpty();
    }

    @Test
    void description_of256Chars_isRejected() {
        assertSingleViolationOn(request("Groceries", TransactionType.EXPENSE, "d".repeat(256), null), "description");
    }

    @Test
    void description_of255CharsPlusSurroundingSpaces_isValid() {
        assertThat(validate(request("Groceries", TransactionType.EXPENSE, " " + "d".repeat(255) + " ", null)))
                .isEmpty();
    }

    // --- color: optional, #RRGGBB ---

    @ParameterizedTest
    @ValueSource(strings = {"#8B5CF6", "#8b5cf6", "#000000", "#FFFFFF"})
    void color_validHex_isAccepted(String color) {
        assertThat(validate(request("Groceries", TransactionType.EXPENSE, null, color))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"8B5CF6", "#FFF", "#8B5CF", "#8B5CF60", "#GGGGGG", "", "purple"})
    void color_invalid_isRejected(String color) {
        assertSingleViolationOn(request("Groceries", TransactionType.EXPENSE, null, color), "color");
    }

    @Test
    void color_invalid_hasAHelpfulMessage() {
        assertThat(validate(request("Groceries", TransactionType.EXPENSE, null, "#FFF")))
                .singleElement()
                .satisfies(v -> assertThat(v.getMessage()).isEqualTo("must be a hex colour like #8B5CF6"));
    }
}
