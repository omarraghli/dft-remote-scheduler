package cires.dft.remotescheduler.service;

import cires.dft.remotescheduler.config.SecurityProperties;
import cires.dft.remotescheduler.domain.AppUser;
import cires.dft.remotescheduler.domain.Role;
import cires.dft.remotescheduler.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Creates the first admin on an empty database, so there is someone to sign in as.
 *
 * <p>Runs only when the table has no accounts at all. If no password is configured one is
 * generated and printed to the log once — better than a default that is the same everywhere.
 * Either way it is temporary and has to be replaced at first sign-in.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties properties;
    private final Clock clock;

    public AdminBootstrap(AppUserRepository users,
                          PasswordEncoder passwordEncoder,
                          SecurityProperties properties,
                          Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) return;

        String email = AppUser.normaliseEmail(properties.getBootstrap().getEmail());
        String configured = properties.getBootstrap().getPassword();

        boolean generated = configured == null || configured.isBlank();
        String password = generated ? TemporaryPasswords.generate() : configured;

        users.save(new AppUser(email, passwordEncoder.encode(password), Role.ADMIN, null,
                clock.instant()));

        if (generated) {
            log.warn("""
                    
                    ┌─────────────────────────────────────────────────────────────┐
                     No accounts existed, so an admin was created:
                    
                       email    : {}
                       password : {}
                    
                     This password is shown once and must be changed at sign-in.
                     Set remote.security.bootstrap.password to choose your own.
                    └─────────────────────────────────────────────────────────────┘
                    """, email, password);
        } else {
            log.info("No accounts existed, so an admin was created for {} using the configured "
                    + "password. It must be changed at first sign-in.", email);
        }
    }
}
