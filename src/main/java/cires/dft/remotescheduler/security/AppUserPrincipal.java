package cires.dft.remotescheduler.security;

import cires.dft.remotescheduler.domain.AppUser;
import cires.dft.remotescheduler.domain.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * The signed-in account, as Spring Security sees it.
 *
 * <p>Carries the id, roster name and must-change flag alongside the credentials so the page and
 * the filters can read them without going back to the database on every request.
 */
public class AppUserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final Role role;
    private final String rosterName;
    private final boolean active;
    private final boolean mustChangePassword;

    public AppUserPrincipal(AppUser user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole();
        this.rosterName = user.getRosterName();
        this.active = user.isActive();
        this.mustChangePassword = user.isMustChangePassword();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        // A temporary password still signs you in — it just cannot go anywhere but the
        // change-password page, which PasswordChangeFilter enforces.
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    public Long getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    public String getRosterName() {
        return rosterName;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
