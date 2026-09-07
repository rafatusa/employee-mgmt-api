package com.udap.employee.security;

import com.udap.employee.domain.AppUser;
import com.udap.employee.dto.AuthDtos;
import com.udap.employee.repository.AppUserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Verifies credentials and issues access tokens. */
@Service
public class AuthenticationService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthenticationService(
            final AppUserRepository userRepository,
            final PasswordEncoder encoder,
            final JwtService service) {
        this.users = userRepository;
        this.passwordEncoder = encoder;
        this.jwtService = service;
    }

    @Transactional(readOnly = true)
    public AuthDtos.LoginResponse login(final AuthDtos.LoginRequest request) {
        final AppUser user = users.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }
        final String token = jwtService.generateToken(user.getUsername(), user.getRole());
        return new AuthDtos.LoginResponse(token, "Bearer", jwtService.getTtlSeconds());
    }
}
