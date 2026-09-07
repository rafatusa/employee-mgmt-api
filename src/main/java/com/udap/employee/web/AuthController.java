package com.udap.employee.web;

import com.udap.employee.dto.AuthDtos;
import com.udap.employee.security.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authentication endpoints. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Obtain a JWT access token")
public class AuthController {

    private final AuthenticationService authenticationService;

    public AuthController(final AuthenticationService service) {
        this.authenticationService = service;
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange username and password for a bearer token")
    public AuthDtos.LoginResponse login(@Valid @RequestBody final AuthDtos.LoginRequest request) {
        return authenticationService.login(request);
    }
}
