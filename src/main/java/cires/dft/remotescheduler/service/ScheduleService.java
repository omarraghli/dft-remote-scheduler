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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private final HolidayCalendar holidays;
    private final VacationCalendar vacations;
    private final WeekPlanService weekPlans;
    private final RosterService people;
    private final Clock clock;

    public ScheduleService(ScheduleSolver solver,
                           WeekScheduleRepository repository,
                           RemoteScheduleProperties properties,
                           HolidayCalendar holidays,
                           VacationCalendar vacations,
                           WeekPlanService weekPlans,
                           RosterService people,
                           Clock clock) {
        this.solver = solver;
        this.repository = repository;
        this.properties = properties;
        this.holidays = holidays;
        this.vacations = vacations;
        this.weekPlans = weekPlans;
        this.people = people;
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

        List<PublicHoliday> publicHolidays = holidays.inWeek(monday);
        if (!publicHolidays.isEmpty()) {
            log.info("Week of {} has {} public holiday(s): {} — each person gets {} remote days",
                    monday, publicHolidays.size(),
                    publicHolidays.stream().map(PublicHoliday::name).toList(),
                    holidays.remotesPerPerson(monday));
        }

        SolverResult result = solver.solve(toSolverInput(monday));

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
                monday, saved.getAssignments().size(), result.peopleByDay().stream()
                        .flatMap(List::stream).distinct().count());

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

    /**
     * Weeks that already have a schedule putting people on one of these dates — what a holiday
     * announced after the fact collides with. Those weeks need re-rolling: their assignments
     * were made when the day was still a working one.
     */
    @Transactional(readOnly = true)
    public List<LocalDate> plannedWeeksAssigningOn(Collection<LocalDate> dates) {
        Set<LocalDate> weeks = new LinkedHashSet<>();

        for (LocalDate date : dates) {
            LocalDate monday = WeekStarts.of(date);
            int dayIndex = (int) ChronoUnit.DAYS.between(monday, date);

            repository.findByWeekStart(monday)
                    .filter(s -> !s.peopleByDayIndex().getOrDefault(dayIndex, List.of()).isEmpty())
                    .ifPresent(s -> weeks.add(monday));
        }

        return List.copyOf(weeks);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /**
     * Sets what one person asked for and where they are required, for one week.
     *
     * <p>Held here rather than in {@link WeekPlanService} because it is the only place that can
     * answer the question that matters: does the week still have an answer afterwards? Holding
     * one person in the office on Lundi <em>and</em> Vendredi leaves them Mardi to Jeudi, which
     * is three days in a row and against the rules — an ordinary request that quietly makes the
     * week unplannable, so it is refused at the point somebody makes it rather than found on
     * the morning the week is due.
     *
     * <p>A week that was already unplannable before the change is not blamed on it: whoever is
     * ticking a box cannot do anything about a holiday announced last week.
     *
     * @throws WeekPlanException if the change is not acceptable, in which case nothing is stored
     */
    @Transactional
    public void setWeekPlan(LocalDate week, String person, Set<Integer> preferred,
                            Set<Integer> onSite, String setBy) {

        setWeekPlan(week, List.of(person),
                new WeekPlan(Map.of(person, preferred), Map.of(person, onSite)), setBy);
    }

    /**
     * The same for a whole grid saved at once. The week is checked once either side of the
     * write rather than once per person, so sixteen rows cost the same two trial solves as one.
     *
     * @throws WeekPlanException if the change is not acceptable, in which case nothing is stored
     */
    @Transactional
    public void setWeekPlan(LocalDate week, Collection<String> people, WeekPlan plan,
                            String setBy) {

        LocalDate monday = WeekStarts.of(week);
        boolean wasPlannable = unplannable(monday).isEmpty();

        weekPlans.replace(monday, people, plan, setBy);

        if (wasPlannable) {
            unplannable(monday).ifPresent(why -> {
                throw new WeekPlanException(
                        "That leaves the week of " + monday + " with no schedule at all — "
                                + why + ".");
            });
        }
    }

    /** Why the week cannot be planned as things stand, or empty when it can. */
    @Transactional(readOnly = true)
    public Optional<String> unplannable(LocalDate week) {
        try {
            solver.solve(toSolverInput(WeekStarts.of(week)));
            return Optional.empty();

        } catch (NoFeasibleScheduleException e) {
            return Optional.of(e.getMessage());
        }
    }

    /**
     * The planned week this change contradicts, if any: a stored schedule that has {@code person}
     * remote on a day they are now held in the office. Those assignments were made before the
     * meeting existed, so the week needs re-rolling — the same warning a late holiday gets, and
     * never acted on automatically.
     *
     * <p>Deliberately narrower than {@link #plannedWeeksAssigningOn}: on any given day somebody
     * is remote, so a warning that fired on that would fire every time and be ignored within a
     * week.
     */
    @Transactional(readOnly = true)
    public Optional<LocalDate> plannedWeekContradicting(LocalDate week, String person,
                                                        Set<Integer> onSite) {
        return peopleContradictingPlannedWeek(week, Map.of(person, onSite)).isEmpty()
                ? Optional.empty()
                : Optional.of(WeekStarts.of(week));
    }

    /**
     * Weeks already planned that have {@code person} remote on a day between these two dates —
     * what leave entered after the fact collides with. They are not working those days at all,
     * so the week has to be re-rolled.
     */
    @Transactional(readOnly = true)
    public List<LocalDate> plannedWeeksAssigningPerson(String person, LocalDate from,
                                                       LocalDate until) {
        Set<LocalDate> weeks = new LinkedHashSet<>();

        for (LocalDate date = from; !date.isAfter(until); date = date.plusDays(1)) {
            LocalDate monday = WeekStarts.of(date);
            int dayIndex = (int) ChronoUnit.DAYS.between(monday, date);

            repository.findByWeekStart(monday)
                    .filter(s -> s.peopleByDayIndex()
                            .getOrDefault(dayIndex, List.of()).contains(person))
                    .ifPresent(s -> weeks.add(monday));
        }

        return List.copyOf(weeks);
    }

    /** A planned week that has somebody remote on a day they are on leave. */
    public record LeaveConflict(LocalDate week, String person) {
    }

    /**
     * Every planned week from {@code from} on that puts somebody remote while they are away.
     * Leave is declared by the people taking it, at any hour, so the admins cannot rely on
     * having seen the flash message; this is what the pages show them instead.
     */
    @Transactional(readOnly = true)
    public List<LeaveConflict> leaveConflicts(LocalDate from) {
        LocalDate monday = WeekStarts.of(from);
        List<LeaveConflict> conflicts = new ArrayList<>();

        for (WeekSchedule schedule :
                repository.findByWeekStartGreaterThanEqualOrderByWeekStartAsc(monday)) {
            Map<Integer, List<String>> byDay = schedule.peopleByDayIndex();
            vacations.awayDays(schedule.getWeekStart()).entrySet().stream()
                    .filter(entry -> entry.getValue().stream().anyMatch(day ->
                            byDay.getOrDefault(day, List.of()).contains(entry.getKey())))
                    .map(Map.Entry::getKey)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(person -> conflicts.add(
                            new LeaveConflict(schedule.getWeekStart(), person)));
        }

        return conflicts;
    }

    /** The same over a whole grid: everybody the stored schedule now disagrees with, by name. */
    @Transactional(readOnly = true)
    public List<String> peopleContradictingPlannedWeek(LocalDate week,
                                                       Map<String, Set<Integer>> onSite) {

        Optional<WeekSchedule> schedule = repository.findByWeekStart(WeekStarts.of(week));
        if (schedule.isEmpty()) return List.of();

        Map<Integer, List<String>> byDay = schedule.get().peopleByDayIndex();

        return onSite.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(dayIndex -> byDay
                        .getOrDefault(dayIndex, List.of()).contains(entry.getKey())))
                .map(Map.Entry::getKey)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    /**
     * Translates the configuration and the roster into the solver's input, resolving day names to indices.
     *
     * <p>The week matters: public holidays are dates, so which days are off — and with them the
     * quota, lowered by {@link HolidayCalendar} for a short week — depends on the week being
     * planned. The weekday names in {@code remote.holidays} apply to every week and are added on
     * top, without touching the quota. That week's {@link WeekPlan} arrives the same way: the
     * on-site days are blocked days, the wishes stay wishes.
     */
    SolverInput toSolverInput(LocalDate week) {
        List<String> days = properties.getDays();
        Set<Integer> closedDays = closedDays(week);
        WeekPlan plan = weekPlans.forWeek(week);

        // A day somebody is held in the office on is closed for them exactly as a holiday is
        // closed for everyone. So are the days they are away and the day they come back.
        Map<String, Set<Integer>> forbiddenDays = new HashMap<>();
        for (Map.Entry<String, Set<Integer>> entry : plan.onSite().entrySet()) {
            forbiddenDays.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        for (Map.Entry<String, Set<Integer>> entry : vacations.blockedDays(week).entrySet()) {
            forbiddenDays.computeIfAbsent(entry.getKey(), k -> new HashSet<>())
                    .addAll(entry.getValue());
        }

        int[] slotsPerDay = new int[properties.getSlotsPerDay().size()];
        for (int i = 0; i < slotsPerDay.length; i++) {
            slotsPerDay[i] = properties.getSlotsPerDay().get(i);
        }

        return new SolverInput(
                people.activeNames(),
                days,
                slotsPerDay,
                holidays.remotesPerPerson(week),
                properties.getMaxConsecutiveDays(),
                closedDays,
                forbiddenDays,
                plan.preferred(),
                vacations.quotas(week, closedDays, holidays.remotesPerPerson(week)));
    }

    /** Days nobody works at all: the week's public holidays and the standing closures. */
    private Set<Integer> closedDays(LocalDate week) {
        Set<Integer> closed = new HashSet<>(holidays.dayIndexes(week));
        for (String holiday : properties.getHolidays()) {
            closed.add(requireDayIndex(holiday, "remote.holidays"));
        }
        return closed;
    }

    /**
     * How many remote days the week owes each person — the week's quota for most of them, less
     * for anybody whose leave leaves no room for it. What the chart measures a row against.
     */
    @Transactional(readOnly = true)
    public Map<String, Integer> expectedRemoteDays(LocalDate week) {
        LocalDate monday = WeekStarts.of(week);
        int weekQuota = holidays.remotesPerPerson(monday);
        Map<String, Integer> personal = vacations.quotas(monday, closedDays(monday), weekQuota);

        Map<String, Integer> expected = new LinkedHashMap<>();
        for (String person : people.activeNames()) {
            expected.put(person, personal.getOrDefault(person, weekQuota));
        }

        return expected;
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
