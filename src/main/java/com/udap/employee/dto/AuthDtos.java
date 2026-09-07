package com.udap.employee.dto;

import jakarta.validation.constraints.NotBlank;

/** Authentication request and response payloads. */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** Credentials submitted to obtain a token. */
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    /** Issued bearer token and its lifetime in seconds. */
    public record LoginResponse(String accessToken, String tokenType, long expiresIn) {
    }
}
