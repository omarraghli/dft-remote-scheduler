package cires.dft.remotescheduler.service;

import cires.dft.remotescheduler.config.RemoteScheduleProperties;
import cires.dft.remotescheduler.domain.AppUser;
import cires.dft.remotescheduler.domain.Role;
import cires.dft.remotescheduler.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Accounts: creating them, resetting them, and the rules about what an admin may not do.
 *
 * <p>Those rules are the interesting part. Every change that could remove administration from
 * the application — deleting yourself, demoting the last admin, deactivating them — is refused
 * here rather than in a controller, so no route into the service can get around them.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private static final int MIN_PASSWORD_LENGTH = 10;

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final RemoteScheduleProperties scheduleProperties;
    private final Clock clock;

    public UserService(AppUserRepository users,
                       PasswordEncoder passwordEncoder,
                       RemoteScheduleProperties scheduleProperties,
                       Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.scheduleProperties = scheduleProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AppUser> findAll() {
        return users.findAllByOrderByEmailAsc();
    }

    @Transactional(readOnly = true)
    public AppUser require(Long id) {
        return users.findById(id)
                .orElseThrow(() -> new UserManagementException("No account with id " + id));
    }

    /**
     * Creates an account with a temporary password.
     *
     * @return the password in clear, the only time it exists in readable form — show it to the
     *         admin once and store only the hash
     */
    @Transactional
    public String create(String email, Role role, String rosterName) {
        String normalised = AppUser.normaliseEmail(email);

        if (normalised == null || normalised.isBlank()) {
            throw new UserManagementException("An email address is required");
        }
        if (!normalised.contains("@")) {
            throw new UserManagementException("\"" + email + "\" is not an email address");
        }
        if (users.existsByEmail(normalised)) {
            throw new UserManagementException(normalised + " already has an account");
        }

        String rosterOrNull = normaliseRosterName(rosterName);
        String temporary = TemporaryPasswords.generate();

        AppUser user = new AppUser(normalised, passwordEncoder.encode(temporary), role,
                rosterOrNull, clock.instant());
        users.save(user);

        log.info("Created {} account for {}{}", role, normalised,
                rosterOrNull == null ? "" : " (roster: " + rosterOrNull + ")");

        return temporary;
    }

    /** Issues a fresh temporary password, returned in clear for the admin to pass on. */
    @Transactional
    public String resetPassword(Long id) {
        AppUser user = require(id);
        String temporary = TemporaryPasswords.generate();

        user.assignTemporaryPassword(passwordEncoder.encode(temporary));
        users.save(user);

        log.info("Reset the password for {}", user.getEmail());
        return temporary;
    }

    /** The person choosing their own password, which clears the must-change flag. */
    @Transactional
    public void changeOwnPassword(String email, String current, String replacement,
                                  String confirmation) {

        AppUser user = users.findByEmail(AppUser.normaliseEmail(email))
                .orElseThrow(() -> new UserManagementException("No such account"));

        if (!passwordEncoder.matches(current, user.getPasswordHash())) {
            throw new UserManagementException("That is not your current password");
        }
        if (replacement == null || replacement.length() < MIN_PASSWORD_LENGTH) {
            throw new UserManagementException(
                    "The new password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (!replacement.equals(confirmation)) {
            throw new UserManagementException("The two new passwords do not match");
        }
        if (passwordEncoder.matches(replacement, user.getPasswordHash())) {
            throw new UserManagementException("The new password must be different from the old one");
        }

        user.changePassword(passwordEncoder.encode(replacement));
        users.save(user);

        log.info("{} changed their password", user.getEmail());
    }

    @Transactional
    public void setActive(Long id, boolean active, Long actingUserId) {
        AppUser user = require(id);

        if (!active) {
            refuseSelf(user, actingUserId, "deactivate your own account");
            refuseLosingLastAdmin(user, "Deactivating");
        }

        user.setActive(active);
        users.save(user);

        log.info("{} is now {}", user.getEmail(), active ? "active" : "deactivated");
    }

    @Transactional
    public void changeRole(Long id, Role role, Long actingUserId) {
        AppUser user = require(id);

        if (user.getRole() == role) return;

        if (role == Role.USER) {
            refuseSelf(user, actingUserId, "remove your own admin rights");
            refuseLosingLastAdmin(user, "Demoting");
        }

        user.setRole(role);
        users.save(user);

        log.info("{} is now {}", user.getEmail(), role);
    }

    @Transactional
    public void delete(Long id, Long actingUserId) {
        AppUser user = require(id);

        refuseSelf(user, actingUserId, "delete your own account");
        refuseLosingLastAdmin(user, "Deleting");

        users.delete(user);
        log.info("Deleted the account for {}", user.getEmail());
    }

    @Transactional
    public void recordSignIn(String email) {
        users.findByEmail(AppUser.normaliseEmail(email)).ifPresent(user -> {
            user.setLastLoginAt(clock.instant());
            users.save(user);
        });
    }

    private void refuseSelf(AppUser user, Long actingUserId, String what) {
        if (actingUserId != null && actingUserId.equals(user.getId())) {
            throw new UserManagementException("You cannot " + what);
        }
    }

    /**
     * The guard that matters: losing the last active admin would lock everyone out of account
     * management, with no way back in short of editing the database by hand.
     */
    private void refuseLosingLastAdmin(AppUser user, String verb) {
        if (user.getRole() == Role.ADMIN
                && user.isActive()
                && users.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {

            throw new UserManagementException(
                    verb + " " + user.getEmail() + " would leave no active admin. "
                            + "Promote someone else first.");
        }
    }

    /** Matches the given name against the configured roster, case-insensitively. */
    private String normaliseRosterName(String rosterName) {
        if (rosterName == null || rosterName.isBlank()) return null;

        return scheduleProperties.getPeople().stream()
                .filter(person -> person.equalsIgnoreCase(rosterName.trim()))
                .findFirst()
                .orElseThrow(() -> new UserManagementException(
                        "\"" + rosterName + "\" is not on the roster"));
    }
}
