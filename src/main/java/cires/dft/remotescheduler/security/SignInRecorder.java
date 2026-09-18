package cires.dft.remotescheduler.security;

import cires.dft.remotescheduler.service.UserService;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Stamps the account each time someone signs in, which is what the accounts page shows as
 * "last seen" — the cheapest way for an admin to spot an account nobody is using.
 */
@Component
public class SignInRecorder {

    private final UserService userService;

    public SignInRecorder(UserService userService) {
        this.userService = userService;
    }

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        // Service-token callers are not accounts and have nothing to stamp.
        if (event.getAuthentication().getPrincipal() instanceof AppUserPrincipal principal) {
            userService.recordSignIn(principal.getUsername());
        }
    }
}
