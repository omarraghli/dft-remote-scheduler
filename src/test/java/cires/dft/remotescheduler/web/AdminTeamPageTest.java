package cires.dft.remotescheduler.web;

import cires.dft.remotescheduler.AbstractPostgresIntegrationTest;
import cires.dft.remotescheduler.domain.AppUser;
import cires.dft.remotescheduler.repository.AppUserRepository;
import cires.dft.remotescheduler.service.RosterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** One list for the team and everybody who can sign in, because an account is a person. */
@SpringBootTest
@AutoConfigureMockMvc
class AdminTeamPageTest extends AbstractPostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private RosterService roster;
    @Autowired private AppUserRepository users;

    @Test
    @DisplayName("the team shows up on the page before anybody has signed up")
    void listsPeopleWhoHaveNotJoined() throws Exception {
        mvc.perform(get("/admin/users").with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"MedKhalil\"")))
                .andExpect(content().string(containsString("not signed up")));
    }

    @Test
    @DisplayName("a hire is added by name alone, is warned about capacity, and can leave again")
    void hireRenameAndLeave() throws Exception {
        mvc.perform(post("/admin/users").with(admin()).with(csrf())
                        .param("name", " Yasmine ")
                        .param("onSchedule", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("message"))
                .andExpect(flash().attributeCount(1));

        AppUser yasmine = users.findByRosterNameIgnoreCase("Yasmine").orElseThrow();
        assertThat(yasmine.hasJoined()).isFalse();

        try {
            // Seventeen people at three days is 51 against 50 slots: no week fits.
            mvc.perform(get("/admin/users").with(admin()))
                    .andExpect(content().string(containsString("No week can be")));

            mvc.perform(post("/admin/users/" + yasmine.getId() + "/rename")
                            .with(admin()).with(csrf()).param("name", "Yasmina"))
                    .andExpect(flash().attributeExists("message"));
            assertThat(roster.activeName("yasmina")).contains("Yasmina");

            mvc.perform(post("/admin/users/" + yasmine.getId() + "/active")
                            .with(admin()).with(csrf()).param("active", "false"))
                    .andExpect(flash().attributeExists("message"));
            assertThat(roster.activeNames()).doesNotContain("Yasmina").hasSize(16);

            mvc.perform(get("/admin/users").with(admin()))
                    .andExpect(content().string(not(containsString("No week can be"))));

        } finally {
            users.deleteById(yasmine.getId());
        }
    }

    @Test
    @DisplayName("a name and an email together issue a temporary password to that person")
    void nameAndEmailIssueAPassword() throws Exception {
        mvc.perform(post("/admin/users").with(admin()).with(csrf())
                        .param("name", "Walid")
                        .param("email", "walid@cires.ma")
                        .param("onSchedule", "true"))
                .andExpect(flash().attributeExists("issuedPassword"));

        AppUser walid = users.findByEmail("walid@cires.ma").orElseThrow();
        try {
            assertThat(walid.getRosterName()).isEqualTo("Walid");
            assertThat(walid.isMustChangePassword()).isTrue();

            mvc.perform(post("/admin/users/" + walid.getId() + "/unjoin").with(admin()).with(csrf()))
                    .andExpect(flash().attributeExists("message"));
            assertThat(users.findById(walid.getId()).orElseThrow().hasJoined()).isFalse();

        } finally {
            users.deleteById(walid.getId());
        }
    }

    @Test
    @DisplayName("issuing a join link shows it once")
    void issuesAJoinLink() throws Exception {
        mvc.perform(post("/admin/users/invite").with(admin()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("inviteLink", containsString("/join/")));

        mvc.perform(post("/admin/users/invite/revoke").with(admin()).with(csrf()))
                .andExpect(flash().attributeExists("message"));
    }

    @Test
    @DisplayName("only admins can open it")
    void adminOnly() throws Exception {
        mvc.perform(get("/admin/users").with(user("reader@cires.ma").roles("USER")))
                .andExpect(status().isForbidden());
    }

    private static RequestPostProcessor admin() {
        return user("admin@cires.ma").roles("ADMIN");
    }
}
