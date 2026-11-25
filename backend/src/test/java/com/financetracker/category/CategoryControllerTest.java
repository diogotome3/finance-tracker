package com.financetracker.category;

import static com.financetracker.common.domain.TransactionType.EXPENSE;
import static com.financetracker.common.domain.TransactionType.INCOME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.financetracker.common.config.AppProperties;
import com.financetracker.common.domain.TransactionType;
import com.financetracker.common.exception.ConflictException;
import com.financetracker.common.exception.ResourceNotFoundException;
import com.financetracker.common.security.SecurityConfig;

/**
 * The HTTP contract of {@link CategoryController}: binding, validation, status codes and the error format.
 *
 * A web slice: controllers, the GlobalExceptionHandler, Jackson and validation, with the service mocked and no
 * database. {@code @WebMvcTest} skips {@code @Configuration} classes, so the real security rules are imported
 * (otherwise Spring Security's defaults answer 401/403), together with the properties their CORS bean needs.
 * The business rules behind each 404/409 are tested in {@link CategoryServiceTest}.
 */
@WebMvcTest(CategoryController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(AppProperties.class)
class CategoryControllerTest {

    private static final String BASE = "/api/categories";
    private static final Instant NOW = Instant.parse("2026-11-25T10:15:30Z");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CategoryService categoryService;

    private static CategoryResponse response(long id, String name, TransactionType type) {
        return new CategoryResponse(id, name, type, null, "#8B5CF6", NOW, NOW);
    }

    @Nested
    class List_ {

        @Test
        void withoutType_returns200WithTheServicesList() {
            when(categoryService.list(null)).thenReturn(List.of(
                    response(1L, "Food", EXPENSE), response(2L, "Salary", INCOME)));

            MvcTestResult result = mvc.get().uri(BASE).exchange();

            assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
            assertThat(result).bodyJson().extractingPath("$[*].name").asArray().containsExactly("Food", "Salary");
            // A plain array, not a page: categories are not paginated.
            assertThat(result).bodyJson().extractingPath("$.length()").isEqualTo(2);
        }

        @Test
        void withType_passesItToTheService() {
            when(categoryService.list(INCOME)).thenReturn(List.of(response(2L, "Salary", INCOME)));

            assertThat(mvc.get().uri(BASE).param("type", "INCOME"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$[0].type").isEqualTo("INCOME");
        }

        @Test
        void withUnknownType_is400_andNeverCallsTheService() {
            MvcTestResult result = mvc.get().uri(BASE).param("type", "FOO").exchange();

            assertProblem(result, HttpStatus.BAD_REQUEST, "Request validation failed.");
            assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("type");
            verifyNoInteractions(categoryService);
        }
    }

    @Nested
    class Get {

        @Test
        void found_returns200WithTheCategory() {
            when(categoryService.get(42L)).thenReturn(response(42L, "Food", EXPENSE));

            MvcTestResult result = mvc.get().uri(BASE + "/42").exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(42);
            assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Food");
            assertThat(result).bodyJson().extractingPath("$.createdAt").isEqualTo("2026-11-25T10:15:30Z");
        }

        @Test
        void notFound_is404ProblemDetails() {
            when(categoryService.get(42L)).thenThrow(new ResourceNotFoundException("Category 42 not found"));

            assertProblem(mvc.get().uri(BASE + "/42").exchange(), HttpStatus.NOT_FOUND, "Category 42 not found");
        }

        @Test
        void nonNumericId_is400() {
            MvcTestResult result = mvc.get().uri(BASE + "/abc").exchange();

            assertProblem(result, HttpStatus.BAD_REQUEST, "Request validation failed.");
            assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("id");
            verifyNoInteractions(categoryService);
        }
    }

    @Nested
    class Create {

        @Test
        void valid_returns201WithLocationAndBody() {
            when(categoryService.create(any())).thenReturn(response(42L, "Food", EXPENSE));

            MvcTestResult result = postJson("""
                    {"name": "Food", "type": "EXPENSE", "color": "#8B5CF6"}
                    """).exchange();

            assertThat(result)
                    .hasStatus(HttpStatus.CREATED)
                    .hasHeader(HttpHeaders.LOCATION, "http://localhost/api/categories/42");
            assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(42);
            assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Food");
        }

        @Test
        void passesTheBoundRequestToTheService() {
            when(categoryService.create(any())).thenReturn(response(42L, "Food", EXPENSE));

            postJson("""
                    {"name": "  Food  ", "type": "EXPENSE", "description": "Groceries", "color": "#8B5CF6"}
                    """).exchange();

            // The record's compact constructor trims the name while Jackson binds it.
            verify(categoryService).create(new CategoryRequest("Food", EXPENSE, "Groceries", "#8B5CF6"));
        }

        @Test
        void blankName_is400WithAFieldError_andNeverCallsTheService() {
            MvcTestResult result = postJson("""
                    {"name": "   ", "type": "EXPENSE"}
                    """).exchange();

            assertValidationError(result, "name");
            verifyNoInteractions(categoryService);
        }

        @Test
        void badColour_is400WithAFieldError() {
            MvcTestResult result = postJson("""
                    {"name": "Food", "type": "EXPENSE", "color": "red"}
                    """).exchange();

            assertValidationError(result, "color");
            assertThat(result).bodyJson().extractingPath("$.errors[0].message")
                    .isEqualTo("must be a hex colour like #8B5CF6");
            verifyNoInteractions(categoryService);
        }

        @Test
        void missingType_is400WithAFieldError() {
            // Parses fine to type = null; @NotNull rejects it during validation.
            MvcTestResult result = postJson("""
                    {"name": "Food"}
                    """).exchange();

            assertValidationError(result, "type");
            verifyNoInteractions(categoryService);
        }

        @Test
        void unknownType_is400WithAFieldError() {
            // Fails earlier than validation: Jackson can't turn "FOO" into the enum.
            MvcTestResult result = postJson("""
                    {"name": "Food", "type": "FOO"}
                    """).exchange();

            assertProblem(result, HttpStatus.BAD_REQUEST, "Malformed request body.");
            assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("type");
            assertThat(result).bodyJson().extractingPath("$.errors[0].message").isEqualTo("has an invalid value");
            verifyNoInteractions(categoryService);
        }

        @Test
        void everyInvalidField_isReportedAtOnce() {
            MvcTestResult result = postJson("""
                    {"name": "", "color": "red"}
                    """).exchange();

            assertProblem(result, HttpStatus.BAD_REQUEST, "Request validation failed.");
            assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray()
                    .containsExactlyInAnyOrder("name", "type", "color");
        }

        @Test
        void duplicate_is409ProblemDetails() {
            when(categoryService.create(any()))
                    .thenThrow(new ConflictException("An expense category named 'Food' already exists"));

            assertProblem(postJson("""
                    {"name": "Food", "type": "EXPENSE"}
                    """).exchange(), HttpStatus.CONFLICT, "An expense category named 'Food' already exists");
        }
    }

    @Nested
    class Update {

        @Test
        void valid_returns200WithTheUpdatedCategory() {
            when(categoryService.update(eq(5L), any())).thenReturn(response(5L, "Groceries", EXPENSE));

            MvcTestResult result = putJson(5L, """
                    {"name": "Groceries", "type": "EXPENSE"}
                    """).exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Groceries");
            verify(categoryService).update(5L, new CategoryRequest("Groceries", EXPENSE, null, null));
        }

        @Test
        void invalidBody_is400_andNeverCallsTheService() {
            assertValidationError(putJson(5L, """
                    {"name": "", "type": "EXPENSE"}
                    """).exchange(), "name");
            verifyNoInteractions(categoryService);
        }

        @Test
        void notFound_is404ProblemDetails() {
            when(categoryService.update(eq(5L), any()))
                    .thenThrow(new ResourceNotFoundException("Category 5 not found"));

            assertProblem(putJson(5L, """
                    {"name": "Food", "type": "EXPENSE"}
                    """).exchange(), HttpStatus.NOT_FOUND, "Category 5 not found");
        }

        @Test
        void conflict_is409ProblemDetails() {
            when(categoryService.update(eq(5L), any()))
                    .thenThrow(new ConflictException("Cannot change the type of a category that has transactions"));

            assertProblem(putJson(5L, """
                    {"name": "Food", "type": "INCOME"}
                    """).exchange(), HttpStatus.CONFLICT, "Cannot change the type of a category that has transactions");
        }
    }

    @Nested
    class Delete {

        @Test
        void unused_returns204WithNoBody() {
            MvcTestResult result = mvc.delete().uri(BASE + "/5").exchange();

            assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
            assertThat(result.getResponse().getContentLength()).isZero();
            verify(categoryService).delete(5L);
        }

        @Test
        void notFound_is404ProblemDetails() {
            doThrow(new ResourceNotFoundException("Category 5 not found")).when(categoryService).delete(5L);

            assertProblem(mvc.delete().uri(BASE + "/5").exchange(), HttpStatus.NOT_FOUND, "Category 5 not found");
        }

        @Test
        void inUse_is409ProblemDetailsWithTheCount() {
            doThrow(new ConflictException("Category is used by 3 transactions.")).when(categoryService).delete(anyLong());

            assertProblem(mvc.delete().uri(BASE + "/5").exchange(),
                    HttpStatus.CONFLICT, "Category is used by 3 transactions.");
        }
    }

    // --- helpers ---

    private MockMvcRequestBuilder postJson(String json) {
        return mvc.post().uri(BASE).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private MockMvcRequestBuilder putJson(long id, String json) {
        return mvc.put().uri(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    /** A Bean Validation failure: 400 Problem Details whose errors list names the field. */
    private static void assertValidationError(MvcTestResult result, String field) {
        assertProblem(result, HttpStatus.BAD_REQUEST, "Request validation failed.");
        assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray().contains(field);
    }

    /** The RFC 9457 shape every error response has; GlobalExceptionHandlerIT covers the rest of the format. */
    private static void assertProblem(MvcTestResult result, HttpStatus status, String detail) {
        assertThat(result).hasStatus(status).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(status.value());
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo(detail);
    }
}
