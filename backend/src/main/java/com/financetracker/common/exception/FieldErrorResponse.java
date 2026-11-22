package com.financetracker.common.exception;

// One entry of the "errors" list in a 400 response. field is null for errors about the whole object.
public record FieldErrorResponse(String field, String message) {
}
