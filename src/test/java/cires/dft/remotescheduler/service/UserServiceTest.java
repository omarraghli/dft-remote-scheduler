package cires.dft.remotescheduler.service;

import cires.dft.remotescheduler.AbstractPostgresIntegrationTest;
import cires.dft.remotescheduler.TestAccounts;
import cires.dft.remotescheduler.domain.AppUser;
import cires.dft.remotescheduler.domain.Role;
import cires.dft.remotescheduler.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class UserServiceTest extends AbstractPostgresIntegrationTest {

    @Autowired private UserService userService;
    @Autowired private AppUserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long adminId;

    @BeforeEach
    void reset() {
        TestAccounts.reset(jdbc);
        userService.create("boss@cires.ma", Role.ADMIN, null);
        adminId = users.findByEmail("boss@cires.ma").orElseThrow().getId();
    }

    @Test
    @DisplayName("a new account gets a temporary password it must replace")
    void createsWithTemporaryPassword() {
        String temporary = userService.create("sara@cires.ma", Role.USER, "Sara");

        AppUser user = users.findByEmail("sara@cires.ma").orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.getRosterName()).isEqualTo("Sara");
        assertThat(user.isActive()).isTrue();
        assertThat(user.isMustChangePassword()).isTrue();

        // stored hashed, and the returned clear text is what actually works
        assertThat(user.getPasswordHash()).isNotEqualTo(temporary);
        assertThat(passwordEncoder.matches(temporary, user.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("email case and spacing never split one person into two accounts")
    void normalisesEmail() {
        userService.create("  Sara@CIRES.ma  ", Role.USER, null);

        assertThat(users.findByEmail("sara@cires.ma")).isPresent();
        assertThatThrownBy(() -> userService.create("SARA@cires.ma", Role.USER, null))
                .isInstanceOf(UserManagementException.class)
                .hasMessageContaining("already has an account");
    }

    @Test
    @DisplayName("the roster name must be someone actually on the roster")
    void rejectsUnknownRosterName() {
        assertThatThrownBy(() -> userService.create("x@cires.ma", Role.USER, "Nobody"))
                .isInstanceOf(UserManagementException.class)
                .hasMessageContaining("not on the roster");

        // and it is matched case-insensitively against the configured spelling
        userService.create("medali@cires.ma", Role.USER, "medali");
        assertThat(users.findByEmail("medali@cires.ma").orElseThrow().getRosterName())
                .isEqualTo("MedAli");
    }

    @Test
    @DisplayName("changing your own password clears the must-change flag")
    void changeOwnPassword() {
        String temporary = userService.create("omar@cires.ma", Role.USER, "Omar");
        userService.changeOwnPassword("omar@cires.ma", temporary, "a-longer-secret", "a-longer-secret");

        AppUser user = users.findByEmail("omar@cires.ma").orElseThrow();
        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(passwordEncoder.matches("a-longer-secret", user.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("a password change is refused on a wrong current, a short new, or a mismatch")
    void changeOwnPasswordValidation() {
        String temporary = userService.create("omar@cires.ma", Role.USER, null);

        assertThatThrownBy(() -> userService.changeOwnPassword(
                "omar@cires.ma", "wrong", "a-longer-secret", "a-longer-secret"))
                .hasMessageContaining("not your current password");

        assertThatThrownBy(() -> userService.changeOwnPassword(
                "omar@cires.ma", temporary, "short", "short"))
                .hasMessageContaining("at least 10");

        assertThatThrownBy(() -> userService.changeOwnPassword(
                "omar@cires.ma", temporary, "a-longer-secret", "a-different-one"))
                .hasMessageContaining("do not match");

        assertThatThrownBy(() -> userService.changeOwnPassword(
                "omar@cires.ma", temporary, temporary, temporary))
                .hasMessageContaining("different from the old one");
    }

    @Test
    @DisplayName("resetting issues a working new temporary password")
    void resetPassword() {
        String first = userService.create("omar@cires.ma", Role.USER, null);
        userService.changeOwnPassword("omar@cires.ma", first, "chosen-by-them", "chosen-by-them");

        String reissued = userService.resetPassword(
                users.findByEmail("omar@cires.ma").orElseThrow().getId());

        AppUser user = users.findByEmail("omar@cires.ma").orElseThrow();
        assertThat(user.isMustChangePassword()).isTrue();
        assertThat(passwordEncoder.matches(reissued, user.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("chosen-by-them", user.getPasswordHash())).isFalse();
    }

    @Test
    @DisplayName("you cannot deactivate, demote or delete yourself")
    void refusesActingOnSelf() {
        assertThatThrownBy(() -> userService.setActive(adminId, false, adminId))
                .hasMessageContaining("your own account");
        assertThatThrownBy(() -> userService.changeRole(adminId, Role.USER, adminId))
                .hasMessageContaining("your own admin rights");
        assertThatThrownBy(() -> userService.delete(adminId, adminId))
                .hasMessageContaining("your own account");
    }

    @Test
    @DisplayName("the last active admin cannot be removed by any route")
    void refusesLosingTheLastAdmin() {
        // acting as somebody else, so the self-guard is not what is being tested
        Long other = 999_999L;

        assertThatThrownBy(() -> userService.setActive(adminId, false, other))
                .hasMessageContaining("no active admin");
        assertThatThrownBy(() -> userService.changeRole(adminId, Role.USER, other))
                .hasMessageContaining("no active admin");
        assertThatThrownBy(() -> userService.delete(adminId, other))
                .hasMessageContaining("no active admin");
    }

    @Test
    @DisplayName("with a second admin, the first can be removed")
    void allowsRemovalOnceAnotherAdminExists() {
        userService.create("second@cires.ma", Role.ADMIN, null);
        Long secondId = users.findByEmail("second@cires.ma").orElseThrow().getId();

        userService.delete(adminId, secondId);

        assertThat(users.findByEmail("boss@cires.ma")).isEmpty();
        assertThat(users.countByRoleAndActiveTrue(Role.ADMIN)).isEqualTo(1);
    }

    @Test
    @DisplayName("a deactivated admin no longer counts towards the last-admin guard")
    void deactivatedAdminDoesNotCount() {
        userService.create("second@cires.ma", Role.ADMIN, null);
        Long secondId = users.findByEmail("second@cires.ma").orElseThrow().getId();

        userService.setActive(secondId, false, adminId);

        assertThatThrownBy(() -> userService.delete(adminId, secondId))
                .hasMessageContaining("no active admin");
    }
}
