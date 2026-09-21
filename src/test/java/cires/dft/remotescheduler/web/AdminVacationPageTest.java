package cires.dft.remotescheduler.web;

import cires.dft.remotescheduler.AbstractPostgresIntegrationTest;
import cires.dft.remotescheduler.domain.Vacation;
import cires.dft.remotescheduler.service.ScheduleService;
import cires.dft.remotescheduler.service.VacationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

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

/** The page an admin uses the day somebody's leave is approved. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "remote.job.enabled=false")
class AdminVacationPageTest extends AbstractPostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private VacationService vacations;
    @Autowired private ScheduleService schedules;

    @Test
    @DisplayName("an admin records leave and the chart marks the days away")
    void recordsLeave() throws Exception {
        LocalDate week = LocalDate.of(2030, 9, 2);

        mvc.perform(post("/admin/vacations").with(admin()).with(csrf())
                        .param("person", "Sara")
                        .param("startDate", "2030-09-02")
                        .param("endDate", "2030-09-04"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", "Leave added."));

        Vacation added = vacations.all().stream()
                .filter(v -> v.getStartDate().equals(LocalDate.of(2030, 9, 2)))
                .findFirst().orElseThrow();

        try {
            mvc.perform(get("/admin/vacations").with(admin()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("3 days")));

            schedules.generate(week, true, "test");

            mvc.perform(get("/").param("week", week.toString()).with(admin()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("is-away")))
                    .andExpect(content().string(containsString("congé")));

            assertThat(schedules.findByWeek(week).orElseThrow().remoteDaysPerPerson())
                    .containsEntry("Sara", 1);

        } finally {
            mvc.perform(post("/admin/vacations/" + added.getId() + "/delete")
                    .with(admin()).with(csrf()));
        }

        assertThat(vacations.all()).noneMatch(v -> v.getId().equals(added.getId()));
    }

    @Test
    @DisplayName("leave landing on a week already planned names the week to re-roll")
    void warnsAboutAPlannedWeek() throws Exception {
        LocalDate week = LocalDate.of(2030, 9, 9);
        var schedule = schedules.generate(week, true, "test");
        String remoteOnMonday = schedule.peopleByDayIndex().get(0).getFirst();

        mvc.perform(post("/admin/vacations").with(admin()).with(csrf())
                        .param("person", remoteOnMonday)
                        .param("startDate", week.toString())
                        .param("endDate", week.toString()))
                .andExpect(flash().attribute("staleWeek", week))
                .andExpect(flash().attribute("stalePerson", remoteOnMonday));

        Vacation added = vacations.all().stream()
                .filter(v -> v.getStartDate().equals(week)).findFirst().orElseThrow();
        vacations.delete(added.getId());
    }

    @Test
    @DisplayName("leave that ends before it starts is refused on the page too")
    void refusesNonsense() throws Exception {
        mvc.perform(post("/admin/vacations").with(admin()).with(csrf())
                        .param("person", "Omar")
                        .param("startDate", "2030-10-09")
                        .param("endDate", "2030-10-02"))
                .andExpect(flash().attribute("error", containsString("before it starts")));
    }

    private static RequestPostProcessor admin() {
        return user("admin@cires.ma").roles("ADMIN");
    }
}
