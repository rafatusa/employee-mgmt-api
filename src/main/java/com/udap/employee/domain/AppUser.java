package com.udap.employee.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Application user used for JWT authentication. */
@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 80)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 120)
    private String passwordHash;

    @Column(name = "role", nullable = false, length = 32)
    private String role;

    public Long getId() {
        return id;
    }

    public void setId(final Long value) {
        this.id = value;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(final String value) {
        this.username = value;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(final String value) {
        this.passwordHash = value;
    }

    public String getRole() {
        return role;
    }

    public void setRole(final String value) {
        this.role = value;
    }
}
