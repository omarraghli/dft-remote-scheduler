package cires.dft.remotescheduler.service;

import cires.dft.remotescheduler.config.PublicHolidayProperties;
import cires.dft.remotescheduler.domain.Holiday;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Tops the holiday table up from {@code remote.public-holidays.dated}, so the calendar keeps
 * reaching into the future instead of quietly running out.
 *
 * <p>Only dates beyond the last stored holiday are added. Everything up to that point is the
 * admins' — a corrected Aïd stays corrected and a deleted one stays deleted, however the shipped
 * list reads. Consecutive days under one name are folded into a single entry, so a two-day Aïd
 * seeds as one holiday of two days, the same shape an admin would type.
 */
@Component
public class HolidaySeed implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(HolidaySeed.class);

    private final HolidayService holidays;
    private final PublicHolidayProperties properties;

    public HolidaySeed(HolidayService holidays, PublicHolidayProperties properties) {
        this.holidays = holidays;
        this.properties = properties;
    }

    /** A holiday the configuration describes, before it is anything in the database. */
    private record Run(String name, LocalDate start, int days) {

        LocalDate end() {
            return start.plusDays(days - 1L);
        }
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Run> configured = fold();
        if (configured.isEmpty()) return;

        LocalDate covered = holidays.coveredUntil();

        List<Run> beyond = covered == null
                ? configured
                : configured.stream().filter(run -> run.start().isAfter(covered)).toList();

        for (Run run : beyond) {
            holidays.add(run.name(), run.start(), run.days());
        }

        if (!beyond.isEmpty()) {
            log.info("Added {} public holidays from configuration, running to {}. "
                            + "They are editable at /admin/holidays from now on.",
                    beyond.size(), beyond.getLast().end());
        }

        warnIfShort();
    }

    /**
     * Says so while there is still time to do something about it: the lunar dates are only ever
     * as good as the last person who typed them in, and a week planned past the end of the list
     * is a week planned as though the Aïd were a working day.
     */
    private void warnIfShort() {
        if (!holidays.coverageRunsLow()) return;

        LocalDate covered = holidays.coveredUntil();

        log.warn("Public holidays are only listed to {}. Weeks planned beyond that will treat "
                        + "the lunar holidays as ordinary working days — add the announced dates "
                        + "at /admin/holidays, or extend remote.public-holidays.dated.",
                covered == null ? "no date at all" : covered);
    }

    /** The configured dates, with consecutive days under one name gathered into one holiday. */
    private List<Run> fold() {
        List<PublicHolidayProperties.Dated> configured = properties.getDated().stream()
                .filter(entry -> entry.getDate() != null
                        && entry.getName() != null && !entry.getName().isBlank())
                .sorted(Comparator.comparing(PublicHolidayProperties.Dated::getDate))
                .toList();

        if (configured.isEmpty()) return List.of();

        List<Run> runs = new ArrayList<>();
        String name = null;
        var start = configured.getFirst().getDate();
        int days = 0;

        for (PublicHolidayProperties.Dated entry : configured) {
            boolean continues = name != null
                    && name.equals(entry.getName().trim())
                    && start.plusDays(days).equals(entry.getDate());

            if (continues) {
                days++;
                continue;
            }

            if (name != null) runs.add(new Run(name, start, days));

            name = entry.getName().trim();
            start = entry.getDate();
            days = 1;
        }

        runs.add(new Run(name, start, days));
        return runs;
    }
}
