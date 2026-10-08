package cires.dft.remotescheduler.web;

import cires.dft.remotescheduler.AbstractPostgresIntegrationTest;
import cires.dft.remotescheduler.domain.AppUser;
import cires.dft.remotescheduler.domain.Role;
import cires.dft.remotescheduler.domain.WeekSchedule;
import cires.dft.remotescheduler.security.AppUserPrincipal;
import cires.dft.remotescheduler.service.ScheduleService;
import cires.dft.remotescheduler.service.VacationService;
import cires.dft.remotescheduler.service.WeekPlanService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;

/** Anybody marking the days they will be in the office, without an admin. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "remote.job.enabled=false")
class OwnOnSiteTest extends AbstractPostgresIntegrationTest {

    /** Clear of every holiday. */
    private static final LocalDate WEEK = LocalDate.of(2030, 6, 10);

    private static final String EMAIL = "rajae@cires.ma";
    private static final String ME = "Rajae";

    @Autowired private MockMvc mvc;
    @Autowired private WeekPlanService weekPlans;
    @Autowired private ScheduleService schedules;
    @Autowired private VacationService vacations;

    @AfterEach
    void cleanUp() {
        weekPlans.replace(WEEK, ME, Set.of(), Set.of(), "test");
        schedules.findByWeek(WEEK).ifPresent(s -> schedules.generate(WEEK, true, "test"));
        vacations.all().stream()
                .filter(v -> v.getPersonName().equals(ME) && !v.getStartDate().isBefore(WEEK)
                        && !v.getStartDate().isAfter(WEEK.plusDays(6)))
                .forEach(v -> vacations.delete(v.getId()));
    }

    @Test
    @DisplayName("a person marks their own office day, recorded as set by them")
    void marksTheirOwnDay() throws Exception {
        save("site", "1", "preferred", "3")
                .andExpect(flash().attribute("message", containsString("Saved for")));

        assertThat(weekPlans.forWeek(WEEK).onSiteFor(ME)).containsExactly(1);
        assertThat(weekPlans.forWeek(WEEK).preferredFor(ME)).containsExactly(3);
        assertThat(weekPlans.onSiteSetBy(ME, WEEK)).containsEntry(1, EMAIL);
    }

    @Test
    @DisplayName("a day ticked both ways is an office day")
    void officeWinsOverRemote() throws Exception {
        save("site", "1", "preferred", "1");

        assertThat(weekPlans.forWeek(WEEK).onSiteFor(ME)).containsExactly(1);
        assertThat(weekPlans.forWeek(WEEK).preferredFor(ME)).doesNotContain(1);
    }

    @Test
    @DisplayName("a day an admin required stays required, and the card is locked")
    void keepsAnAdminsPin() throws Exception {
        schedules.setWeekPlan(WEEK, ME, Set.of(), Set.of(2), "admin@cires.ma");

        save("site", "1");

        assertThat(weekPlans.forWeek(WEEK).onSiteFor(ME)).containsExactly(1, 2);

        mvc.perform(get("/").param("week", WEEK.toString()).with(user(account())))
                .andExpect(content().string(containsString("required by an admin")));
    }

    @Test
    @DisplayName("on a planned week, a remote day marked Office comes off at once")
    void plannedWeekGivesTheDayBack() throws Exception {
        WeekSchedule week = schedules.generate(WEEK, true, "test");
        int remoteDay = week.peopleByDayIndex().entrySet().stream()
                .filter(e -> e.getValue().contains(ME))
                .map(e -> e.getKey()).findFirst().orElseThrow();

        save("site", String.valueOf(remoteDay))
                .andExpect(flash().attribute("message", containsString("now off your schedule")));

        assertThat(schedules.findByWeek(WEEK).orElseThrow().peopleByDayIndex()
                .getOrDefault(remoteDay, List.of())).doesNotContain(ME);
    }

    @Test
    @DisplayName("Office on Lundi and Vendredi leaves a run of three, and is refused whole")
    void refusesAnImpossibleWeek() throws Exception {
        save("site", "0", "site", "4", "preferred", "2")
                .andExpect(flash().attribute("error", containsString("Office on Lundi and Vendredi")));

        assertThat(weekPlans.forWeek(WEEK).onSiteFor(ME)).isEmpty();
        assertThat(weekPlans.hasOwnWishes(ME, WEEK)).isFalse();
    }

    @Test
    @DisplayName("office on a day of leave is refused")
    void refusesADayAway() throws Exception {
        vacations.add(ME, WEEK.plusDays(2), WEEK.plusDays(2));

        save("site", "2")
                .andExpect(flash().attribute("error", containsString("on leave")));

        assertThat(weekPlans.forWeek(WEEK).onSiteFor(ME)).isEmpty();
    }

    @Test
    @DisplayName("office on a public holiday is refused")
    void refusesAHoliday() throws Exception {
        // Wednesday 18 November 2026 — Fête de l'Indépendance.
        LocalDate holidayWeek = LocalDate.of(2026, 11, 16);

        mvc.perform(post("/preferences").with(user(account())).with(csrf())
                        .param("week", holidayWeek.toString())
                        .param("site", "2"))
                .andExpect(flash().attribute("error", containsString("office is closed")));
    }

    private org.springframework.test.web.servlet.ResultActions save(String... pairs)
            throws Exception {
        var request = post("/preferences").with(user(account())).with(csrf())
                .param("week", WEEK.toString());
        for (int i = 0; i < pairs.length; i += 2) request.param(pairs[i], pairs[i + 1]);
        return mvc.perform(request);
    }

    private static AppUserPrincipal account() {
        AppUser account = new AppUser(EMAIL, "irrelevant", Role.USER, ME, Instant.EPOCH);
        account.changePassword("irrelevant");
        return new AppUserPrincipal(account);
    }
}
