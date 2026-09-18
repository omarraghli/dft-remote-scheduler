package cires.dft.remotescheduler.service;

import cires.dft.remotescheduler.config.RemoteScheduleProperties;
import cires.dft.remotescheduler.domain.WeekSchedule;
import cires.dft.remotescheduler.repository.WeekScheduleRepository;
import cires.dft.remotescheduler.solver.NoFeasibleScheduleException;
import cires.dft.remotescheduler.solver.ScheduleSolver;
import cires.dft.remotescheduler.solver.SolverInput;
import cires.dft.remotescheduler.solver.SolverResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Generates weekly schedules and stores them.
 *
 * <p>This is the only place that knows both the solver and the database: it translates the
 * configuration into {@link SolverInput}, runs the solver, and persists the answer as a
 * {@link WeekSchedule}. The solver itself stays a pure function of its input.
 */
@Service
public class ScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);

    private final ScheduleSolver solver;
    private final WeekScheduleRepository repository;
    private final RemoteScheduleProperties properties;
    private final Clock clock;

    public ScheduleService(ScheduleSolver solver,
                           WeekScheduleRepository repository,
                           RemoteScheduleProperties properties,
                           Clock clock) {
        this.solver = solver;
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Generates and stores the schedule for a week.
     *
     * @param weekStart the Monday of the target week; normalised, so any day of that week works
     * @param replace   whether to overwrite an existing schedule for that week
     * @param generatedBy a short label recording what triggered this, e.g. {@code scheduler}
     * @throws ScheduleAlreadyExistsException if the week is taken and {@code replace} is false
     * @throws NoFeasibleScheduleException    if the constraints admit no valid week
     */
    @Transactional
    public WeekSchedule generate(LocalDate weekStart, boolean replace, String generatedBy) {
        LocalDate monday = WeekStarts.of(weekStart);

        Optional<WeekSchedule> existing = repository.findByWeekStart(monday);
        if (existing.isPresent()) {
            if (!replace) throw new ScheduleAlreadyExistsException(monday);

            log.info("Replacing the existing schedule for week starting {}", monday);
            repository.delete(existing.get());
            repository.flush();
        }

        SolverResult result = solver.solve(toSolverInput());

        WeekSchedule schedule = new WeekSchedule(monday, clock.instant(), generatedBy);
        List<String> dayNames = properties.getDays();
        List<List<String>> peopleByDay = result.peopleByDay();

        for (int dayIndex = 0; dayIndex < peopleByDay.size(); dayIndex++) {
            for (String person : peopleByDay.get(dayIndex)) {
                schedule.addAssignment(person, dayIndex, dayNames.get(dayIndex));
            }
        }

        WeekSchedule saved = repository.save(schedule);

        log.info("Generated schedule for week starting {} — {} remote days across {} people",
                monday, saved.getAssignments().size(), properties.getPeople().size());

        return saved;
    }

    /** The schedule for the week containing {@code date}, if one was generated. */
    @Transactional(readOnly = true)
    public Optional<WeekSchedule> findByWeek(LocalDate date) {
        return repository.findByWeekStart(WeekStarts.of(date));
    }

    /** The schedule for the current week, if one was generated. */
    @Transactional(readOnly = true)
    public Optional<WeekSchedule> findCurrentWeek() {
        return findByWeek(LocalDate.now(clock));
    }

    /**
     * What the web page shows by default: this week's schedule, falling back to the most recent
     * one so a fresh install with only next week planned still displays something.
     */
    @Transactional(readOnly = true)
    public Optional<WeekSchedule> findCurrentOrLatest() {
        return findCurrentWeek().or(repository::findFirstByOrderByWeekStartDesc);
    }

    @Transactional(readOnly = true)
    public List<WeekSchedule> findAll() {
        return repository.findAllByOrderByWeekStartDesc();
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /** Translates the YAML configuration into the solver's input, resolving day names to indices. */
    SolverInput toSolverInput() {
        List<String> days = properties.getDays();

        Set<Integer> holidays = new HashSet<>();
        for (String holiday : properties.getHolidays()) {
            holidays.add(requireDayIndex(holiday, "remote.holidays"));
        }

        Map<String, Set<Integer>> forbiddenDays = new HashMap<>();
        for (Map.Entry<String, String> entry : properties.getVacationReturns().entrySet()) {
            int dayIndex = requireDayIndex(entry.getValue(), "remote.vacation-returns");
            forbiddenDays.computeIfAbsent(entry.getKey(), k -> new HashSet<>()).add(dayIndex);
        }

        int[] slotsPerDay = new int[properties.getSlotsPerDay().size()];
        for (int i = 0; i < slotsPerDay.length; i++) {
            slotsPerDay[i] = properties.getSlotsPerDay().get(i);
        }

        return new SolverInput(
                properties.getPeople(),
                days,
                slotsPerDay,
                properties.getRemotesPerPerson(),
                properties.getMaxConsecutiveDays(),
                holidays,
                forbiddenDays);
    }

    private int requireDayIndex(String dayName, String propertyPath) {
        List<String> days = properties.getDays();
        for (int i = 0; i < days.size(); i++) {
            if (days.get(i).equalsIgnoreCase(dayName)) return i;
        }
        throw new IllegalArgumentException(
                propertyPath + " refers to \"" + dayName + "\", which is not one of the "
                        + "configured days " + days
                        + " (compared ignoring case, locale " + Locale.ROOT + ")");
    }
}
