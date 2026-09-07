package com.udap.employee.dto;

import com.udap.employee.domain.Employee;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Representation of an employee returned by the API. */
public record EmployeeResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String department,
        String position,
        BigDecimal salary,
        LocalDate hireDate,
        Instant createdAt,
        Instant updatedAt) {

    public static EmployeeResponse from(final Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getFirstName(),
                employee.getLastName(),
                employee.getEmail(),
                employee.getDepartment(),
                employee.getPosition(),
                employee.getSalary(),
                employee.getHireDate(),
                employee.getCreatedAt(),
                employee.getUpdatedAt());
    }
}
