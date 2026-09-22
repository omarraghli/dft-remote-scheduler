package cires.dft.remotescheduler;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Back to a clean slate between tests without losing the team: an account is a person, so
 * deleting every account would empty the roster the rest of the suite plans with. Accounts that
 * are nobody go; everybody on the team goes back to not having signed up, active and planned.
 * A test that adds somebody to the team removes them itself.
 */
public final class TestAccounts {

    private TestAccounts() {
    }

    public static void reset(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM app_user WHERE roster_name IS NULL");
        jdbc.update("""
                UPDATE app_user
                   SET email = NULL, password_hash = NULL, role = 'USER',
                       must_change_password = FALSE, last_login_at = NULL,
                       active = TRUE, on_schedule = TRUE
                """);
    }
}
