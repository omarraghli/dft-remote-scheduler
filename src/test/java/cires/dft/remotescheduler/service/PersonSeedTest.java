package cires.dft.remotescheduler.service;

import cires.dft.remotescheduler.AbstractPostgresIntegrationTest;
import cires.dft.remotescheduler.TestAccounts;
import cires.dft.remotescheduler.domain.AppUser;
import cires.dft.remotescheduler.domain.Role;
import cires.dft.remotescheduler.repository.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The upgrade production goes through: accounts linked to roster names already exist, the person
 * table they are folded from was never filled, and so nobody is on the schedule when it starts.
 */
@SpringBootTest
class PersonSeedTest extends AbstractPostgresIntegrationTest {

    @Autowired private PersonSeed seed;
    @Autowired private UserService userService;
    @Autowired private RosterService roster;
    @Autowired private AppUserRepository users;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        TestAccounts.reset(jdbc);
    }

    @Test
    @DisplayName("existing accounts keep their passwords and join the schedule; the rest are listed")
    void upgradesExistingAccounts() {
        String temporary = userService.create("sara@cires.ma", Role.USER, "Sara");
        String chosen = userService.create("omar@cires.ma", Role.ADMIN, "Omar");
        userService.changeOwnPassword("omar@cires.ma", chosen, "omar-chose-this", "omar-chose-this");

        // What 007 leaves behind on a database whose person table was empty.
        jdbc.update("DELETE FROM app_user WHERE email IS NULL");
        jdbc.update("UPDATE app_user SET on_schedule = FALSE");
        assertThat(roster.activeNames()).isEmpty();

        seed.run(null);

        assertThat(roster.activeNames()).hasSize(16).contains("Sara", "Omar", "MedKhalil");
        assertThat(users.findAll().stream().filter(u -> "Sara".equals(u.getRosterName())))
                .hasSize(1);

        AppUser sara = users.findByEmail("sara@cires.ma").orElseThrow();
        assertThat(sara.isOnSchedule()).isTrue();
        // Still on the temporary password they were given, and still made to replace it.
        assertThat(sara.isMustChangePassword()).isTrue();
        assertThat(passwordEncoder.matches(temporary, sara.getPasswordHash())).isTrue();

        AppUser omar = users.findByEmail("omar@cires.ma").orElseThrow();
        assertThat(omar.getRole()).isEqualTo(Role.ADMIN);
        assertThat(passwordEncoder.matches("omar-chose-this", omar.getPasswordHash())).isTrue();

        assertThat(userService.unclaimedNames()).doesNotContain("Sara", "Omar").contains("Adam");
    }
}
