package cires.dft.remotescheduler.web;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * How a week is written on screen. Shared by the two pages that show one, so the strip above
 * the chart and the strip above the grid never word it differently.
 */
final class WeekLabels {

    /** Day names are configured in French, so the dates on the pages follow. */
    static final Locale PAGE_LOCALE = Locale.FRENCH;

    static final DateTimeFormatter DAY_MONTH =
            DateTimeFormatter.ofPattern("d MMM", PAGE_LOCALE);

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d", PAGE_LOCALE);
    private static final DateTimeFormatter DAY_MONTH_YEAR =
            DateTimeFormatter.ofPattern("d MMM yyyy", PAGE_LOCALE);

    private WeekLabels() {
    }

    /**
     * {@code 21 – 25 sept. 2026}, keeping only what changes across the week: the month appears
     * twice only when the week straddles two, and the year only when it straddles two of those.
     *
     * @param dayCount how many working days the week has, so the range ends on the last one
     */
    static String range(LocalDate weekStart, int dayCount) {
        LocalDate weekEnd = weekStart.plusDays(Math.max(dayCount - 1, 0));

        DateTimeFormatter from = weekStart.getYear() != weekEnd.getYear() ? DAY_MONTH_YEAR
                : weekStart.getMonth() != weekEnd.getMonth() ? DAY_MONTH
                : DAY;

        return from.format(weekStart) + " – " + DAY_MONTH_YEAR.format(weekEnd);
    }
}
