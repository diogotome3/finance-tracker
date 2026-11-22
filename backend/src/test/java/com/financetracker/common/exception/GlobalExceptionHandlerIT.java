package com.financetracker.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.financetracker.budget.MonthlyBudget;
import com.financetracker.budget.MonthlyBudgetRepository;
import com.financetracker.common.domain.TransactionType;
import com.financetracker.support.TestcontainersConfiguration;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Sends real requests (MockMvc, through the security filters and the DispatcherServlet) to a test-only controller
 * that fails in every way the handler knows about, and checks each response is the same Problem Details shape.
 */
@Import({TestcontainersConfiguration.class, GlobalExceptionHandlerIT.ErrorTestController.class})
@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerIT {

    private static final String BASE = "/api/test-errors";

    @Autowired
    private MockMvcTester mvc;

    // --- Domain exceptions ---

    @Test
    void resourceNotFound_is404WithItsMessage() {
        MvcTestResult result = exchange(mvc.get().uri(BASE + "/not-found"));

        assertProblem(result, HttpStatus.NOT_FOUND, "Category 42 not found.");
        assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo(BASE + "/not-found");
    }

    @Test
    void conflict_is409WithItsMessage() {
        assertProblem(exchange(mvc.get().uri(BASE + "/conflict")),
                HttpStatus.CONFLICT, "Category 'Food' already exists.");
    }

    @Test
    void businessRule_is422WithItsMessage() {
        assertProblem(exchange(mvc.get().uri(BASE + "/business-rule")),
                HttpStatus.UNPROCESSABLE_CONTENT, "Transaction type must match the category type.");
    }

    // --- 400: request body ---

    @Test
    void invalidBody_is400WithEveryFieldError() {
        MvcTestResult result = exchange(postJson("{\"name\": \"\", \"amount\": -5}"));

        assertProblem(result, HttpStatus.BAD_REQUEST, "Request validation failed.");
        assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray()
                .containsExactlyInAnyOrder("name", "amount", "type");
        assertThat(result).bodyJson().extractingPath("$.errors[*].message").asArray()
                .hasSize(3).allSatisfy(message -> assertThat(message).asString().isNotBlank());
    }

    @Test
    void malformedJson_is400WithoutFieldErrors() {
        MvcTestResult result = exchange(postJson("{\"name\": "));

        assertProblem(result, HttpStatus.BAD_REQUEST, "Malformed request body.");
        assertThat(result).bodyJson().extractingPath("$.errors").asArray().isEmpty();
    }

    @Test
    void unknownEnumInBody_is400NamingTheField() {
        // Jackson's own message here names com.financetracker.common.domain.TransactionType; it must not leak.
        MvcTestResult result = exchange(postJson("{\"name\": \"Food\", \"amount\": 5, \"type\": \"FOO\"}"));

        assertProblem(result, HttpStatus.BAD_REQUEST, "Malformed request body.");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("type");
    }

    @Test
    void wrongTypeInBody_is400NamingTheField() {
        MvcTestResult result = exchange(postJson("{\"name\": \"Food\", \"amount\": \"abc\", \"type\": \"EXPENSE\"}"));

        assertProblem(result, HttpStatus.BAD_REQUEST, "Malformed request body.");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("amount");
    }

    // --- 400: query parameters ---

    @Test
    void unknownEnumInQuery_is400NamingTheParameter() {
        MvcTestResult result = exchange(mvc.get().uri(BASE + "/params").param("type", "FOO"));

        assertProblem(result, HttpStatus.BAD_REQUEST, "Request validation failed.");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("type");
    }

    @Test
    void wrongTypeInQuery_is400NamingTheParameter() {
        MvcTestResult result = exchange(mvc.get().uri(BASE + "/params").param("size", "abc"));

        assertProblem(result, HttpStatus.BAD_REQUEST, "Request validation failed.");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("size");
    }

    @Test
    void invalidQueryParameter_is400NamingTheParameter() {
        MvcTestResult result = exchange(mvc.get().uri(BASE + "/params").param("size", "500"));

        assertProblem(result, HttpStatus.BAD_REQUEST, "Request validation failed.");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("size");
    }

    @Test
    void missingRequiredParameter_is400NamingTheParameter() {
        MvcTestResult result = exchange(mvc.get().uri(BASE + "/required"));

        assertProblem(result, HttpStatus.BAD_REQUEST, "Request validation failed.");
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("q");
    }

    @Test
    void constraintViolationOutsideTheController_is400WithFieldErrors() {
        MvcTestResult result = exchange(mvc.get().uri(BASE + "/constraint-violation"));

        assertProblem(result, HttpStatus.BAD_REQUEST, "Request validation failed.");
        assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray()
                .containsExactlyInAnyOrder("name", "amount", "type");
    }

    // --- Database and unexpected errors ---

    @Test
    void databaseConstraintViolation_is409WithAGenericMessage() {
        // A real CHECK violation from PostgreSQL: its message contains the SQL and the table name.
        MvcTestResult result = exchange(mvc.get().uri(BASE + "/data-integrity"));

        assertProblem(result, HttpStatus.CONFLICT, "The request conflicts with existing data.");
        assertThat(body(result)).doesNotContainIgnoringCase("monthly_budgets").doesNotContainIgnoringCase("check");
    }

    @Test
    void unexpectedException_is500WithAGenericMessage() {
        MvcTestResult result = exchange(mvc.get().uri(BASE + "/unexpected"));

        assertProblem(result, HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
        assertThat(body(result)).doesNotContain("secret");
    }

    // --- Handled by Spring's base class, same shape ---

    @Test
    void unknownUrl_is404() {
        assertProblem(exchange(mvc.get().uri("/api/does-not-exist")), HttpStatus.NOT_FOUND, null);
    }

    @Test
    void unsupportedMethod_is405AndListsTheAllowedOnes() {
        MvcTestResult result = exchange(mvc.delete().uri(BASE + "/not-found"));

        assertProblem(result, HttpStatus.METHOD_NOT_ALLOWED, null);
        assertThat(result).hasHeader(HttpHeaders.ALLOW, "GET");
    }

    // --- Helpers ---

    private MvcTestResult exchange(MockMvcRequestBuilder request) {
        return request.exchange();
    }

    private MockMvcRequestBuilder postJson(String json) {
        return mvc.post().uri(BASE + "/body").contentType(MediaType.APPLICATION_JSON).content(json);
    }

    /** The shape every error must have. Pass detail = null to skip checking Spring's own wording. */
    private static void assertProblem(MvcTestResult result, HttpStatus status, String detail) {
        assertThat(result).hasStatus(status).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(status.value());
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo(status.getReasonPhrase());
        assertThat(result).bodyJson().extractingPath("$.instance").asString().isNotBlank();
        if (detail != null) {
            assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo(detail);
        }
        // Nothing internal: no stack trace, no exception or class names, no SQL.
        assertThat(body(result))
                .doesNotContainIgnoringCase("trace")
                .doesNotContainIgnoringCase("exception")
                .doesNotContain("com.financetracker", "org.springframework", "tools.jackson")
                .doesNotContainIgnoringCase("sql")
                .doesNotContainIgnoringCase("insert into");
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    // --- Test-only endpoints: one per kind of failure. Nested in a test class, so the app never scans it. ---

    record Body(@NotBlank String name, @NotNull @Positive BigDecimal amount, @NotNull TransactionType type) {
    }

    // No @Validated here on purpose: since Spring 6.1 controllers validate @RequestParam constraints themselves
    // (HandlerMethodValidationException). @Validated would switch to the older AOP validation, which throws
    // ConstraintViolationException with paths like "params.size" instead.
    @RestController
    @RequestMapping(BASE)
    static class ErrorTestController {

        private final MonthlyBudgetRepository budgets;
        private final Validator validator;

        ErrorTestController(MonthlyBudgetRepository budgets, Validator validator) {
            this.budgets = budgets;
            this.validator = validator;
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new ResourceNotFoundException("Category 42 not found.");
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ConflictException("Category 'Food' already exists.");
        }

        @GetMapping("/business-rule")
        void businessRule() {
            throw new BusinessRuleException("Transaction type must match the category type.");
        }

        @PostMapping("/body")
        void body(@Valid @RequestBody Body body) {
        }

        @GetMapping("/params")
        void params(@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                @RequestParam(required = false) TransactionType type) {
        }

        @GetMapping("/required")
        void required(@RequestParam String q) {
        }

        @GetMapping("/constraint-violation")
        void constraintViolation() {
            // What a @Validated service would throw.
            throw new ConstraintViolationException(validator.validate(new Body("", null, null)));
        }

        @GetMapping("/data-integrity")
        void dataIntegrity() {
            budgets.saveAndFlush(new MonthlyBudget(1L, 2026, 13, BigDecimal.TEN));   // month 13 breaks the CHECK
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("secret internal detail");
        }
    }
}
