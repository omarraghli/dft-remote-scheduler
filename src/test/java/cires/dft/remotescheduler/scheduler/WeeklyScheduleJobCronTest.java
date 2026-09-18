package cires.dft.remotescheduler.scheduler;

import cires.dft.remotescheduler.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.support.CronExpression;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards the one thing nobody notices is broken until a Thursday has come and gone. */
@SpringBootTest
class WeeklyScheduleJobCronTest extends AbstractPostgresIntegrationTest {

    @Value("${remote.job.cron}")
    private String cron;

    @Test
    @DisplayName("the configured cron fires on Thursdays at 08:00, once a week")
    void firesThursdayMornings() {
        CronExpression expression = CronExpression.parse(cron);

        LocalDateTime next = LocalDateTime.of(2026, 9, 18, 9, 0);

        for (int week = 0; week < 5; week++) {
            next = expression.next(next);

            assertThat(next).isNotNull();
            assertThat(next.getDayOfWeek()).isEqualTo(DayOfWeek.THURSDAY);
            assertThat(next.getHour()).isEqualTo(8);
            assertThat(next.getMinute()).isZero();
        }

        // five fires on from 2026-09-18 is the Thursday five weeks later, so it runs weekly
        assertThat(next.toLocalDate()).isEqualTo(LocalDate.of(2026, 10, 22));
    }
}
