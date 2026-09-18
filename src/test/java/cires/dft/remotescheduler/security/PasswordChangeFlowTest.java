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
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PasswordChangeFlowTest extends AbstractPostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserService userService;
    @Autowired private AppUserRepository users;

    private String temporaryPassword;

    @BeforeEach
    void reset() {
        users.deleteAll();
        userService.create("keeper@cires.ma", Role.ADMIN, null);
        temporaryPassword = userService.create("new@cires.ma", Role.USER, "Omar");
    }

    @Test
    @DisplayName("the temporary password signs you in")
    void temporaryPasswordAuthenticates() throws Exception {
        mvc.perform(formLogin("/login").user("new@cires.ma").password(temporaryPassword))
                .andExpect(authenticated());
    }

    @Test
    @DisplayName("a wrong password does not")
    void wrongPasswordRejected() throws Exception {
        mvc.perform(formLogin("/login").user("new@cires.ma").password("nope"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("until it is replaced, every page redirects to the change form")
    void heldOnTheChangePageUntilReplaced() throws Exception {
        var session = mvc.perform(formLogin("/login").user("new@cires.ma").password(temporaryPassword))
                .andReturn().getRequest().getSession();

        mvc.perform(get("/").session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/password"));

        mvc.perform(get("/password").session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("once replaced, the rest of the app opens up")
    void releasedAfterChanging() throws Exception {
        var session = (org.springframework.mock.web.MockHttpSession)
                mvc.perform(formLogin("/login").user("new@cires.ma").password(temporaryPassword))
                        .andReturn().getRequest().getSession();

        mvc.perform(post("/password").session(session).with(csrf())
                        .param("currentPassword", temporaryPassword)
                        .param("newPassword", "my-own-secret-1")
                        .param("confirmPassword", "my-own-secret-1"))
                .andExpect(redirectedUrl("/"));

        mvc.perform(get("/").session(session)).andExpect(status().isOk());

        assertThat(users.findByEmail("new@cires.ma").orElseThrow().isMustChangePassword()).isFalse();
    }

    @Test
    @DisplayName("a deactivated account cannot sign in")
    void deactivatedCannotSignIn() throws Exception {
        Long id = users.findByEmail("new@cires.ma").orElseThrow().getId();
        Long adminId = users.findByEmail("keeper@cires.ma").orElseThrow().getId();
        userService.setActive(id, false, adminId);

        mvc.perform(formLogin("/login").user("new@cires.ma").password(temporaryPassword))
                .andExpect(unauthenticated());
    }
}
