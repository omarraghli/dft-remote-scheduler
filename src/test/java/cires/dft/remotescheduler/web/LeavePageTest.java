package cires.dft.remotescheduler.web;

import cires.dft.remotescheduler.AbstractPostgresIntegrationTest;
import cires.dft.remotescheduler.TestAccounts;
import cires.dft.remotescheduler.domain.AppUser;
import cires.dft.remotescheduler.domain.Vacation;
import cires.dft.remotescheduler.repository.AppUserRepository;
import cires.dft.remotescheduler.security.AppUserPrincipal;
import cires.dft.remotescheduler.service.ScheduleService;
import cires.dft.remotescheduler.service.VacationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

/** People declaring their own leave, and everybody seeing everybody's. */
@SpringBootTest
@AutoConfigureMockMvc
class LeavePageTest extends AbstractPostgresIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private VacationService vacations;
    @Autowired private ScheduleService schedules;
    @Autowired private AppUserRepository users;
    @Autowired private JdbcTemplate jdbc;

    private AppUserPrincipal hamza;

    @BeforeEach
    void account() {
        AppUser account = users.findByRosterNameIgnoreCase("Hamza").orElseThrow();
        account.join("hamza.leave@cires.ma", "irrelevant");
        hamza = new AppUserPrincipal(users.save(account));
    }

    @AfterEach
    void cleanUp() {
        vacations.all().stream()
                .filter(v -> v.getPersonName().equals("Hamza") || v.getPersonName().equals("Nassim"))
                .forEach(v -> vacations.delete(v.getId()));
        TestAccounts.reset(jdbc);
    }

    @Test
    @DisplayName("a user declares, moves and removes their own leave")
    void ownLifecycle() throws Exception {
        LocalDate start = schedules.today().plusDays(14);

        mvc.perform(post("/leave").with(user(hamza)).with(csrf())
                        .param("startDate", start.toString())
                        .param("endDate", start.plusDays(2).toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", "Leave added."));

        Vacation mine = only("Hamza");
        assertThat(mine.days()).isEqualTo(3);

        mvc.perform(post("/leave/" + mine.getId()).with(user(hamza)).with(csrf())
                        .param("startDate", start.toString())
                        .param("endDate", start.toString()))
                .andExpect(flash().attribute("message", "Leave updated."));
        assertThat(vacations.require(mine.getId()).days()).isEqualTo(1);

        mvc.perform(get("/leave").with(user(hamza)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your leave")))
                .andExpect(content().string(containsString("leave-cal")))
                .andExpect(content().string(containsString("lc-day away")));

        mvc.perform(post("/leave/" + mine.getId() + "/delete").with(user(hamza)).with(csrf()))
                .andExpect(flash().attribute("message", "Leave removed."));
        assertThat(vacations.all()).noneMatch(v -> v.getId().equals(mine.getId()));
    }

    @Test
    @DisplayName("somebody else's leave cannot be touched, whatever id is posted")
    void cannotTouchSomeoneElses() throws Exception {
        LocalDate start = schedules.today().plusDays(21);
        Vacation theirs = vacations.add("Nassim", start, start.plusDays(1));

        mvc.perform(post("/leave/" + theirs.getId()).with(user(hamza)).with(csrf())
                        .param("startDate", start.toString())
                        .param("endDate", start.plusDays(9).toString()))
                .andExpect(flash().attribute("error", "That leave is not yours."));

        mvc.perform(post("/leave/" + theirs.getId() + "/delete").with(user(hamza)).with(csrf()))
                .andExpect(flash().attribute("error", "That leave is not yours."));

        assertThat(vacations.require(theirs.getId()).days()).isEqualTo(2);
    }

    @Test
    @DisplayName("leave cannot be backdated by the person taking it")
    void refusesThePast() throws Exception {
        LocalDate yesterday = schedules.today().minusDays(1);

        mvc.perform(post("/leave").with(user(hamza)).with(csrf())
                        .param("startDate", yesterday.toString())
                        .param("endDate", yesterday.plusDays(3).toString()))
                .andExpect(flash().attributeExists("error"));

        assertThat(vacations.all()).noneMatch(v -> v.getPersonName().equals("Hamza"));
    }

    @Test
    @DisplayName("declaring leave says who else is off over the same days")
    void warnsAboutOverlap() throws Exception {
        LocalDate start = schedules.today().plusDays(28);
        vacations.add("Nassim", start.plusDays(1), start.plusDays(4));

        mvc.perform(post("/leave").with(user(hamza)).with(csrf())
                        .param("startDate", start.toString())
                        .param("endDate", start.plusDays(2).toString()))
                .andExpect(flash().attribute("message", "Leave added."))
                .andExpect(flash().attribute("overlap", containsString("Nassim")));
    }

    @Test
    @DisplayName("everyone can see the team calendar, linked or not")
    void everyoneSeesTheCalendar() throws Exception {
        mvc.perform(get("/leave").with(user("reader@cires.ma").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("leave-cal")))
                .andExpect(content().string(containsString("not on the schedule")))
                .andExpect(content().string(containsString(">Sara<")));

        mvc.perform(post("/leave").with(user("reader@cires.ma").roles("USER")).with(csrf())
                        .param("startDate", schedules.today().plusDays(3).toString())
                        .param("endDate", schedules.today().plusDays(3).toString()))
                .andExpect(flash().attributeExists("error"));
    }

    private Vacation only(String person) {
        var matching = vacations.all().stream()
                .filter(v -> v.getPersonName().equals(person))
                .toList();
        assertThat(matching).hasSize(1);
        return matching.getFirst();
    }
}
