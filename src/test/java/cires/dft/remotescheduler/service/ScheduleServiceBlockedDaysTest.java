package cires.dft.remotescheduler.service;

import cires.dft.remotescheduler.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** A vacation return and a meeting can land on the same person, and both have to hold. */
@SpringBootTest
@TestPropertySource(properties = {
        "remote.job.enabled=false",
        "remote.vacation-returns.Sara=Lundi"
})
class ScheduleServiceBlockedDaysTest extends AbstractPostgresIntegrationTest {

    private static final LocalDate WEEK = LocalDate.of(2029, 5, 7);

    @Autowired
    private ScheduleService scheduleService;

    @Test
    @DisplayName("an on-site day joins a vacation return rather than replacing it")
    void bothBlockTheSamePerson() {
        try {
            scheduleService.setWeekPlan(WEEK, "Sara", Set.of(), Set.of(3), "test");

            assertThat(scheduleService.toSolverInput(WEEK).forbiddenDays())
                    .containsEntry("Sara", Set.of(0, 3));

            var schedule = scheduleService.generate(WEEK, true, "test");
            assertThat(schedule.peopleByDayIndex().get(0)).doesNotContain("Sara");
            assertThat(schedule.peopleByDayIndex().get(3)).doesNotContain("Sara");

        } finally {
            scheduleService.setWeekPlan(WEEK, "Sara", Set.of(), Set.of(), "test");
        }
    }
}
