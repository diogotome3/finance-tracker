package com.financetracker.common.exception;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * Turns every exception thrown while handling a request into an RFC 9457 Problem Details response.
 *
 * The base class already handles Spring MVC's own exceptions (unknown URL → 404, wrong method → 405, ...).
 * The 400s are overridden to add an "errors" list; the domain, database and unexpected exceptions are added.
 *
 * Rule: "detail" is read by the client. Only our own messages go there, never ex.getMessage() of a library
 * exception (it can contain SQL, class names or other internals).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String VALIDATION_FAILED = "Request validation failed.";
    private static final String INVALID_VALUE = "has an invalid value";

    // --- Domain exceptions: their messages are written for the client ---

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail handleBusinessRule(BusinessRuleException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
    }

    // --- 400s with a list of field errors ---

    /** {@code @Valid @RequestBody} failed. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        Stream<FieldErrorResponse> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldErrorResponse(e.getField(), e.getDefaultMessage()));
        // Class-level constraints (e.g. "from must not be after to") aren't tied to one field.
        Stream<FieldErrorResponse> globalErrors = ex.getBindingResult().getGlobalErrors().stream()
                .map(e -> new FieldErrorResponse(null, e.getDefaultMessage()));

        return badRequest(ex, VALIDATION_FAILED, Stream.concat(fieldErrors, globalErrors).toList(),
                headers, status, request);
    }

    /** A constraint on a {@code @RequestParam} / {@code @PathVariable} failed, e.g. {@code @Max(100) int size}. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        List<FieldErrorResponse> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldErrorResponse(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();

        return badRequest(ex, VALIDATION_FAILED, errors, headers, status, request);
    }

    /** A required {@code @RequestParam} is missing. */
    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {

        return badRequest(ex, VALIDATION_FAILED, List.of(new FieldErrorResponse(ex.getParameterName(), "is required")),
                headers, status, request);
    }

    /** A parameter can't be converted to its type, e.g. {@code ?type=FOO} for an enum or {@code ?page=abc}. */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        return badRequest(ex, VALIDATION_FAILED, List.of(new FieldErrorResponse(ex.getPropertyName(), INVALID_VALUE)),
                headers, status, request);
    }

    /**
     * The body isn't valid JSON, or a value has the wrong type ({@code "amount": "abc"}, {@code "type": "FOO"}).
     * Jackson's message names our classes and quotes the input, so it is never sent to the client.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        List<FieldErrorResponse> errors = List.of();
        if (ex.getCause() instanceof MismatchedInputException mismatch) {
            String field = mismatch.getPath().stream()
                    .map(JacksonException.Reference::getPropertyName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("."));
            if (!field.isEmpty()) {
                errors = List.of(new FieldErrorResponse(field, INVALID_VALUE));
            }
        }
        return badRequest(ex, "Malformed request body.", errors, headers, status, request);
    }

    /**
     * Validation that ran outside a controller, e.g. on a {@code @Validated} service. Controller parameters raise
     * {@link HandlerMethodValidationException} instead; this is a safety net.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldErrorResponse> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldErrorResponse(lastNodeName(v.getPropertyPath()), v.getMessage()))
                .toList();

        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, VALIDATION_FAILED);
        body.setProperty("errors", errors);
        return body;
    }

    // --- Database and unexpected errors: generic message, details only in the log ---

    /**
     * A database constraint rejected the change. Services check the common cases first and throw a
     * {@link ConflictException} with a friendly message; this catches what slips past them (e.g. two
     * requests racing). The exception message contains SQL, table names and values, so it is only logged.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The request conflicts with existing data.");
    }

    /** Anything else is a bug. The full exception goes to the log; the client learns nothing about it. */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }

    /** "create.request.name" → "name": the client doesn't know our method and parameter names. */
    private static String lastNodeName(Path path) {
        String name = null;
        for (Path.Node node : path) {
            name = node.getName();
        }
        return name;
    }

    /** A 400 with our detail and the errors list, sent through the base class's single exit point. */
    private ResponseEntity<Object> badRequest(Exception ex, String detail, List<FieldErrorResponse> errors,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }
}
