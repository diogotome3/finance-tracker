package com.financetracker.common.exception;

// 404. "The thing in the URL doesn't exist (or isn't yours)"
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
