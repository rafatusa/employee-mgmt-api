package com.udap.employee.service;

/** Raised when an employee id does not exist. */
public class EmployeeNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EmployeeNotFoundException(final Long id) {
        super("Employee not found: " + id);
    }
}
