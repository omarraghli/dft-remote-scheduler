package cires.dft.remotescheduler.service;

import cires.dft.remotescheduler.domain.Holiday;

import java.util.List;

/**
 * Where the holidays that move come from — the database in the application, a plain list in the
 * calendar's unit tests. Keeps {@link HolidayCalendar} a function of its inputs.
 */
public interface DatedHolidays {

    List<Holiday> all();
}
