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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = "remote.job.enabled=false")
class ScheduleServiceTest extends AbstractPostgresIntegrationTest {

    private static final LocalDate WEEK = LocalDate.of(2026, 9, 21);

    @Autowired
    private ScheduleService scheduleService;

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
        var input = scheduleService.toSolverInput();

        assertThat(input.people()).hasSize(16).contains("MedKhalil", "Outman");
        assertThat(input.dayNames()).containsExactly(
                "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi");
        assertThat(input.slotsPerDay()).containsExactly(10, 10, 10, 10, 10);
        assertThat(input.remotesPerPerson()).isEqualTo(3);
        assertThat(input.maxConsecutiveDays()).isEqualTo(2);
        assertThat(input.requiredPersonDays()).isEqualTo(48);
        assertThat(input.availableSlots()).isEqualTo(50);
    }
}
