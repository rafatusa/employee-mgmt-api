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
 * Sets the administrator password from the environment on every start.
 *
 * <p>The V2 migration seeds the administrator row with an unusable placeholder
 * rather than a real hash, so that no credential material is ever committed to
 * version control. This runner turns that locked row into a working account
 * using the supplied ADMIN_PASSWORD.
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
            LOG.warn("ADMIN_PASSWORD is not set: the '{}' account keeps its unusable placeholder "
                    + "hash and CANNOT be authenticated against. Set ADMIN_PASSWORD and restart "
                    + "to enable administrator login.", adminUsername);
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
