package cires.dft.remotescheduler.service;

import java.time.LocalDate;

/** Raised when a week already has a schedule and the caller did not ask to replace it. */
public class ScheduleAlreadyExistsException extends RuntimeException {

    private final LocalDate weekStart;

    public ScheduleAlreadyExistsException(LocalDate weekStart) {
        super("A schedule already exists for the week starting " + weekStart
                + ". Replace it explicitly if you want to re-roll it.");
        this.weekStart = weekStart;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }
}
