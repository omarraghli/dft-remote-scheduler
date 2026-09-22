package cires.dft.remotescheduler.service;

import cires.dft.remotescheduler.domain.Vacation;
import cires.dft.remotescheduler.repository.VacationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
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
    private final RosterService people;
    private final Clock clock;

    public VacationService(VacationRepository vacations,
                           RosterService people,
                           Clock clock) {
        this.vacations = vacations;
        this.people = people;
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

    /**
     * Somebody declaring their own leave. Nobody approves it — the point is that the admins are
     * not the bottleneck — so the limits are about what cannot be undone rather than what is
     * wise: nothing is backdated, and leave already over is history, not theirs to rewrite.
     */
    @Transactional
    public Vacation addOwn(String me, LocalDate startDate, LocalDate endDate) {
        requireNotPast(startDate, "Leave cannot start in the past. Ask an admin to record it.");
        return add(me, startDate, endDate);
    }

    @Transactional
    public Vacation updateOwn(String me, Long id, LocalDate startDate, LocalDate endDate) {
        Vacation vacation = requireOwn(me, id);
        requireNotPast(vacation.getEndDate(), "That leave is over and can no longer be changed.");

        // Moving the start earlier is backdating; keeping one already in the past is not.
        if (startDate != null && startDate.isBefore(vacation.getStartDate())) {
            requireNotPast(startDate, "Leave cannot start in the past. Ask an admin to record it.");
        }

        return update(id, vacation.getPersonName(), startDate, endDate);
    }

    @Transactional
    public void deleteOwn(String me, Long id) {
        Vacation vacation = requireOwn(me, id);
        requireNotPast(vacation.getEndDate(), "That leave is over and can no longer be removed.");
        delete(id);
    }

    /**
     * Everybody else away on at least one of these days — what someone planning leave wants to
     * know before they book, and the whole of the "no approval" bargain.
     */
    @Transactional(readOnly = true)
    public List<Vacation> othersAway(String me, LocalDate startDate, LocalDate endDate) {
        return vacations.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(endDate, startDate)
                .stream()
                .filter(v -> !v.getPersonName().equalsIgnoreCase(me))
                .sorted(Comparator.comparing(Vacation::getStartDate)
                        .thenComparing(Vacation::getPersonName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Leave overlapping a window, for the team calendar. */
    @Transactional(readOnly = true)
    public List<Vacation> overlapping(LocalDate from, LocalDate until) {
        return vacations.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(until, from);
    }

    private Vacation requireOwn(String me, Long id) {
        Vacation vacation = require(id);
        if (me == null || !vacation.getPersonName().equalsIgnoreCase(me)) {
            throw new VacationManagementException("That leave is not yours.");
        }
        return vacation;
    }

    private void requireNotPast(LocalDate date, String message) {
        if (date != null && date.isBefore(LocalDate.now(clock))) {
            throw new VacationManagementException(message);
        }
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

        return people.activeName(person)
                .orElseThrow(() -> new VacationManagementException(
                        "\"" + person + "\" is not on the roster."));
    }
}
