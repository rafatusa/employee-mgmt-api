package com.udap.employee.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DomainAccessorsTest {

    @Test
    void employeeAccessorsRoundTrip() {
        final Employee employee = new Employee();
        final Instant created = Instant.parse("2024-01-01T10:00:00Z");
        final Instant updated = Instant.parse("2024-02-01T10:00:00Z");

        employee.setId(3L);
        employee.setFirstName("Grace");
        employee.setLastName("Hopper");
        employee.setEmail("grace@example.com");
        employee.setDepartment("Research");
        employee.setPosition("Rear Admiral");
        employee.setSalary(new BigDecimal("210000.00"));
        employee.setHireDate(LocalDate.of(2019, 5, 20));
        employee.setCreatedAt(created);
        employee.setUpdatedAt(updated);

        assertThat(employee.getId()).isEqualTo(3L);
        assertThat(employee.getFirstName()).isEqualTo("Grace");
        assertThat(employee.getLastName()).isEqualTo("Hopper");
        assertThat(employee.getEmail()).isEqualTo("grace@example.com");
        assertThat(employee.getDepartment()).isEqualTo("Research");
        assertThat(employee.getPosition()).isEqualTo("Rear Admiral");
        assertThat(employee.getSalary()).isEqualByComparingTo("210000.00");
        assertThat(employee.getHireDate()).isEqualTo(LocalDate.of(2019, 5, 20));
        assertThat(employee.getCreatedAt()).isEqualTo(created);
        assertThat(employee.getUpdatedAt()).isEqualTo(updated);
    }

    @Test
    void appUserAccessorsRoundTrip() {
        final AppUser user = new AppUser();
        user.setId(9L);
        user.setUsername("auditor");
        user.setPasswordHash("hashed");
        user.setRole("USER");

        assertThat(user.getId()).isEqualTo(9L);
        assertThat(user.getUsername()).isEqualTo("auditor");
        assertThat(user.getPasswordHash()).isEqualTo("hashed");
        assertThat(user.getRole()).isEqualTo("USER");
    }
}
