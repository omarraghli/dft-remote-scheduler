package cires.dft.remotescheduler.service;

import cires.dft.remotescheduler.config.RemoteScheduleProperties;
import cires.dft.remotescheduler.domain.Vacation;
import cires.dft.remotescheduler.repository.VacationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Recording who is away and when.
 *
 * <p>The rules live here rather than in the controller: a name has to be on the roster, leave
 * has to end after it starts, and the same person cannot be away twice over the same days —
 * that last one is refused because the case it catches is the same fortnight entered twice,
 * which would otherwise sit in the table unnoticed until the week came round.
 */
@Service
public class VacationService implements PersonVacations {

    private static final Logger log = LoggerFactory.getLogger(VacationService.class);

    /** Long enough for any leave anybody takes in one stretch, short enough to catch a typo. */
    private static final int MAX_DAYS = 120;

    private final VacationRepository vacations;
    private final RemoteScheduleProperties properties;
    private final Clock clock;

    public VacationService(VacationRepository vacations,
                           RemoteScheduleProperties properties,
                           Clock clock) {
        this.vacations = vacations;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Vacation> all() {
        return vacations.findAllByOrderByStartDateAscPersonNameAsc();
    }

    @Transactional(readOnly = true)
    public Vacation require(Long id) {
        return vacations.findById(id)
                .orElseThrow(() -> new VacationManagementException("No leave with id " + id));
    }

    @Transactional
    public Vacation add(String person, LocalDate startDate, LocalDate endDate) {
        String rosterName = validate(person, startDate, endDate, null);

        Vacation saved = vacations.save(
                new Vacation(rosterName, startDate, endDate, clock.instant()));
        log.info("Leave added: {} away {} to {}", rosterName, startDate, endDate);

        return saved;
    }

    @Transactional
    public Vacation update(Long id, String person, LocalDate startDate, LocalDate endDate) {
        Vacation vacation = require(id);
        String rosterName = validate(person, startDate, endDate, id);

        vacation.edit(rosterName, startDate, endDate);
        log.info("Leave {} updated: {} away {} to {}", id, rosterName, startDate, endDate);

        return vacation;
    }

    @Transactional
    public void delete(Long id) {
        Vacation vacation = require(id);
        vacations.delete(vacation);

        log.info("Leave removed: {} away {} to {}", vacation.getPersonName(),
                vacation.getStartDate(), vacation.getEndDate());
    }

    /** @return the roster name, once everything about the entry has been accepted */
    private String validate(String person, LocalDate startDate, LocalDate endDate,
                            Long ignoringId) {

        if (startDate == null || endDate == null) {
            throw new VacationManagementException("Leave needs a first and a last day.");
        }
        if (endDate.isBefore(startDate)) {
            throw new VacationManagementException("Leave cannot end before it starts.");
        }

        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (days > MAX_DAYS) {
            throw new VacationManagementException(
                    "That is " + days + " days. Leave runs at most " + MAX_DAYS
                            + " days in one entry — split it if it really is that long.");
        }

        String rosterName = requireRosterName(person);

        for (Vacation existing : all()) {
            if (existing.getId().equals(ignoringId)) continue;
            if (!existing.getPersonName().equals(rosterName)) continue;

            if (existing.overlaps(startDate, endDate)) {
                throw new VacationManagementException(
                        rosterName + " is already away " + existing.getStartDate() + " to "
                                + existing.getEndDate() + ". Edit that one instead.");
            }
        }

        return rosterName;
    }

    private String requireRosterName(String person) {
        if (person == null || person.isBlank()) {
            throw new VacationManagementException("Leave needs somebody to belong to.");
        }

        return properties.getPeople().stream()
                .filter(name -> name.equalsIgnoreCase(person.trim()))
                .findFirst()
                .orElseThrow(() -> new VacationManagementException(
                        "\"" + person + "\" is not on the roster."));
    }
}
