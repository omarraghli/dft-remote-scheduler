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
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Creates the first admin on an empty database, so there is someone to sign in as.
 *
 * <p>Runs only when nobody can sign in as an admin. If no password is configured one is
 * generated and printed to the log once — better than a default that is the same everywhere.
 * Either way it is temporary and has to be replaced at first sign-in.
 */
@Component
@Order(0)
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
        // Not "no accounts at all": the team is listed before anybody signs up, and a roster
        // of people without passwords is still nobody who can sign in.
        if (users.existsByRoleAndActiveTrueAndPasswordHashIsNotNull(Role.ADMIN)) return;

        String email = AppUser.normaliseEmail(properties.getBootstrap().getEmail());
        String configured = properties.getBootstrap().getPassword();

        if (users.existsByEmail(email)) {
            log.warn("No active admin can sign in, and {} already has an account, so none was "
                    + "created. Set remote.security.bootstrap.email to a free address.", email);
            return;
        }

        boolean generated = configured == null || configured.isBlank();
        String password = generated ? TemporaryPasswords.generate() : configured;

        users.save(new AppUser(email, passwordEncoder.encode(password), Role.ADMIN, null,
                clock.instant()));

        if (generated) {
            log.warn("""
                    
                    ┌─────────────────────────────────────────────────────────────┐
                     No admin could sign in, so one was created:
                    
                       email    : {}
                       password : {}
                    
                     This password is shown once and must be changed at sign-in.
                     Set remote.security.bootstrap.password to choose your own.
                    └─────────────────────────────────────────────────────────────┘
                    """, email, password);
        } else {
            log.info("No admin could sign in, so one was created for {} using the configured "
                    + "password. It must be changed at first sign-in.", email);
        }
    }
}
