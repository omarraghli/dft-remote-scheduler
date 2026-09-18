package cires.dft.remotescheduler.security;

import cires.dft.remotescheduler.config.SecurityProperties;
import cires.dft.remotescheduler.domain.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Lets a machine call the API with a shared secret instead of a login session — a scheduled
 * job elsewhere hitting the generate endpoint, say.
 *
 * <p>Off unless {@code remote.security.service-token} is set, and the comparison is
 * constant-time so a wrong token cannot be narrowed down by how long the answer takes.
 */
public class ServiceTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Service-Token";
    public static final String PRINCIPAL = "service-token";

    private final byte[] expected;

    public ServiceTokenFilter(SecurityProperties properties) {
        String token = properties.getServiceToken();
        this.expected = (token == null || token.isBlank())
                ? null
                : token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String presented = request.getHeader(HEADER);

        if (expected != null
                && presented != null
                && SecurityContextHolder.getContext().getAuthentication() == null
                && MessageDigest.isEqual(expected, presented.getBytes(StandardCharsets.UTF_8))) {

            var auth = new UsernamePasswordAuthenticationToken(
                    PRINCIPAL, null, List.of(new SimpleGrantedAuthority(Role.ADMIN.authority())));

            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        chain.doFilter(request, response);
    }
}
