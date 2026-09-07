package com.udap.employee.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.udap.employee.domain.AppUser;
import com.udap.employee.dto.AuthDtos;
import com.udap.employee.repository.AppUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private AppUserRepository users;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final JwtService jwtService = new JwtService(TestKeys.signingKey(), 1800L);

    private String knownCredential;
    private AuthenticationService service;
    private AppUser admin;

    @BeforeEach
    void setUp() {
        // Generated per test run so that no credential literal lives in the source.
        knownCredential = TestKeys.signingKey();

        service = new AuthenticationService(users, encoder, jwtService);
        admin = new AppUser();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setRole("ADMIN");
        admin.setPasswordHash(encoder.encode(knownCredential));
    }

    @Test
    void loginIssuesTokenForValidCredentials() {
        when(users.findByUsername("admin")).thenReturn(Optional.of(admin));

        final AuthDtos.LoginResponse response =
                service.login(new AuthDtos.LoginRequest("admin", knownCredential));

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(1800L);
        assertThat(jwtService.extractUsername(response.accessToken())).isEqualTo("admin");
        assertThat(jwtService.extractRole(response.accessToken())).isEqualTo("ADMIN");
    }

    @Test
    void loginRejectsUnknownUser() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new AuthDtos.LoginRequest("ghost", knownCredential)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginRejectsWrongPassword() {
        when(users.findByUsername("admin")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() ->
                service.login(new AuthDtos.LoginRequest("admin", TestKeys.signingKey())))
                .isInstanceOf(BadCredentialsException.class);
    }
}
