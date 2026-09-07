package com.udap.employee.service;

/** Raised when an employee email is already registered. */
public class DuplicateEmailException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DuplicateEmailException(final String email) {
        super("Email already registered: " + email);
    }
}
