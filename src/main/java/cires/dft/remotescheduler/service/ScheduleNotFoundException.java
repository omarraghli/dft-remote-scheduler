package cires.dft.remotescheduler.service;

import java.time.LocalDate;

/** Raised when a week has no schedule stored. */
public class ScheduleNotFoundException extends RuntimeException {

    public ScheduleNotFoundException(LocalDate weekStart) {
        super("No schedule stored for the week starting " + weekStart);
    }

    public ScheduleNotFoundException(String message) {
        super(message);
    }
}
