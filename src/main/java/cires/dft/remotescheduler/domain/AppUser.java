package cires.dft.remotescheduler.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Locale;

/**
 * Somebody: a member of the team, an admin, or both.
 *
 * <p>One row per person, whether or not they have signed up. Somebody on the roster who has not
 * joined yet has a name and no email or password — the join link fills those in — so the team
 * exists, and is planned, from the day it is listed rather than the day the last person gets
 * round to signing up.
 */
@Entity
@Table(name = "app_user",
        uniqueConstraints = @UniqueConstraint(name = "uk_app_user_email", columnNames = "email"))
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null until they join. */
    @Column(name = "email", length = 190)
    private String email;

    /** Null until they join. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private Role role;

    /** Their name as the schedule spells it. Every other table refers to them by it. */
    @Column(name = "roster_name", length = 64)
    private String rosterName;

    /** Planned for remote days. Off for an admin who is not part of the team. */
    @Column(name = "on_schedule", nullable = false)
    private boolean onSchedule;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /** Set whenever a temporary password is issued; cleared once they choose their own. */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected AppUser() {
        // for JPA
    }

    public AppUser(String email, String passwordHash, Role role, String rosterName,
                   Instant createdAt) {
        this.email = normaliseEmail(email);
        this.passwordHash = passwordHash;
        this.role = role;
        this.rosterName = rosterName;
        this.createdAt = createdAt;
    }

    /** Somebody on the team who has not signed up yet. */
    public static AppUser unjoined(String name, Instant createdAt) {
        AppUser person = new AppUser(null, null, Role.USER, name, createdAt);
        person.onSchedule = true;
        person.mustChangePassword = false;
        return person;
    }

    /** Whether they have signed up — an email and a password to sign in with. */
    public boolean hasJoined() {
        return email != null && passwordHash != null;
    }

    /** Signing up: their address and the password they chose themselves. */
    public void join(String email, String passwordHash) {
        this.email = normaliseEmail(email);
        this.passwordHash = passwordHash;
        this.mustChangePassword = false;
    }

    /**
     * Back to not joined, keeping the person — what fixes somebody signing up under the wrong
     * name, which frees it for its owner.
     */
    public void unjoin() {
        this.email = null;
        this.passwordHash = null;
        this.mustChangePassword = false;
        this.lastLoginAt = null;
    }

    /** An admin handing out a temporary password rather than the join link. */
    public void issueCredentials(String email, String temporaryHash) {
        this.email = normaliseEmail(email);
        assignTemporaryPassword(temporaryHash);
    }

    /** Emails are compared and stored lower-cased, so case can never split an account in two. */
    public static String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Issues a temporary password: usable once, then it has to be replaced. */
    public void assignTemporaryPassword(String hash) {
        this.passwordHash = hash;
        this.mustChangePassword = true;
    }

    /** The password the person chose themselves. */
    public void changePassword(String hash) {
        this.passwordHash = hash;
        this.mustChangePassword = false;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getRosterName() {
        return rosterName;
    }

    public void setRosterName(String rosterName) {
        this.rosterName = rosterName;
    }

    public boolean isOnSchedule() {
        return onSchedule;
    }

    public void setOnSchedule(boolean onSchedule) {
        this.onSchedule = onSchedule;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
}
