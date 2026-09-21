package cires.dft.remotescheduler.service;

import cires.dft.remotescheduler.AbstractPostgresIntegrationTest;
import cires.dft.remotescheduler.domain.WeekSchedule;
import cires.dft.remotescheduler.solver.NoFeasibleScheduleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = "remote.job.enabled=false")
class ScheduleServiceTest extends AbstractPostgresIntegrationTest {

    private static final LocalDate WEEK = LocalDate.of(2026, 9, 21);

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private WeekPlanService weekPlanService;

    @Autowired
    private VacationService vacationService;

    @Test
    @DisplayName("a generated week is persisted with every assignment")
    void generatesAndPersists() {
        WeekSchedule schedule = scheduleService.generate(WEEK, true, "test");

        assertThat(schedule.getId()).isNotNull();
        assertThat(schedule.getWeekStart()).isEqualTo(WEEK);
        assertThat(schedule.getAssignments()).hasSize(48);

        WeekSchedule reloaded = scheduleService.findByWeek(WEEK).orElseThrow();
        assertThat(reloaded.getAssignments()).hasSize(48);
        assertThat(reloaded.remoteDaysPerPerson()).hasSize(16);
        assertThat(reloaded.remoteDaysPerPerson().values()).allMatch(count -> count == 3);
    }

    @Test
    @DisplayName("any day of the target week resolves to the same stored Monday")
    void normalisesTheWeek() {
        scheduleService.generate(LocalDate.of(2026, 10, 7), true, "test");

        WeekSchedule schedule = scheduleService.findByWeek(LocalDate.of(2026, 10, 9)).orElseThrow();
        assertThat(schedule.getWeekStart()).isEqualTo(LocalDate.of(2026, 10, 5));
    }

    @Test
    @DisplayName("generating twice is refused unless replace is asked for")
    void refusesToOverwriteByDefault() {
        LocalDate week = LocalDate.of(2026, 11, 2);
        scheduleService.generate(week, true, "test");

        assertThatThrownBy(() -> scheduleService.generate(week, false, "test"))
                .isInstanceOf(ScheduleAlreadyExistsException.class);
    }

    @Test
    @DisplayName("replacing leaves exactly one schedule, with no orphaned assignments")
    void replaceSwapsTheWeekCleanly() {
        LocalDate week = LocalDate.of(2026, 11, 9);

        scheduleService.generate(week, true, "first");
        WeekSchedule second = scheduleService.generate(week, true, "second");

        assertThat(second.getGeneratedBy()).isEqualTo("second");
        assertThat(second.getAssignments()).hasSize(48);

        List<WeekSchedule> forThatWeek = scheduleService.findAll().stream()
                .filter(s -> s.getWeekStart().equals(week))
                .toList();

        assertThat(forThatWeek).hasSize(1);
    }

    @Test
    @DisplayName("the configuration is translated into solver input correctly")
    void mapsConfigurationToSolverInput() {
        var input = scheduleService.toSolverInput(WEEK);

        assertThat(input.people()).hasSize(16).contains("MedKhalil", "Outman");
        assertThat(input.dayNames()).containsExactly(
                "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi");
        assertThat(input.slotsPerDay()).containsExactly(10, 10, 10, 10, 10);
        assertThat(input.remotesPerPerson()).isEqualTo(3);
        assertThat(input.maxConsecutiveDays()).isEqualTo(2);
        assertThat(input.requiredPersonDays()).isEqualTo(48);
        assertThat(input.availableSlots()).isEqualTo(50);
    }

    @Test
    @DisplayName("a public holiday closes its column and drops the week to two remote days each")
    void oneHolidayShortensTheWeek() {
        // Wednesday 18 November 2026 — Fête de l'Indépendance.
        LocalDate week = LocalDate.of(2026, 11, 16);

        var input = scheduleService.toSolverInput(week);
        assertThat(input.holidays()).containsExactly(2);
        assertThat(input.remotesPerPerson()).isEqualTo(2);

        WeekSchedule schedule = scheduleService.generate(week, true, "test");

        assertThat(schedule.getAssignments()).hasSize(32);
        assertThat(schedule.remoteDaysPerPerson().values()).allMatch(count -> count == 2);
        assertThat(schedule.peopleByDayIndex().get(2)).isNull();
    }

    @Test
    @DisplayName("two holidays in one week drop it to a single remote day each")
    void twoHolidaysLeaveOneRemoteDay() {
        // Thursday 20 and Friday 21 August 2026.
        LocalDate week = LocalDate.of(2026, 8, 17);

        var input = scheduleService.toSolverInput(week);
        assertThat(input.holidays()).containsExactlyInAnyOrder(3, 4);
        assertThat(input.remotesPerPerson()).isEqualTo(1);

        WeekSchedule schedule = scheduleService.generate(week, true, "test");

        assertThat(schedule.getAssignments()).hasSize(16);
        assertThat(schedule.remoteDaysPerPerson().values()).allMatch(count -> count == 1);
    }

    @Test
    @DisplayName("a day somebody is held in the office on reaches the solver as a blocked day")
    void anOnSiteDayIsBlockedForThatPerson() {
        LocalDate week = LocalDate.of(2029, 3, 5);

        try {
            scheduleService.setWeekPlan(week, "Sara", Set.of(), Set.of(2), "test");

            assertThat(scheduleService.toSolverInput(week).forbiddenDays())
                    .containsEntry("Sara", Set.of(2));

            WeekSchedule schedule = scheduleService.generate(week, true, "test");
            assertThat(schedule.peopleByDayIndex().get(2)).doesNotContain("Sara");
            assertThat(schedule.remoteDaysPerPerson()).containsEntry("Sara", 3);

        } finally {
            scheduleService.setWeekPlan(week, "Sara", Set.of(), Set.of(), "test");
        }
    }

    @Test
    @DisplayName("a day somebody asked for reaches the solver as a wish, and is granted")
    void aPreferenceIsCarriedThrough() {
        LocalDate week = LocalDate.of(2029, 3, 12);

        try {
            scheduleService.setWeekPlan(week, "Omar", Set.of(0, 3), Set.of(), "test");

            assertThat(scheduleService.toSolverInput(week).preferredDays())
                    .containsEntry("Omar", Set.of(0, 3));

            WeekSchedule schedule = scheduleService.generate(week, true, "test");
            assertThat(schedule.peopleByDayIndex().get(0)).contains("Omar");
            assertThat(schedule.peopleByDayIndex().get(3)).contains("Omar");

        } finally {
            scheduleService.setWeekPlan(week, "Omar", Set.of(), Set.of(), "test");
        }
    }

    @Test
    @DisplayName("holding one person in the office on Lundi and Vendredi is refused, and nothing is stored")
    void aPinThatLeavesNoWeekIsRefused() {
        LocalDate week = LocalDate.of(2029, 3, 19);

        assertThatThrownBy(() ->
                scheduleService.setWeekPlan(week, "Nader", Set.of(), Set.of(0, 4), "test"))
                .isInstanceOf(WeekPlanException.class)
                .hasMessageContaining("Nader");

        assertThat(weekPlanService.forWeek(week).onSiteFor("Nader")).isEmpty();
    }

    @Test
    @DisplayName("a week that could not be planned already does not blame the next change made to it")
    void anAlreadyBrokenWeekDoesNotBlameTheNextChange() {
        LocalDate week = LocalDate.of(2029, 3, 26);

        try {
            // Straight to the store, so the week is unplannable before anybody touches it again.
            weekPlanService.replace(week, "Hamza", Set.of(), Set.of(0, 4), "test");
            assertThat(scheduleService.unplannable(week)).isPresent();

            scheduleService.setWeekPlan(week, "Sara", Set.of(1), Set.of(), "test");
            assertThat(weekPlanService.forWeek(week).preferredFor("Sara")).containsExactly(1);

        } finally {
            weekPlanService.replace(week, "Hamza", Set.of(), Set.of(), "test");
            weekPlanService.replace(week, "Sara", Set.of(), Set.of(), "test");
        }
    }

    @Test
    @DisplayName("a week that is over cannot be asked for anything")
    void aWeekThatIsOverIsRefused() {
        assertThatThrownBy(() -> scheduleService.setWeekPlan(
                LocalDate.of(2020, 1, 6), "Sara", Set.of(0), Set.of(), "test"))
                .isInstanceOf(WeekPlanException.class)
                .hasMessageContaining("over");
    }

    @Test
    @DisplayName("somebody who is not on the roster cannot be given days")
    void anUnknownPersonIsRefused() {
        assertThatThrownBy(() -> scheduleService.setWeekPlan(
                LocalDate.of(2029, 4, 2), "Nobody", Set.of(0), Set.of(), "test"))
                .isInstanceOf(WeekPlanException.class)
                .hasMessageContaining("roster");
    }

    @Test
    @DisplayName("somebody away until the Wednesday is in the office on the Thursday and has one remote day")
    void leaveShortensTheirWeek() {
        LocalDate week = LocalDate.of(2029, 6, 4);
        var leave = vacationService.add("Sara", LocalDate.of(2029, 6, 4), LocalDate.of(2029, 6, 6));

        try {
            var input = scheduleService.toSolverInput(week);

            // Away Lundi to Mercredi, back on the Jeudi: four days they cannot be remote on.
            assertThat(input.forbiddenDays()).containsEntry("Sara", Set.of(0, 1, 2, 3));
            assertThat(input.quotaFor("Sara")).isEqualTo(1);
            assertThat(input.quotaFor("Omar")).isEqualTo(3);
            assertThat(input.requiredPersonDays()).isEqualTo(46);

            assertThat(scheduleService.expectedRemoteDays(week))
                    .containsEntry("Sara", 1)
                    .containsEntry("Omar", 3);

            WeekSchedule schedule = scheduleService.generate(week, true, "test");

            assertThat(schedule.remoteDaysPerPerson()).containsEntry("Sara", 1);
            assertThat(schedule.peopleByDayIndex().get(4)).contains("Sara");
            for (int day = 0; day <= 3; day++) {
                assertThat(schedule.peopleByDayIndex().getOrDefault(day, List.of()))
                        .doesNotContain("Sara");
            }

        } finally {
            vacationService.delete(leave.getId());
        }
    }

    @Test
    @DisplayName("somebody away all week is left out of it, and the week is planned around them")
    void awayAllWeekMeansNoRemoteDays() {
        LocalDate week = LocalDate.of(2029, 6, 11);
        var leave = vacationService.add("Adam", LocalDate.of(2029, 6, 11),
                LocalDate.of(2029, 6, 15));

        try {
            assertThat(scheduleService.expectedRemoteDays(week)).containsEntry("Adam", 0);

            WeekSchedule schedule = scheduleService.generate(week, true, "test");

            assertThat(schedule.remoteDaysPerPerson()).doesNotContainKey("Adam");
            assertThat(schedule.getAssignments()).hasSize(45);

        } finally {
            vacationService.delete(leave.getId());
        }
    }

    @Test
    @DisplayName("leave ending on a Friday costs the Monday after it, not the week it fell in")
    void theDayBackCanLandInTheNextWeek() {
        var leave = vacationService.add("Nader", LocalDate.of(2029, 6, 18),
                LocalDate.of(2029, 6, 22));

        try {
            var input = scheduleService.toSolverInput(LocalDate.of(2029, 6, 25));

            assertThat(input.forbiddenDays()).containsEntry("Nader", Set.of(0));
            assertThat(input.quotaFor("Nader")).isEqualTo(3);

            WeekSchedule schedule = scheduleService.generate(LocalDate.of(2029, 6, 25), true, "test");
            assertThat(schedule.peopleByDayIndex().get(0)).doesNotContain("Nader");
            assertThat(schedule.remoteDaysPerPerson()).containsEntry("Nader", 3);

        } finally {
            vacationService.delete(leave.getId());
        }
    }

    @Test
    @DisplayName("leave entered after the week was planned says which week to re-roll")
    void leaveOnAPlannedWeekIsReported() {
        LocalDate week = LocalDate.of(2029, 7, 2);
        scheduleService.generate(week, true, "test");

        String remoteOnMonday = scheduleService.findByWeek(week).orElseThrow()
                .peopleByDayIndex().get(0).getFirst();

        var leave = vacationService.add(remoteOnMonday, week, week);

        try {
            assertThat(scheduleService.plannedWeeksAssigningPerson(remoteOnMonday, week, week))
                    .containsExactly(week);

        } finally {
            vacationService.delete(leave.getId());
        }
    }
}
