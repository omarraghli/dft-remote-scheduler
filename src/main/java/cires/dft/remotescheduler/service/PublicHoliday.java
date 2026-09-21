package cires.dft.remotescheduler.service;

import java.time.LocalDate;

/**
 * One public holiday falling on one of the configured working days.
 *
 * @param date     the day itself
 * @param dayIndex its column in the week, so the page and the solver agree on which day it is
 * @param dayName  the configured name of that day, e.g. {@code Vendredi}
 * @param name     the holiday, e.g. {@code Marche Verte}
 */
public record PublicHoliday(LocalDate date, int dayIndex, String dayName, String name) {
}
