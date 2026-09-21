package cires.dft.remotescheduler.web;

import cires.dft.remotescheduler.AbstractPostgresIntegrationTest;
import cires.dft.remotescheduler.domain.Holiday;
import cires.dft.remotescheduler.service.HolidayService;
import cires.dft.remotescheduler.service.ScheduleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The screen an admin uses the evening an Aïd is announced. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "remote.job.enabled=false")
class AdminHolidayPageTest extends AbstractPostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private HolidayService holidays;
    @Autowired private ScheduleService schedules;

    @Test
    @DisplayName("an admin adds a two-day holiday and sees it listed and applied to the week")
    void addsAHoliday() throws Exception {
        mvc.perform(post("/admin/holidays").with(admin()).with(csrf())
                        .param("name", "Aïd de test")
                        .param("startDate", "2030-02-07")
                        .param("days", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", "Holiday added."));

        Holiday added = holidays.all().stream()
                .filter(h -> h.getName().equals("Aïd de test")).findFirst().orElseThrow();
        assertThat(added.getDays()).isEqualTo(2);

        try {
            mvc.perform(get("/admin/holidays").with(admin()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Aïd de test")));

            // Thursday 7 and Friday 8 February 2030 — the page shows it on a week nobody
            // has generated yet, which is the point of adding it early.
            mvc.perform(get("/").param("week", "2030-02-04").with(admin()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Aïd de test")));

        } finally {
            mvc.perform(post("/admin/holidays/" + added.getId() + "/delete")
                    .with(admin()).with(csrf()));
        }

        assertThat(holidays.all()).noneMatch(h -> h.getName().equals("Aïd de test"));
    }

    @Test
    @DisplayName("a holiday announced after the week was planned says which week to re-roll")
    void warnsAboutAnAlreadyPlannedWeek() throws Exception {
        schedules.generate(LocalDate.of(2029, 9, 17), true, "test");

        mvc.perform(post("/admin/holidays").with(admin()).with(csrf())
                        .param("name", "Annoncé tard")
                        .param("startDate", "2029-09-19")
                        .param("days", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("staleWeeks",
                        org.hamcrest.Matchers.hasItem(LocalDate.of(2029, 9, 17))));

        Holiday added = holidays.all().stream()
                .filter(h -> h.getName().equals("Annoncé tard")).findFirst().orElseThrow();

        mvc.perform(get("/admin/holidays").with(admin()))
                .andExpect(content().string(containsString("week already planned")));

        mvc.perform(post("/admin/holidays/" + added.getId() + "/delete")
                .with(admin()).with(csrf()));
    }

    @Test
    @DisplayName("entering the same holiday twice is refused with a message, not a duplicate row")
    void refusesADuplicate() throws Exception {
        mvc.perform(post("/admin/holidays").with(admin()).with(csrf())
                .param("name", "Once").param("startDate", "2030-04-02").param("days", "2"));

        Holiday first = holidays.all().stream()
                .filter(h -> h.getName().equals("Once")).findFirst().orElseThrow();

        mvc.perform(post("/admin/holidays").with(admin()).with(csrf())
                        .param("name", "Twice").param("startDate", "2030-04-03").param("days", "1"))
                .andExpect(flash().attribute("error", containsString("Once")));

        assertThat(holidays.all()).noneMatch(h -> h.getName().equals("Twice"));

        mvc.perform(post("/admin/holidays/" + first.getId() + "/delete").with(admin()).with(csrf()));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return user("admin@cires.ma").roles("ADMIN");
    }
}
