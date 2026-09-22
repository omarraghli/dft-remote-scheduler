package cires.dft.remotescheduler.config;

import cires.dft.remotescheduler.domain.Role;
import cires.dft.remotescheduler.security.PasswordChangeFilter;
import cires.dft.remotescheduler.security.ServiceTokenFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final SecurityProperties properties;

    public SecurityConfig(SecurityProperties properties) {
        this.properties = properties;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * The API. Stateless, authenticated by the service token or by an existing session, and
     * answering 401/403 rather than redirecting a machine to a login page.
     *
     * <p>CSRF is off here because there is no cookie-driven write to forge: a caller must
     * present the token header explicitly. It stays on for the pages below.
     */
    @Bean
    @org.springframework.core.annotation.Order(1)
    public SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**")
            .csrf(csrf -> csrf.disable())
            .addFilterBefore(new ServiceTokenFilter(properties),
                    UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.POST, "/api/**").hasRole(Role.ADMIN.name())
                    .anyRequest().authenticated())
            .exceptionHandling(e -> e
                    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .httpBasic(basic -> {});

        return http.build();
    }

    /** The pages. Form login, session-backed, CSRF on. */
    @Bean
    @org.springframework.core.annotation.Order(2)
    public SecurityFilterChain pageChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/login", "/join/**", "/css/**", "/favicon.ico", "/error")
                            .permitAll()
                    .requestMatchers("/admin/**").hasRole(Role.ADMIN.name())
                    .requestMatchers(HttpMethod.POST, "/generate").hasRole(Role.ADMIN.name())
                    .anyRequest().authenticated())
            .formLogin(form -> form
                    .loginPage("/login")
                    .defaultSuccessUrl("/", false)
                    .failureUrl("/login?error")
                    .permitAll())
            .logout(out -> out
                    .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                    .logoutSuccessUrl("/login?logout")
                    .permitAll())
            // Runs after authentication so it can see who is signed in.
            .addFilterAfter(new PasswordChangeFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
