package cires.dft.remotescheduler.service;

import cires.dft.remotescheduler.domain.AppUser;
import cires.dft.remotescheduler.domain.Role;
import cires.dft.remotescheduler.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;
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
    private final RosterService roster;
    private final TeamInviteService invites;
    private final Clock clock;

    public UserService(AppUserRepository users,
                       PasswordEncoder passwordEncoder,
                       RosterService roster,
                       TeamInviteService invites,
                       Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.roster = roster;
        this.invites = invites;
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

    /** Everybody, the team first by name, then anyone who is only an account. */
    @Transactional(readOnly = true)
    public List<AppUser> findAllByName() {
        return users.findAll().stream()
                .sorted(Comparator.comparing((AppUser u) -> u.getRosterName() == null)
                        .thenComparing(u -> u.getRosterName() == null ? "" : u.getRosterName(),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(u -> u.getEmail() == null ? "" : u.getEmail()))
                .toList();
    }

    /**
     * Gives somebody a way to sign in with a temporary password, for when the join link will not
     * do. With a name, it is that person on the roster getting their credentials; without one it
     * is an account nobody plans for, such as a second admin.
     *
     * @return the password in clear, the only time it exists in readable form — show it to the
     *         admin once and store only the hash
     */
    @Transactional
    public String create(String email, Role role, String rosterName) {
        String normalised = requireFreeEmail(email);
        String temporary = TemporaryPasswords.generate();
        String hash = passwordEncoder.encode(temporary);

        if (rosterName == null || rosterName.isBlank()) {
            users.save(new AppUser(normalised, hash, role, null, clock.instant()));
            log.info("Created {} account for {}", role, normalised);
            return temporary;
        }

        AppUser person = users.findByRosterNameIgnoreCase(rosterName.trim())
                .orElseThrow(() -> new UserManagementException(
                        "\"" + rosterName + "\" is not on the roster"));
        if (person.hasJoined()) {
            throw new UserManagementException(
                    person.getRosterName() + " is already linked to " + person.getEmail());
        }

        person.issueCredentials(normalised, hash);
        person.setRole(role);
        users.save(person);

        log.info("Issued a temporary password to {} ({}) as {}", person.getRosterName(),
                normalised, role);
        return temporary;
    }

    /**
     * The Équipe page's one Add form: somebody on the team, an account, or both.
     *
     * <ul>
     *   <li>a name alone lists a person who has not signed up — they claim it with the join link</li>
     *   <li>a name and an email also issues them a temporary password</li>
     *   <li>an email alone is an account nobody plans for, such as a second admin</li>
     * </ul>
     *
     * @return the temporary password in clear when one was issued, otherwise null
     */
    @Transactional
    public String add(String name, String email, Role role, boolean onSchedule) {
        boolean hasName = name != null && !name.isBlank();
        boolean hasEmail = email != null && !email.isBlank();

        if (!hasName && !hasEmail) {
            throw new UserManagementException("Give a name, an email, or both.");
        }
        if (!hasName) {
            return create(email, role, null);
        }

        AppUser person = roster.add(name);
        person.setOnSchedule(onSchedule);
        person.setRole(role);

        return hasEmail ? create(email, role, person.getRosterName()) : null;
    }

    /**
     * Somebody joining through the team link: they pick their own name from the roster — or type
     * it, if they are new — and their own password, so nobody has to send them anything. The
     * link only proves they were in the chat it was posted to; the domain check is what keeps a
     * forwarded copy harmless.
     *
     * @param rosterName the name picked from the list, when {@code newName} is blank
     * @param newName    a name typed by somebody who is not on the list yet
     * @throws UserManagementException if the link, the address, the name or the password is
     *                                 not acceptable, in which case nothing is changed
     */
    @Transactional
    public AppUser join(String token, String rosterName, String newName, String email,
                        String password, String confirmation) {

        invites.requireValid(token);

        String normalised = AppUser.normaliseEmail(email);
        if (normalised == null || !normalised.matches("[^@\\s]+@[^@\\s]+")) {
            throw new UserManagementException("That is not an email address.");
        }

        String domain = invites.allowedDomain();
        if (!domain.isEmpty() && !normalised.endsWith("@" + domain)) {
            throw new UserManagementException("Use your @" + domain + " address.");
        }
        if (users.existsByEmail(normalised)) {
            throw new UserManagementException(
                    normalised + " already has an account — sign in instead.");
        }

        requireAcceptablePassword(password, confirmation);
        String hash = passwordEncoder.encode(password);

        AppUser person;
        if (newName != null && !newName.isBlank()) {
            person = AppUser.unjoined(roster.requireUsableName(newName, null), clock.instant());
        } else if (rosterName == null || rosterName.isBlank()) {
            throw new UserManagementException("Pick your name from the list.");
        } else {
            person = users.lockByRosterName(rosterName.trim())
                    .filter(p -> p.isActive() && p.isOnSchedule())
                    .orElseThrow(() -> new UserManagementException(
                            "\"" + rosterName + "\" is not on the roster."));
            if (person.hasJoined()) {
                throw new UserManagementException(person.getRosterName()
                        + " already has an account. Pick your own name, or ask an admin.");
            }
        }

        person.join(normalised, hash);

        try {
            users.saveAndFlush(person);
        } catch (DataIntegrityViolationException e) {
            // Somebody else typed the same new name, or used the same address, a moment earlier.
            throw new UserManagementException(
                    person.getRosterName() + " was just taken. Pick your own name, or ask an admin.");
        }

        log.info("{} joined as {}", normalised, person.getRosterName());
        return person;
    }

    /** The people on the team who have not signed up — what the join page offers. */
    @Transactional(readOnly = true)
    public List<String> unclaimedNames() {
        return users.findByActiveTrueAndOnScheduleTrue().stream()
                .filter(user -> !user.hasJoined())
                .map(AppUser::getRosterName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    /**
     * Takes back a sign-up, keeping the person: their email and password go, and the name can be
     * claimed again. The fix for somebody who picked the wrong name on the join page.
     */
    @Transactional
    public void unjoin(Long id, Long actingUserId) {
        AppUser user = require(id);

        if (user.getRosterName() == null) {
            throw new UserManagementException(
                    "That account is not anybody on the team — delete it instead.");
        }
        refuseSelf(user, actingUserId, "undo your own sign-up");
        refuseLosingLastAdmin(user, "Resetting");

        String email = user.getEmail();
        user.unjoin();
        users.save(user);

        log.info("{} is no longer {}; the name can be claimed again", email, user.getRosterName());
    }

    /** Whether the schedule plans for them. Only somebody with a name can be on it. */
    @Transactional
    public void setOnSchedule(Long id, boolean onSchedule) {
        AppUser user = require(id);

        if (onSchedule && user.getRosterName() == null) {
            throw new UserManagementException("Give them a name first — the schedule lists people "
                    + "by name.");
        }

        user.setOnSchedule(onSchedule);
        users.save(user);

        log.info("{} is {} the schedule", label(user), onSchedule ? "on" : "off");
    }

    /** Issues a fresh temporary password, returned in clear for the admin to pass on. */
    @Transactional
    public String resetPassword(Long id) {
        AppUser user = require(id);
        if (user.getEmail() == null) {
            throw new UserManagementException(label(user) + " has not joined yet — send them the "
                    + "join link.");
        }
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
        requireAcceptablePassword(replacement, confirmation);
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

        log.info("{} is now {}", label(user), active ? "active" : "deactivated");
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

        log.info("{} is now {}", label(user), role);
    }

    @Transactional
    public void delete(Long id, Long actingUserId) {
        AppUser user = require(id);

        refuseSelf(user, actingUserId, "delete your own account");
        refuseLosingLastAdmin(user, "Deleting");

        users.delete(user);
        log.info("Deleted {}", label(user));
    }

    @Transactional
    public void recordSignIn(String email) {
        users.findByEmail(AppUser.normaliseEmail(email)).ifPresent(user -> {
            user.setLastLoginAt(clock.instant());
            users.save(user);
        });
    }

    private void requireAcceptablePassword(String password, String confirmation) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new UserManagementException(
                    "The new password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (!password.equals(confirmation)) {
            throw new UserManagementException("The two new passwords do not match");
        }
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
                    verb + " " + label(user) + " would leave no active admin. "
                            + "Promote someone else first.");
        }
    }

    private String requireFreeEmail(String email) {
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
        return normalised;
    }

    private static String label(AppUser user) {
        return user.getRosterName() != null ? user.getRosterName() : user.getEmail();
    }
}
