package cires.dft.remotescheduler.web;

import cires.dft.remotescheduler.AbstractPostgresIntegrationTest;
import cires.dft.remotescheduler.TestAccounts;
import cires.dft.remotescheduler.domain.AppUser;
import cires.dft.remotescheduler.domain.Role;
import cires.dft.remotescheduler.repository.AppUserRepository;
import cires.dft.remotescheduler.service.TeamInviteService;
import cires.dft.remotescheduler.service.UserManagementException;
import cires.dft.remotescheduler.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The team link: the whole of onboarding, with nobody sending anybody a password. */
@SpringBootTest
@AutoConfigureMockMvc
class JoinPageTest extends AbstractPostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private TeamInviteService invites;
    @Autowired private UserService userService;
    @Autowired private AppUserRepository users;
    @Autowired private JdbcTemplate jdbc;

    private String token;

    @BeforeEach
    void issue() {
        token = invites.issue("test");
    }

    @AfterEach
    void cleanUp() {
        TestAccounts.reset(jdbc);
        users.findByRosterNameIgnoreCase("Yasmine").ifPresent(users::delete);
        invites.revoke();
    }

    @Test
    @DisplayName("the link opens without signing in and offers the unclaimed names")
    void opensAnonymously() throws Exception {
        mvc.perform(get("/join/" + token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Join the team")))
                .andExpect(content().string(containsString(">MedKhalil<")))
                .andExpect(content().string(containsString("Omar Raghli")));
    }

    @Test
    @DisplayName("joining creates an ordinary account on the chosen name and signs them in")
    void joinsAndSignsIn() throws Exception {
        join("MedKhalil", "MedKhalil@CiresTechnologies.ma", "a-long-password")
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/leave"))
                .andExpect(authenticated().withUsername("medkhalil@cirestechnologies.ma"));

        AppUser user = users.findByEmail("medkhalil@cirestechnologies.ma").orElseThrow();
        // The person who was already listed, not a second MedKhalil.
        assertThat(users.findByRosterNameIgnoreCase("MedKhalil").map(AppUser::getId))
                .contains(user.getId());
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.getRosterName()).isEqualTo("MedKhalil");
        // They chose it themselves, so there is nothing to replace.
        assertThat(user.isMustChangePassword()).isFalse();

        // And the name is no longer on offer.
        mvc.perform(get("/join/" + token))
                .andExpect(content().string(not(containsString(">MedKhalil<"))));
    }

    @Test
    @DisplayName("an address outside the company domain is refused")
    void refusesAnotherDomain() throws Exception {
        join("MedKhalil", "medkhalil@gmail.com", "a-long-password")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("@cirestechnologies.ma")))
                .andExpect(unauthenticated());

        assertThat(users.findByEmail("medkhalil@gmail.com")).isEmpty();
    }

    @Test
    @DisplayName("a name somebody already claimed cannot be claimed twice")
    void refusesAClaimedName() throws Exception {
        join("MedKhalil", "medkhalil@cirestechnologies.ma", "a-long-password");

        join("MedKhalil", "someone.else@cirestechnologies.ma", "a-long-password")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("already has an account")));

        assertThat(users.findByEmail("someone.else@cirestechnologies.ma")).isEmpty();
    }

    @Test
    @DisplayName("a replaced or revoked link stops working")
    void refusesAStaleLink() throws Exception {
        String old = token;
        token = invites.issue("test");

        mvc.perform(get("/join/" + old))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Link expired")));

        invites.revoke();

        assertThatThrownBy(() -> userService.join(token, "Outman", null,
                "outman@cirestechnologies.ma", "a-long-password", "a-long-password"))
                .isInstanceOf(UserManagementException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("a short or mismatched password is refused")
    void refusesAWeakPassword() throws Exception {
        join("Outman", "outman@cirestechnologies.ma", "short")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("at least 10")));

        assertThat(users.findByEmail("outman@cirestechnologies.ma")).isEmpty();
    }

    @Test
    @DisplayName("somebody new who is not listed types their name and joins the schedule")
    void aNewcomerTypesTheirName() throws Exception {
        mvc.perform(post("/join/" + token).with(csrf())
                        .param("rosterName", "__new__")
                        .param("newName", "Yasmine")
                        .param("email", "yasmine@cirestechnologies.ma")
                        .param("password", "a-long-password")
                        .param("confirmPassword", "a-long-password"))
                .andExpect(redirectedUrl("/leave"))
                .andExpect(authenticated());

        AppUser yasmine = users.findByRosterNameIgnoreCase("Yasmine").orElseThrow();
        assertThat(yasmine.isOnSchedule()).isTrue();
        assertThat(yasmine.hasJoined()).isTrue();
    }

    private ResultActions join(String name, String email, String password) throws Exception {
        return mvc.perform(post("/join/" + token).with(csrf())
                .param("rosterName", name)
                .param("email", email)
                .param("password", password)
                .param("confirmPassword", password));
    }
}
