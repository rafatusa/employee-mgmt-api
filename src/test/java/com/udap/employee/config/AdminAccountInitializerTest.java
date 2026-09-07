package com.udap.employee.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.udap.employee.domain.AppUser;
import com.udap.employee.repository.AppUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminAccountInitializerTest {

    @Mock
    private AppUserRepository users;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void doesNothingWhenPasswordNotConfigured() {
        new AdminAccountInitializer(users, encoder, "admin", "").run(null);

        verify(users, never()).save(any(AppUser.class));
    }

    @Test
    void updatesExistingAdminPassword() {
        final AppUser existing = new AppUser();
        existing.setId(1L);
        existing.setUsername("admin");
        existing.setRole("ADMIN");
        existing.setPasswordHash("stale");
        when(users.findByUsername("admin")).thenReturn(Optional.of(existing));

        new AdminAccountInitializer(users, encoder, "admin", "NewStrongPassword123").run(null);

        final ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(captor.capture());
        assertThat(encoder.matches("NewStrongPassword123", captor.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void createsAdminWhenMissing() {
        when(users.findByUsername("root")).thenReturn(Optional.empty());

        new AdminAccountInitializer(users, encoder, "root", "AnotherStrongPassword1").run(null);

        final ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("root");
        assertThat(captor.getValue().getRole()).isEqualTo("ADMIN");
    }
}
