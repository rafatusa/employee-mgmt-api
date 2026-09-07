package com.udap.employee.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Payload accepted when creating or updating an employee. */
public record EmployeeRequest(
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @NotBlank @Email @Size(max = 160) String email,
        @NotBlank @Size(max = 80) String department,
        @NotBlank @Size(max = 80) String position,
        @NotNull @DecimalMin("0.0") BigDecimal salary,
        @NotNull LocalDate hireDate) {
}
