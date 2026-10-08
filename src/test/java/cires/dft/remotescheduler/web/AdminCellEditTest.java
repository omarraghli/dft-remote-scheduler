package cires.dft.remotescheduler.web;

import cires.dft.remotescheduler.AbstractPostgresIntegrationTest;
import cires.dft.remotescheduler.domain.Vacation;
import cires.dft.remotescheduler.domain.WeekSchedule;
import cires.dft.remotescheduler.service.ScheduleService;
import cires.dft.remotescheduler.service.VacationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/** What an admin does from the right-click menu on the chart. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "remote.job.enabled=false")
class AdminCellEditTest extends AbstractPostgresIntegrationTest {

    private static final LocalDate WEEK = LocalDate.of(2030, 11, 11);

    @Autowired private MockMvc mvc;
    @Autowired private ScheduleService schedules;
    @Autowired private VacationService vacations;

    @AfterEach
    void removeLeave() {
        vacations.all().stream()
                .filter(v -> !v.getEndDate().isBefore(WEEK.minusWeeks(1))
                        && !v.getStartDate().isAfter(WEEK.plusWeeks(1)))
                .forEach(v -> vacations.delete(v.getId()));
    }

    @Test
    @DisplayName("emptying a remote cell takes that one day away and leaves the slot free")
    void emptiesACell() throws Exception {
        WeekSchedule before = schedules.generate(WEEK, true, "test");
        String person = before.peopleByDayIndex().get(0).getFirst();
        int total = before.getAssignments().size();

        mvc.perform(post("/admin/week/empty").with(admin()).with(csrf())
                        .param("week", WEEK.toString())
                        .param("person", person)
                        .param("day", "0"))
                .andExpect(redirectedUrl("/?week=" + WEEK))
                .andExpect(flash().attribute("message", containsString(person)));

        WeekSchedule after = schedules.findByWeek(WEEK).orElseThrow();
        assertThat(after.peopleByDayIndex().getOrDefault(0, List.of())).doesNotContain(person);
        assertThat(after.getAssignments()).hasSize(total - 1);
    }

    @Test
    @DisplayName("an office cell has nothing to empty, and nothing changes")
    void refusesAnOfficeCell() throws Exception {
        WeekSchedule before = schedules.generate(WEEK, true, "test");
        String person = before.remoteDaysPerPerson().keySet().iterator().next();
        int officeDay = officeDayOf(before, person);
        int total = before.getAssignments().size();

        mvc.perform(post("/admin/week/empty").with(admin()).with(csrf())
                        .param("week", WEEK.toString())
                        .param("person", person)
                        .param("day", String.valueOf(officeDay)))
                .andExpect(flash().attribute("error", containsString("is not remote")));

        assertThat(schedules.findByWeek(WEEK).orElseThrow().getAssignments()).hasSize(total);
    }

    @Test
    @DisplayName("marking somebody absent records the leave and takes them off the days it covers")
    void marksAbsent() throws Exception {
        WeekSchedule before = schedules.generate(WEEK, true, "test");
        String person = before.peopleByDayIndex().get(0).getFirst();

        mvc.perform(post("/admin/week/absent").with(admin()).with(csrf())
                        .param("week", WEEK.toString())
                        .param("person", person)
                        .param("from", WEEK.toString())
                        .param("to", WEEK.plusDays(4).toString()))
                .andExpect(flash().attribute("message", containsString("on leave")));

        assertThat(vacations.all()).anyMatch(v -> v.getPersonName().equals(person)
                && v.getStartDate().equals(WEEK) && v.getEndDate().equals(WEEK.plusDays(4)));
        assertThat(schedules.findByWeek(WEEK).orElseThrow().remoteDaysPerPerson())
                .doesNotContainKey(person);
        assertThat(schedules.leaveConflicts(WEEK))
                .noneMatch(c -> c.person().equals(person));

        mvc.perform(get("/").param("week", WEEK.toString()).with(admin()))
                .andExpect(content().string(containsString("cell-menu")));
    }

    @Test
    @DisplayName("absence right after leave already recorded widens it rather than adding a second")
    void extendsAdjacentLeave() throws Exception {
        schedules.generate(WEEK, true, "test");
        Vacation existing = vacations.add("Sara", WEEK, WEEK.plusDays(1));

        mvc.perform(post("/admin/week/absent").with(admin()).with(csrf())
                        .param("week", WEEK.toString())
                        .param("person", "Sara")
                        .param("from", WEEK.plusDays(2).toString())
                        .param("to", WEEK.plusDays(2).toString()))
                .andExpect(flash().attribute("message", containsString("on leave")));

        List<Vacation> saras = vacations.all().stream()
                .filter(v -> v.getPersonName().equals("Sara")
                        && !v.getStartDate().isAfter(WEEK.plusDays(4))
                        && !v.getEndDate().isBefore(WEEK))
                .toList();
        assertThat(saras).singleElement().satisfies(v -> {
            assertThat(v.getId()).isEqualTo(existing.getId());
            assertThat(v.getEndDate()).isEqualTo(WEEK.plusDays(2));
        });
    }

    @Test
    @DisplayName("absence over leave already recorded is refused and the week is untouched")
    void refusesOverlappingLeave() throws Exception {
        WeekSchedule before = schedules.generate(WEEK, true, "test");
        vacations.add("Sara", WEEK.plusDays(1), WEEK.plusDays(2));
        int total = before.getAssignments().size();

        mvc.perform(post("/admin/week/absent").with(admin()).with(csrf())
                        .param("week", WEEK.toString())
                        .param("person", "Sara")
                        .param("from", WEEK.toString())
                        .param("to", WEEK.plusDays(4).toString()))
                .andExpect(flash().attribute("error", containsString("already away")));

        assertThat(schedules.findByWeek(WEEK).orElseThrow().getAssignments()).hasSize(total);
    }

    private static int officeDayOf(WeekSchedule schedule, String person) {
        for (int day = 0; day < 5; day++) {
            if (!schedule.peopleByDayIndex().getOrDefault(day, List.of()).contains(person)) {
                return day;
            }
        }
        throw new AssertionError(person + " is remote every day");
    }

    private static RequestPostProcessor admin() {
        return user("admin@cires.ma").roles("ADMIN");
    }
}
