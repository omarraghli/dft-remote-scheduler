package cires.dft.remotescheduler.service;

import cires.dft.remotescheduler.AbstractPostgresIntegrationTest;
import cires.dft.remotescheduler.TestAccounts;
import cires.dft.remotescheduler.domain.AppUser;
import cires.dft.remotescheduler.domain.Role;
import cires.dft.remotescheduler.domain.Vacation;
import cires.dft.remotescheduler.repository.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class RosterServiceTest extends AbstractPostgresIntegrationTest {

    /** Clear of every holiday, configured or dated, and of every other test's weeks. */
    private static final LocalDate QUIET_WEEK = LocalDate.of(2031, 2, 10);

    @Autowired private RosterService roster;
    @Autowired private ScheduleService schedules;
    @Autowired private VacationService vacations;
    @Autowired private UserService userService;
    @Autowired private AppUserRepository users;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        TestAccounts.reset(jdbc);
    }

    @Test
    @DisplayName("the team is listed from remote.people as people who have not signed up")
    void seededFromConfiguration() {
        assertThat(roster.activeNames()).hasSize(16).contains("Sara", "MedKhalil");
        assertThat(userService.unclaimedNames()).contains("Sara");

        AppUser sara = person("Sara");
        assertThat(sara.hasJoined()).isFalse();
        assertThat(sara.isOnSchedule()).isTrue();
    }

    @Test
    @DisplayName("a name already on the team is refused, whatever its case")
    void refusesADuplicate() {
        assertThatThrownBy(() -> roster.add("  sARa "))
                .isInstanceOf(UserManagementException.class)
                .hasMessageContaining("already on the team");

        assertThatThrownBy(() -> roster.add("   "))
                .isInstanceOf(UserManagementException.class);
    }

    @Test
    @DisplayName("an admin handing out a password claims the person rather than adding a second one")
    void credentialsGoToTheExistingPerson() {
        userService.create("rajae@cires.ma", Role.USER, "rajae");

        AppUser rajae = users.findByEmail("rajae@cires.ma").orElseThrow();
        assertThat(rajae.getId()).isEqualTo(person("Rajae").getId());
        assertThat(roster.activeNames()).hasSize(16);

        assertThatThrownBy(() -> userService.create("other@cires.ma", Role.USER, "Rajae"))
                .isInstanceOf(UserManagementException.class)
                .hasMessageContaining("already linked");
    }

    @Test
    @DisplayName("somebody who left, or is taken off the schedule, is no longer planned")
    void leavingTakesThemOutOfTheSolver() {
        AppUser nader = person("Nader");
        userService.create("boss@cires.ma", Role.ADMIN, null);
        Long boss = users.findByEmail("boss@cires.ma").orElseThrow().getId();

        try {
            userService.setActive(nader.getId(), false, boss);
            assertThat(schedules.toSolverInput(QUIET_WEEK).people()).doesNotContain("Nader");
            assertThat(roster.activeName("nader")).isEmpty();

            userService.setActive(nader.getId(), true, boss);
            userService.setOnSchedule(nader.getId(), false);
            assertThat(schedules.toSolverInput(QUIET_WEEK).people()).doesNotContain("Nader");

        } finally {
            userService.setActive(nader.getId(), true, boss);
            userService.setOnSchedule(nader.getId(), true);
        }

        assertThat(schedules.toSolverInput(QUIET_WEEK).people()).contains("Nader");
    }

    @Test
    @DisplayName("only somebody with a name can be on the schedule")
    void scheduleNeedsAName() {
        userService.create("helper@cires.ma", Role.ADMIN, null);
        Long helper = users.findByEmail("helper@cires.ma").orElseThrow().getId();

        assertThatThrownBy(() -> userService.setOnSchedule(helper, true))
                .isInstanceOf(UserManagementException.class)
                .hasMessageContaining("name");
    }

    @Test
    @DisplayName("a rename follows the person into past weeks and their leave")
    void renameCascades() {
        AppUser adnan = person("Adnan");
        schedules.generate(QUIET_WEEK, true, "test");
        Vacation leave = vacations.add("Adnan", LocalDate.of(2031, 3, 3), LocalDate.of(2031, 3, 4));

        try {
            roster.rename(adnan.getId(), "Adnane");

            var perPerson = schedules.findByWeek(QUIET_WEEK).orElseThrow().remoteDaysPerPerson();
            assertThat(perPerson).containsKey("Adnane").doesNotContainKey("Adnan");
            assertThat(vacations.require(leave.getId()).getPersonName()).isEqualTo("Adnane");
            assertThat(roster.activeNames()).contains("Adnane").doesNotContain("Adnan");

        } finally {
            roster.rename(adnan.getId(), "Adnan");
            vacations.delete(leave.getId());
        }
    }

    @Test
    @DisplayName("renaming onto somebody else's name is refused")
    void renameRefusesACollision() {
        AppUser adam = person("Adam");

        assertThatThrownBy(() -> roster.rename(adam.getId(), "sara"))
                .isInstanceOf(UserManagementException.class);
        assertThat(roster.activeNames()).contains("Adam");
    }

    @Test
    @DisplayName("a planned week that has somebody remote while on leave is reported")
    void reportsLeaveLandingOnAPlannedWeek() {
        LocalDate week = LocalDate.of(2031, 2, 17);
        var schedule = schedules.generate(week, true, "test");
        String person = schedule.peopleByDayIndex().get(0).getFirst();

        assertThat(schedules.leaveConflicts(week)).noneMatch(c -> c.person().equals(person));

        Vacation leave = vacations.add(person, week, week);
        try {
            assertThat(schedules.leaveConflicts(week))
                    .contains(new ScheduleService.LeaveConflict(week, person));
            // A week behind the one asked from is not reported.
            assertThat(schedules.leaveConflicts(week.plusWeeks(1)))
                    .noneMatch(c -> c.week().equals(week));

        } finally {
            vacations.delete(leave.getId());
        }
    }

    private AppUser person(String name) {
        return users.findByRosterNameIgnoreCase(name).orElseThrow();
    }
}
