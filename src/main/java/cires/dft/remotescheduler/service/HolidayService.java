package cires.dft.remotescheduler.service;

import cires.dft.remotescheduler.domain.Holiday;
import cires.dft.remotescheduler.repository.HolidayRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Adding, editing and removing public holidays.
 *
 * <p>The rules live here rather than in the controller, so the API of the service is the same
 * whatever calls it: a holiday needs a name, runs at least one day, and cannot overlap another.
 * Overlapping is refused because the case it really catches is the same Aïd entered twice, which
 * would otherwise sit in the table unnoticed until the week came round.
 */
@Service
public class HolidayService implements DatedHolidays {

    private static final Logger log = LoggerFactory.getLogger(HolidayService.class);

    /** Two is the longest Morocco actually observes; the rest is room for an exceptional closure. */
    private static final int MAX_DAYS = 14;

    /**
     * Below this much runway the calendar is treated as running out. Half a year is enough to
     * cover anything already planned and still leave room to chase the announcements.
     */
    private static final int COVERAGE_DAYS = 180;

    private final HolidayRepository holidays;
    private final Clock clock;

    public HolidayService(HolidayRepository holidays, Clock clock) {
        this.holidays = holidays;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Holiday> all() {
        return holidays.findAllByOrderByStartDateAsc();
    }

    /** The last day the stored calendar accounts for, or {@code null} while there are no rows. */
    @Transactional(readOnly = true)
    public LocalDate coveredUntil() {
        return all().stream().map(Holiday::endDate).max(Comparator.naturalOrder()).orElse(null);
    }

    /**
     * Whether the calendar is close enough to its end to be worth saying so. Past the last stored
     * holiday every week plans as though the lunar days were ordinary working days, and nothing
     * about the result looks wrong until the week arrives.
     */
    @Transactional(readOnly = true)
    public boolean coverageRunsLow() {
        LocalDate covered = coveredUntil();
        return covered == null || covered.isBefore(LocalDate.now(clock).plusDays(COVERAGE_DAYS));
    }

    @Transactional(readOnly = true)
    public Holiday require(Long id) {
        return holidays.findById(id)
                .orElseThrow(() -> new HolidayManagementException("No holiday with id " + id));
    }

    @Transactional
    public Holiday add(String name, LocalDate startDate, int days) {
        String cleaned = validate(name, startDate, days, null);

        Holiday saved = holidays.save(new Holiday(cleaned, startDate, days, clock.instant()));
        log.info("Holiday added: {} from {} for {} day(s)", cleaned, startDate, days);

        return saved;
    }

    @Transactional
    public Holiday update(Long id, String name, LocalDate startDate, int days) {
        Holiday holiday = require(id);
        String cleaned = validate(name, startDate, days, id);

        holiday.edit(cleaned, startDate, days);
        log.info("Holiday {} updated: {} from {} for {} day(s)", id, cleaned, startDate, days);

        return holiday;
    }

    @Transactional
    public void delete(Long id) {
        Holiday holiday = require(id);
        holidays.delete(holiday);

        log.info("Holiday removed: {} on {}", holiday.getName(), holiday.getStartDate());
    }

    /** @return the trimmed name, once everything about the entry has been accepted */
    private String validate(String name, LocalDate startDate, int days, Long ignoringId) {
        if (name == null || name.isBlank()) {
            throw new HolidayManagementException("A holiday needs a name.");
        }
        if (startDate == null) {
            throw new HolidayManagementException("A holiday needs a date.");
        }
        if (days < 1 || days > MAX_DAYS) {
            throw new HolidayManagementException(
                    "A holiday runs between 1 and " + MAX_DAYS + " days, not " + days + ".");
        }

        for (Holiday existing : holidays.findAllByOrderByStartDateAsc()) {
            if (existing.getId().equals(ignoringId)) continue;

            if (existing.overlaps(startDate, days)) {
                throw new HolidayManagementException(
                        "That overlaps " + existing.getName() + ", already set for "
                                + existing.getStartDate()
                                + (existing.getDays() > 1
                                        ? " to " + existing.endDate() : "")
                                + ". Edit that one instead.");
            }
        }

        return name.trim();
    }
}
