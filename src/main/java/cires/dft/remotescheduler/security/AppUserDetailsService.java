package cires.dft.remotescheduler.security;

import cires.dft.remotescheduler.domain.AppUser;
import cires.dft.remotescheduler.repository.AppUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public AppUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AppUser user = users.findByEmail(AppUser.normaliseEmail(email))
                .filter(AppUser::hasJoined)
                // Deliberately vague: whether an address has an account is not something an
                // unauthenticated caller should be able to probe.
                .orElseThrow(() -> new UsernameNotFoundException("Bad credentials"));

        return new AppUserPrincipal(user);
    }
}
