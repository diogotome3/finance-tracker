package com.financetracker.common.exception;

// 422: the request is well-formed but breaks a rule. Example: the transaction type is not the category's type
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
