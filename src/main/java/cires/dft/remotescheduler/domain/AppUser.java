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

/** A login account. */
@Entity
@Table(name = "app_user",
        uniqueConstraints = @UniqueConstraint(name = "uk_app_user_email", columnNames = "email"))
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 190)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private Role role;

    /** Which person on the schedule roster this account is, if any. */
    @Column(name = "roster_name", length = 64)
    private String rosterName;

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
