package cires.dft.remotescheduler.service;

import cires.dft.remotescheduler.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Usual days stand in for any week a person has said nothing about, and only those. */
@SpringBootTest
class UsualDaysTest extends AbstractPostgresIntegrationTest {

    private static final LocalDate WEEK = LocalDate.of(2030, 5, 6);

    @Autowired private WeekPlanService weekPlans;

    @AfterEach
    void cleanUp() {
        weekPlans.setUsual("Sara", Set.of());
        weekPlans.replace(WEEK, "Sara", Set.of(), Set.of(), "test");
        weekPlans.replace(WEEK.plusWeeks(1), "Sara", Set.of(), Set.of(), "test");
    }

    @Test
    @DisplayName("a week nobody has touched asks for the usual days")
    void fillsAnUntouchedWeek() {
        weekPlans.setUsual("Sara", Set.of(1, 3));

        assertThat(weekPlans.forWeek(WEEK).preferredFor("Sara")).containsExactly(1, 3);
        assertThat(weekPlans.forWeek(WEEK.plusWeeks(5)).preferredFor("Sara")).containsExactly(1, 3);
        assertThat(weekPlans.hasOwnWishes("Sara", WEEK)).isFalse();
    }

    @Test
    @DisplayName("changing one week overrides the usual days for that week alone")
    void oneWeekOverrides() {
        weekPlans.setUsual("Sara", Set.of(1, 3));
        weekPlans.replace(WEEK, "Sara", Set.of(0), Set.of(), "test");

        assertThat(weekPlans.forWeek(WEEK).preferredFor("Sara")).containsExactly(0);
        assertThat(weekPlans.hasOwnWishes("Sara", WEEK)).isTrue();
        assertThat(weekPlans.forWeek(WEEK.plusWeeks(1)).preferredFor("Sara")).containsExactly(1, 3);
    }

    @Test
    @DisplayName("saving a week with exactly the usual days stores nothing, so it keeps following them")
    void theUsualDaysAreNotCopied() {
        weekPlans.setUsual("Sara", Set.of(1, 3));
        weekPlans.replace(WEEK, "Sara", Set.of(1, 3), Set.of(), "test");

        assertThat(weekPlans.hasOwnWishes("Sara", WEEK)).isFalse();

        weekPlans.setUsual("Sara", Set.of(2, 4));
        assertThat(weekPlans.forWeek(WEEK).preferredFor("Sara")).containsExactly(2, 4);
    }

    @Test
    @DisplayName("the usual days are wishes like any other: the solver input carries them")
    void reachTheSolver(@Autowired ScheduleService schedules) {
        weekPlans.setUsual("Sara", Set.of(1, 3));

        assertThat(schedules.toSolverInput(WEEK).preferredDays().get("Sara")).containsExactly(1, 3);
    }
}
