package cires.dft.remotescheduler.service;

import cires.dft.remotescheduler.config.RemoteScheduleProperties;
import cires.dft.remotescheduler.domain.OnSiteDay;
import cires.dft.remotescheduler.domain.RemotePreference;
import cires.dft.remotescheduler.repository.OnSiteDayRepository;
import cires.dft.remotescheduler.repository.RemotePreferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The days people ask for and the days an admin holds them in the office, one week at a time.
 *
 * <p>The rules live here rather than in the controllers, so both front doors refuse the same
 * things: a name has to be on the roster, a day has to be one of the configured ones, and a week
 * that is already over cannot be changed. Whether the change leaves the week <em>plannable</em>
 * is a different question and belongs to {@link ScheduleService}, which is the class that knows
 * the solver.
 */
@Service
public class WeekPlanService {

    private static final Logger log = LoggerFactory.getLogger(WeekPlanService.class);

    private final RemotePreferenceRepository preferences;
    private final OnSiteDayRepository onSiteDays;
    private final RemoteScheduleProperties properties;
    private final RosterService people;
    private final Clock clock;

    public WeekPlanService(RemotePreferenceRepository preferences,
                           OnSiteDayRepository onSiteDays,
                           RemoteScheduleProperties properties,
                           RosterService people,
                           Clock clock) {
        this.preferences = preferences;
        this.onSiteDays = onSiteDays;
        this.properties = properties;
        this.people = people;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public WeekPlan forWeek(LocalDate week) {
        LocalDate monday = WeekStarts.of(week);

        Map<String, Set<Integer>> preferred = new HashMap<>();
        for (RemotePreference preference : preferences.findByWeekStart(monday)) {
            preferred.computeIfAbsent(preference.getPersonName(), k -> new TreeSet<>())
                    .add(preference.getDayIndex());
        }

        Map<String, Set<Integer>> onSite = new HashMap<>();
        for (OnSiteDay day : onSiteDays.findByWeekStart(monday)) {
            onSite.computeIfAbsent(day.getPersonName(), k -> new TreeSet<>())
                    .add(day.getDayIndex());
        }

        return new WeekPlan(preferred, onSite);
    }

    /**
     * Replaces everything one person has for one week — what they asked for and where they are
     * required — in a single go, so a form that submits a whole row is the whole truth about it.
     *
     * @param setBy who is making the change, recorded against the on-site days
     * @throws WeekPlanException if the person, the days or the week are not something to accept
     */
    @Transactional
    public void replace(LocalDate week, String person, Set<Integer> preferred,
                        Set<Integer> onSite, String setBy) {

        replace(week, List.of(person),
                new WeekPlan(Map.of(person, preferred), Map.of(person, onSite)), setBy);
    }

    /**
     * The same for several people at once, which is what one Save over a whole grid means: every
     * person named is replaced by what the plan holds for them, and somebody the plan says
     * nothing about has their week cleared — an unticked box has to mean something.
     *
     * @throws WeekPlanException if any of it is not something to accept, in which case none of
     *                           it is written
     */
    @Transactional
    public void replace(LocalDate week, Collection<String> people, WeekPlan plan, String setBy) {
        LocalDate monday = WeekStarts.of(week);

        if (monday.isBefore(WeekStarts.of(LocalDate.now(clock)))) {
            throw new WeekPlanException("The week of " + monday + " is over.");
        }

        // Everything is checked before anything is written, so a bad name at the bottom of the
        // grid does not leave the rows above it half changed.
        Map<String, Set<Integer>> wanted = new LinkedHashMap<>();
        Map<String, Set<Integer>> required = new LinkedHashMap<>();

        for (String person : people) {
            String rosterName = requireRosterName(person);
            wanted.put(rosterName, requireDays(plan.preferredFor(person)));
            required.put(rosterName, requireDays(plan.onSiteFor(person)));
        }

        // Who asked for a day in the office is worth keeping, so a day that was already there
        // holds on to the admin who set it rather than being re-attributed to whoever saved the
        // grid next — which, on the schedule page, is the person the pin is about.
        Map<String, Map<Integer, String>> setPreviouslyBy = new HashMap<>();
        for (OnSiteDay day : onSiteDays.findByWeekStart(monday)) {
            setPreviouslyBy.computeIfAbsent(day.getPersonName(), k -> new HashMap<>())
                    .put(day.getDayIndex(), day.getSetBy());
        }

        for (String rosterName : wanted.keySet()) {
            preferences.deleteByWeekStartAndPersonName(monday, rosterName);
            onSiteDays.deleteByWeekStartAndPersonName(monday, rosterName);
        }
        preferences.flush();
        onSiteDays.flush();

        for (Map.Entry<String, Set<Integer>> entry : wanted.entrySet()) {
            for (int dayIndex : entry.getValue()) {
                preferences.save(new RemotePreference(
                        entry.getKey(), monday, dayIndex, clock.instant()));
            }
        }

        for (Map.Entry<String, Set<Integer>> entry : required.entrySet()) {
            Map<Integer, String> before =
                    setPreviouslyBy.getOrDefault(entry.getKey(), Map.of());

            for (int dayIndex : entry.getValue()) {
                onSiteDays.save(new OnSiteDay(entry.getKey(), monday, dayIndex,
                        before.getOrDefault(dayIndex, setBy), clock.instant()));
            }
        }

        // Flushed here so the feasibility check ScheduleService runs next sees these rows.
        preferences.flush();
        onSiteDays.flush();

        for (String rosterName : wanted.keySet()) {
            log.info("Week of {}: {} wants {} and is on site {}", monday, rosterName,
                    names(wanted.get(rosterName)), names(required.get(rosterName)));
        }
    }

    private String requireRosterName(String person) {
        if (person == null || person.isBlank()) {
            throw new WeekPlanException("There is nobody to set days for.");
        }

        return people.activeName(person)
                .orElseThrow(() -> new WeekPlanException(
                        "\"" + person + "\" is not on the roster."));
    }

    private Set<Integer> requireDays(Set<Integer> days) {
        if (days == null) return Set.of();

        List<String> dayNames = properties.getDays();
        Set<Integer> checked = new LinkedHashSet<>();

        for (Integer day : days) {
            if (day == null || day < 0 || day >= dayNames.size()) {
                throw new WeekPlanException(
                        day + " is not one of the configured days " + dayNames + ".");
            }
            checked.add(day);
        }

        return checked;
    }

    private String names(Set<Integer> days) {
        if (days.isEmpty()) return "nothing";
        return days.stream().map(day -> properties.getDays().get(day)).toList().toString();
    }
}
