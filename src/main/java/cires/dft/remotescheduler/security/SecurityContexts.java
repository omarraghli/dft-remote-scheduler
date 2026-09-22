package cires.dft.remotescheduler.security;

import cires.dft.remotescheduler.domain.AppUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Helpers for reading and refreshing whoever is signed in. */
public final class SecurityContexts {

    private static final SecurityContextRepository SESSIONS =
            new HttpSessionSecurityContextRepository();

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

    /**
     * Signs somebody in without a login form — the end of the join page, where they have just
     * chosen their password and asking for it again would be pointless.
     */
    public static void signIn(AppUser user, HttpServletRequest request,
                              HttpServletResponse response) {

        // A new session id, as a form login would issue, so a session planted before sign-in
        // is not the one that ends up authenticated.
        if (request.getSession(false) != null) request.changeSessionId();

        AppUserPrincipal principal = new AppUserPrincipal(user);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));

        SecurityContextHolder.setContext(context);
        SESSIONS.saveContext(context, request, response);
    }
}
