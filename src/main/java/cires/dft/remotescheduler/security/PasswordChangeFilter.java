package cires.dft.remotescheduler.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Holds an account on the change-password page until its temporary password is replaced.
 *
 * <p>Without this the flag would be advisory: someone handed a temporary password could simply
 * navigate away and keep using it indefinitely.
 */
public class PasswordChangeFilter extends OncePerRequestFilter {

    public static final String CHANGE_PATH = "/password";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal principal
                && principal.isMustChangePassword()
                && !isAllowedWhilePending(request)) {

            response.sendRedirect(request.getContextPath() + CHANGE_PATH);
            return;
        }

        chain.doFilter(request, response);
    }

    /** The change page itself, signing out, and the assets those two need. */
    private boolean isAllowedWhilePending(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());

        return path.equals(CHANGE_PATH)
                || path.equals("/logout")
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.equals("/favicon.ico");
    }
}
