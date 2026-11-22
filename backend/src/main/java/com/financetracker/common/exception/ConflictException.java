package com.financetracker.common.exception;

// 409: the request clashes with the current state. Examples: a duplicate category name, or deleting a category that is in use
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }

}
