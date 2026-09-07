package com.udap.employee.config;

import com.udap.employee.domain.AppUser;
import com.udap.employee.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Aligns the seeded administrator account with the password supplied through the
 * environment so that no usable credential is ever committed to the repository.
 */
@Component
@Profile("!test")
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;

    public AdminAccountInitializer(
            final AppUserRepository userRepository,
            final PasswordEncoder encoder,
            @Value("${app.admin.username:admin}") final String username,
            @Value("${app.admin.password:}") final String password) {
        this.users = userRepository;
        this.passwordEncoder = encoder;
        this.adminUsername = username;
        this.adminPassword = password;
    }

    @Override
    public void run(final ApplicationArguments args) {
        if (adminPassword == null || adminPassword.isBlank()) {
            LOG.warn("app.admin.password is not set; leaving the seeded administrator hash untouched");
            return;
        }
        final AppUser user = users.findByUsername(adminUsername).orElseGet(() -> {
            final AppUser created = new AppUser();
            created.setUsername(adminUsername);
            created.setRole("ADMIN");
            return created;
        });
        user.setPasswordHash(passwordEncoder.encode(adminPassword));
        users.save(user);
        LOG.info("Administrator account '{}' synchronised from configuration", adminUsername);
    }
}
