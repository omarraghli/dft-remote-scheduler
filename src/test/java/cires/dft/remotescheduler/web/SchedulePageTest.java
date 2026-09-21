package cires.dft.remotescheduler.web;

import cires.dft.remotescheduler.AbstractPostgresIntegrationTest;
import cires.dft.remotescheduler.domain.AppUser;
import cires.dft.remotescheduler.domain.Role;
import cires.dft.remotescheduler.security.AppUserPrincipal;
import cires.dft.remotescheduler.service.ScheduleService;
import cires.dft.remotescheduler.service.WeekPlanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

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

/** Renders the page for real, so a broken expression in the template fails here. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "remote.job.enabled=false")
class SchedulePageTest extends AbstractPostgresIntegrationTest {

    /** Wednesday 18 November 2026 — Fête de l'Indépendance. */
    private static final LocalDate HOLIDAY_WEEK = LocalDate.of(2026, 11, 16);

    /** A week with no holiday in it, so what is on screen is only what the test put there. */
    private static final LocalDate QUIET_WEEK = LocalDate.of(2030, 3, 4);

    @Autowired private MockMvc mvc;
    @Autowired private ScheduleService scheduleService;
    @Autowired private WeekPlanService weekPlans;

    @Test
    @DisplayName("a generated week names its holiday and marks the column closed")
    void chartShowsTheHoliday() throws Exception {
        scheduleService.generate(HOLIDAY_WEEK, true, "test");

        mvc.perform(get("/").param("week", HOLIDAY_WEEK.toString())
                        .with(user("reader@cires.ma").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Indépendance")))
                .andExpect(content().string(containsString("Mercredi 18 nov.")))
                .andExpect(content().string(containsString("is-holiday")))
                .andExpect(content().string(containsString("short week")));
    }

    @Test
    @DisplayName("a week nobody has generated still shows the holidays coming in it")
    void emptyWeekShowsTheHoliday() throws Exception {
        // Thursday 20 and Friday 21 August 2026, with no schedule stored for that week.
        mvc.perform(get("/").param("week", "2026-08-17")
                        .with(user("reader@cires.ma").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Révolution du Roi et du Peuple")))
                .andExpect(content().string(containsString("Fête de la Jeunesse")));
    }

    @Test
    @DisplayName("an account linked to a roster name can pick its days, and they are stored")
    void aRosterLinkedUserPicksTheirDays() throws Exception {
        AppUserPrincipal account = principal("picker@cires.ma", "Rajae");

        try {
            mvc.perform(get("/").param("week", QUIET_WEEK.toString()).with(user(account)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("action=\"/preferences\"")))
                    .andExpect(content().string(containsString("A wish, not a booking")));

            mvc.perform(post("/preferences").with(user(account)).with(csrf())
                            .param("week", QUIET_WEEK.toString())
                            .param("preferred", "1")
                            .param("preferred", "3"))
                    .andExpect(status().is3xxRedirection());

            assertThat(weekPlans.forWeek(QUIET_WEEK).preferredFor("Rajae")).containsExactly(1, 3);

        } finally {
            weekPlans.replace(QUIET_WEEK, "Rajae", Set.of(), Set.of(), "test");
        }
    }

    @Test
    @DisplayName("an account linked to nobody is not asked which days it wants")
    void anUnlinkedAccountIsNotAsked() throws Exception {
        AppUserPrincipal account = principal("nobody@cires.ma", null);

        mvc.perform(get("/").param("week", QUIET_WEEK.toString()).with(user(account)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("action=\"/preferences\""))));

        mvc.perform(post("/preferences").with(user(account)).with(csrf())
                        .param("week", QUIET_WEEK.toString())
                        .param("preferred", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));

        assertThat(weekPlans.forWeek(QUIET_WEEK).preferred()).isEmpty();
    }

    /** The real principal, because the control keys off the roster name it carries. */
    private AppUserPrincipal principal(String email, String rosterName) {
        AppUser account = new AppUser(email, "irrelevant", Role.USER, rosterName, Instant.EPOCH);
        account.changePassword("irrelevant");
        return new AppUserPrincipal(account);
    }

    @Test
    @DisplayName("every page wears the same bar, marking the section you are on")
    void theBarIsTheSameEverywhere() throws Exception {
        assertBar("/", "href=\"/?week=" + QUIET_WEEK + "\" aria-current=\"page\"");
        assertBar("/admin/week", "href=\"/admin/week?week=" + QUIET_WEEK + "\" aria-current=\"page\"");
        assertBar("/admin/vacations", "href=\"/admin/vacations\" aria-current=\"page\"");
        assertBar("/admin/holidays", "href=\"/admin/holidays\" aria-current=\"page\"");
        assertBar("/admin/users", "href=\"/admin/users\" aria-current=\"page\"");
    }

    @Test
    @DisplayName("a read-only user is not offered the admin sections")
    void theBarHidesWhatAUserCannotOpen() throws Exception {
        mvc.perform(get("/").param("week", QUIET_WEEK.toString())
                        .with(user("reader@cires.ma").roles("USER")))
                .andExpect(content().string(containsString("data-theme-set")))
                .andExpect(content().string(not(containsString("/admin/week"))))
                .andExpect(content().string(not(containsString("/admin/vacations"))))
                .andExpect(content().string(not(containsString("/admin/users"))));
    }

    @Test
    @DisplayName("the week strip is on the two pages that have a week, and nowhere else")
    void theWeekStripFollowsTheWeek() throws Exception {
        assertStrip("/", true);
        assertStrip("/admin/week", true);
        assertStrip("/admin/vacations", false);
        assertStrip("/admin/holidays", false);
        assertStrip("/admin/users", false);
    }

    private void assertStrip(String path, boolean expected) throws Exception {
        var matcher = containsString("weekbar-strip");
        mvc.perform(get(path).param("week", QUIET_WEEK.toString())
                        .with(user("admin@cires.ma").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(expected ? matcher : not(matcher)));
    }

    /** The bar, the theme switch and the way out are on the page, and it knows where it is. */
    private void assertBar(String path, String activeLink) throws Exception {
        mvc.perform(get(path).param("week", QUIET_WEEK.toString())
                        .with(user("admin@cires.ma").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"bar\"")))
                .andExpect(content().string(containsString("data-theme-set")))
                .andExpect(content().string(containsString("href=\"/logout\"")))
                .andExpect(content().string(containsString(activeLink)));
    }
}
