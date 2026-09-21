package cires.dft.remotescheduler.service;

import cires.dft.remotescheduler.domain.Vacation;

import java.util.List;

/**
 * The stored leave, as {@link VacationCalendar} sees it. A seam rather than the repository, so
 * the calendar's rules can be tested against a list without a database.
 */
public interface PersonVacations {

    List<Vacation> all();
}
