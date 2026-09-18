package cires.dft.remotescheduler.scheduler;

import cires.dft.remotescheduler.domain.WeekSchedule;
import cires.dft.remotescheduler.service.ScheduleAlreadyExistsException;
import cires.dft.remotescheduler.service.ScheduleService;
import cires.dft.remotescheduler.service.WeekStarts;
import cires.dft.remotescheduler.solver.NoFeasibleScheduleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Runs the distribution every Thursday. */
@Component
@ConditionalOnProperty(prefix = "remote.job", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class WeeklyScheduleJob {

    public static final String TRIGGER = "scheduler";

    private static final Logger log = LoggerFactory.getLogger(WeeklyScheduleJob.class);

    private final ScheduleService scheduleService;

    public WeeklyScheduleJob(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    /**
     * Generates next week's schedule. Runs Thursday morning by default, so the team has the
     * following week planned before the weekend.
     *
     * <p>The cron and time zone come from configuration: {@code remote.job.cron} and
     * {@code remote.job.zone}. It stores the schedule and nothing else — the workbook is built
     * on request by the export endpoint, so no files are written here.
     */
    @Scheduled(cron = "${remote.job.cron:0 0 8 * * THU}", zone = "${remote.job.zone:Africa/Casablanca}")
    public void generateNextWeek() {
        LocalDate targetWeek = WeekStarts.next(scheduleService.today());

        log.info("Thursday job starting — planning the week of {}", targetWeek);

        try {
            WeekSchedule schedule = scheduleService.generate(targetWeek, false, TRIGGER);

            log.info("Thursday job finished — {} remote days assigned for the week of {}",
                    schedule.getAssignments().size(), targetWeek);

        } catch (ScheduleAlreadyExistsException e) {
            // Someone generated it by hand already. Leave their version alone.
            log.info("Skipping: {}", e.getMessage());

        } catch (NoFeasibleScheduleException e) {
            // Loud, but do not kill the scheduler thread — next Thursday should still fire.
            log.error("Could not plan the week of {}: {}", targetWeek, e.getMessage());

        } catch (RuntimeException e) {
            log.error("Thursday job failed for the week of {}", targetWeek, e);
        }
    }
}
