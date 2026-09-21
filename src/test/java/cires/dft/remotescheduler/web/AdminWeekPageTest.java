package cires.dft.remotescheduler.web;

import cires.dft.remotescheduler.AbstractPostgresIntegrationTest;
import cires.dft.remotescheduler.service.ScheduleService;
import cires.dft.remotescheduler.service.WeekPlanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The grid an admin opens the morning a meeting is put in the diary. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "remote.job.enabled=false")
class AdminWeekPageTest extends AbstractPostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ScheduleService schedules;
    @Autowired private WeekPlanService weekPlans;

    @Test
    @DisplayName("an admin holds somebody in the office and the chart marks the cell")
    void pinsSomeoneOnSite() throws Exception {
        LocalDate week = LocalDate.of(2030, 3, 4);

        try {
            mvc.perform(post("/admin/week").with(admin()).with(csrf())
                            .param("week", week.toString())
                            .param("person", "Sara")
                            .param("onSite", "2"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attribute("message", "Saved for Sara."));

            assertThat(weekPlans.forWeek(week).onSiteFor("Sara")).containsExactly(2);

            mvc.perform(get("/admin/week").param("week", week.toString()).with(admin()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("bureau")));

            schedules.generate(week, true, "test");

            mvc.perform(get("/").param("week", week.toString()).with(admin()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("is-onsite")));

        } finally {
            clear(week, "Sara");
        }
    }

    @Test
    @DisplayName("a wish saved on the grid comes back ticked and is honoured when the week is planned")
    void gridRoundTripsAPreference() throws Exception {
        LocalDate week = LocalDate.of(2030, 3, 11);

        try {
            mvc.perform(post("/admin/week").with(admin()).with(csrf())
                            .param("week", week.toString())
                            .param("person", "Omar")
                            .param("preferred", "0")
                            .param("preferred", "3"))
                    .andExpect(flash().attribute("message", "Saved for Omar."));

            assertThat(weekPlans.forWeek(week).preferredFor("Omar")).containsExactly(0, 3);

            var schedule = schedules.generate(week, true, "test");
            assertThat(schedule.peopleByDayIndex().get(0)).contains("Omar");
            assertThat(schedule.peopleByDayIndex().get(3)).contains("Omar");

        } finally {
            clear(week, "Omar");
        }
    }

    @Test
    @DisplayName("holding somebody on a day they are already remote on names the week to re-roll")
    void warnsWhenTheWeekWasAlreadyPlanned() throws Exception {
        LocalDate week = LocalDate.of(2030, 3, 18);

        try {
            var schedule = schedules.generate(week, true, "test");
            int taken = schedule.remoteDaysPerPerson().isEmpty() ? 0
                    : firstRemoteDayOf(schedule.peopleByDayIndex(), "Adam");

            mvc.perform(post("/admin/week").with(admin()).with(csrf())
                            .param("week", week.toString())
                            .param("person", "Adam")
                            .param("onSite", String.valueOf(taken)))
                    .andExpect(flash().attribute("staleWeeks", hasItem(week)));

        } finally {
            clear(week, "Adam");
        }
    }

    @Test
    @DisplayName("holding somebody on a day they were not remote on says nothing about re-rolling")
    void doesNotWarnWhenNothingIsContradicted() throws Exception {
        LocalDate week = LocalDate.of(2030, 3, 25);

        try {
            var schedule = schedules.generate(week, true, "test");
            int free = firstOfficeDayOf(schedule.peopleByDayIndex(), "Ayoub");

            mvc.perform(post("/admin/week").with(admin()).with(csrf())
                            .param("week", week.toString())
                            .param("person", "Ayoub")
                            .param("onSite", String.valueOf(free)))
                    .andExpect(flash().attributeExists("message"))
                    .andExpect(flash().attributeCount(1));

        } finally {
            clear(week, "Ayoub");
        }
    }

    @Test
    @DisplayName("a pin that would leave the week unplannable is refused with the reason")
    void refusesAPinThatBreaksTheWeek() throws Exception {
        LocalDate week = LocalDate.of(2030, 6, 3);

        mvc.perform(post("/admin/week").with(admin()).with(csrf())
                        .param("week", week.toString())
                        .param("person", "Nassim")
                        .param("onSite", "0")
                        .param("onSite", "4"))
                .andExpect(flash().attribute("error", containsString("Nassim")));

        assertThat(weekPlans.forWeek(week).onSiteFor("Nassim")).isEmpty();
    }

    private int firstRemoteDayOf(java.util.Map<Integer, java.util.List<String>> byDay,
                                 String person) {
        return byDay.entrySet().stream()
                .filter(entry -> entry.getValue().contains(person))
                .map(java.util.Map.Entry::getKey)
                .findFirst()
                .orElseThrow();
    }

    private int firstOfficeDayOf(java.util.Map<Integer, java.util.List<String>> byDay,
                                 String person) {
        return byDay.entrySet().stream()
                .filter(entry -> !entry.getValue().contains(person))
                .map(java.util.Map.Entry::getKey)
                .findFirst()
                .orElseThrow();
    }

    private void clear(LocalDate week, String person) {
        weekPlans.replace(week, person, Set.of(), Set.of(), "test");
    }

    private static RequestPostProcessor admin() {
        return user("admin@cires.ma").roles("ADMIN");
    }
}
