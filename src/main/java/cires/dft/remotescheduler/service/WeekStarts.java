package cires.dft.remotescheduler.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** Week identity: a schedule is always keyed by the Monday its week starts on. */
public final class WeekStarts {

    private WeekStarts() {
    }

    /** The Monday of the week containing {@code date}. */
    public static LocalDate of(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /**
     * The Monday the Thursday job plans for. Running on Thursday the 18th produces the week
     * starting Monday the 22nd, so the team has next week's schedule before the weekend.
     */
    public static LocalDate next(LocalDate date) {
        return date.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }
}
