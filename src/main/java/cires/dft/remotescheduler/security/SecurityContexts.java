package cires.dft.remotescheduler.security;

import cires.dft.remotescheduler.domain.AppUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Helpers for reading and refreshing whoever is signed in. */
public final class SecurityContexts {

    private SecurityContexts() {
    }

    /** The signed-in account, or null for an anonymous or service-token caller. */
    public static AppUserPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        return (auth != null && auth.getPrincipal() instanceof AppUserPrincipal principal)
                ? principal
                : null;
    }

    public static Long currentUserId() {
        AppUserPrincipal principal = currentPrincipal();
        return principal == null ? null : principal.getId();
    }

    /**
     * Swaps in a principal built from the updated account.
     *
     * <p>Needed after a password change: the must-change flag is carried on the principal, so
     * without this the filter would keep redirecting the person back to the change page they
     * just completed.
     */
    public static void refresh(AppUser user) {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current == null) return;

        AppUserPrincipal principal = new AppUserPrincipal(user);
        var updated = new UsernamePasswordAuthenticationToken(
                principal, current.getCredentials(), principal.getAuthorities());

        SecurityContextHolder.getContext().setAuthentication(updated);
    }
}
