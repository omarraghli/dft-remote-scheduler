package cires.dft.remotescheduler.security;

import cires.dft.remotescheduler.AbstractPostgresIntegrationTest;
import cires.dft.remotescheduler.domain.Role;
import cires.dft.remotescheduler.repository.AppUserRepository;
import cires.dft.remotescheduler.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "remote.security.service-token=test-token-123")
class AccessControlTest extends AbstractPostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserService userService;
    @Autowired private AppUserRepository users;

    @BeforeEach
    void reset() {
        users.deleteAll();
        userService.create("admin@cires.ma", Role.ADMIN, null);
        userService.create("reader@cires.ma", Role.USER, "Sara");
    }

    @Test
    @DisplayName("a signed-out visitor is sent to the login page, not the schedule")
    void anonymousIsRedirectedToLogin() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("the login page and the stylesheet are reachable signed out")
    void loginPageIsPublic() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/css/app.css")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a read-only user can see the schedule")
    void userCanRead() throws Exception {
        mvc.perform(get("/").with(user("reader@cires.ma").roles("USER")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a read-only user cannot generate, from the page or the API")
    void userCannotWrite() throws Exception {
        mvc.perform(post("/generate").with(user("reader@cires.ma").roles("USER")).with(csrf())
                        .param("week", "2026-09-21"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/schedules/generate").with(user("reader@cires.ma").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a read-only user cannot reach account management")
    void userCannotAdminister() throws Exception {
        mvc.perform(get("/admin/users").with(user("reader@cires.ma").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a read-only user cannot reach holiday management")
    void userCannotEditHolidays() throws Exception {
        mvc.perform(get("/admin/holidays").with(user("reader@cires.ma").roles("USER")))
                .andExpect(status().isForbidden());

        mvc.perform(post("/admin/holidays").with(user("reader@cires.ma").roles("USER")).with(csrf())
                        .param("name", "Invented").param("startDate", "2029-01-05"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an admin can reach account and holiday management, and generate")
    void adminCanAdministerAndWrite() throws Exception {
        mvc.perform(get("/admin/users").with(user("admin@cires.ma").roles("ADMIN")))
                .andExpect(status().isOk());

        mvc.perform(get("/admin/holidays").with(user("admin@cires.ma").roles("ADMIN")))
                .andExpect(status().isOk());

        mvc.perform(post("/generate").with(user("admin@cires.ma").roles("ADMIN")).with(csrf())
                        .param("week", "2026-09-21"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("a page POST without a CSRF token is refused")
    void csrfIsEnforcedOnPages() throws Exception {
        mvc.perform(post("/generate").with(user("admin@cires.ma").roles("ADMIN"))
                        .param("week", "2026-09-21"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the API answers 401 to an anonymous caller rather than redirecting it")
    void apiIsNotRedirected() throws Exception {
        mvc.perform(get("/api/schedules")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the service token authenticates a machine caller as an admin")
    void serviceTokenWorks() throws Exception {
        mvc.perform(post("/api/schedules/generate")
                        .header(ServiceTokenFilter.HEADER, "test-token-123")
                        .param("week", "2026-10-05"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("a wrong or missing service token gets nowhere")
    void wrongServiceTokenRejected() throws Exception {
        mvc.perform(post("/api/schedules/generate")
                        .header(ServiceTokenFilter.HEADER, "not-the-token"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/schedules/generate"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a read-only user cannot reach the week grid, or hold anyone in the office")
    void userCannotSetOnSiteDays() throws Exception {
        mvc.perform(get("/admin/week").with(user("reader@cires.ma").roles("USER")))
                .andExpect(status().isForbidden());

        mvc.perform(post("/admin/week").with(user("reader@cires.ma").roles("USER")).with(csrf())
                        .param("week", "2029-01-08")
                        .param("person", "Sara")
                        .param("onSite", "0"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a read-only user can still say which days they would rather be remote")
    void userCanSetTheirOwnPreferences() throws Exception {
        mvc.perform(post("/preferences").with(user("reader@cires.ma").roles("USER")).with(csrf())
                        .param("week", "2029-01-08")
                        .param("preferred", "0"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("a signed-out visitor cannot set preferences")
    void anonymousCannotSetPreferences() throws Exception {
        mvc.perform(post("/preferences").with(csrf())
                        .param("week", "2029-01-08")
                        .param("preferred", "0"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }
}
