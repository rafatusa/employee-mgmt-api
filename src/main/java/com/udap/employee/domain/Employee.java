package com.udap.employee.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Persistent representation of an employee record. */
@Entity
@Table(name = "employees")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(name = "email", nullable = false, unique = true, length = 160)
    private String email;

    @Column(name = "department", nullable = false, length = 80)
    private String department;

    @Column(name = "position", nullable = false, length = 80)
    private String position;

    @Column(name = "salary", nullable = false, precision = 12, scale = 2)
    private BigDecimal salary;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(final Long value) {
        this.id = value;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(final String value) {
        this.firstName = value;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(final String value) {
        this.lastName = value;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(final String value) {
        this.email = value;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(final String value) {
        this.department = value;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(final String value) {
        this.position = value;
    }

    public BigDecimal getSalary() {
        return salary;
    }

    public void setSalary(final BigDecimal value) {
        this.salary = value;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public void setHireDate(final LocalDate value) {
        this.hireDate = value;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final Instant value) {
        this.createdAt = value;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(final Instant value) {
        this.updatedAt = value;
    }
}
