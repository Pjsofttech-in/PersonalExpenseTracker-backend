package com.pjsofttech.expensetracker.custom_exceptions;

public class DuplicateCategoryException extends RuntimeException {
    public DuplicateCategoryException(String message) {
        super(message);
    }
}
