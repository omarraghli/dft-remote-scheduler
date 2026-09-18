package cires.dft.remotescheduler.solver;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Builds a weekly remote schedule by backtracking over per-person week patterns.
 *
 * <h2>Why patterns and not day combinations</h2>
 * The obvious formulation — for each day, pick a subset of people that fits the slots — does not
 * scale. At 16 people and 10 slots a single day has 58,651 candidate subsets, and the "everyone
 * gets exactly their quota" rule can only be checked once every day has been chosen, so the
 * search explores enormous numbers of branches that were doomed from the first day.
 *
 * <p>Transposing it fixes both problems. Each person is assigned one whole-week pattern, so the
 * quota and the consecutive-day rule hold by construction and the branching factor stays at the
 * number of valid patterns (7 for the standard configuration) no matter how large the team gets.
 * Only the daily capacity has to be tracked during the search.
 *
 * <p>The search is randomised — people order and pattern order are shuffled — so a different
 * valid week comes out each run rather than the same people always getting the same days.
 */
@Component
public class ScheduleSolver {

    /**
     * Solves with a fresh random ordering.
     *
     * @throws NoFeasibleScheduleException if the constraints admit no valid week
     */
    public SolverResult solve(SolverInput input) {
        return solve(input, new Random());
    }

    /**
     * Solves using the supplied source of randomness. Passing a seeded {@link Random} makes the
     * result reproducible, which is what the tests rely on.
     *
     * @throws NoFeasibleScheduleException if the constraints admit no valid week
     */
    public SolverResult solve(SolverInput input, Random random) {
        rejectImpossibleInput(input);

        List<String> people = new ArrayList<>(input.people());
        Collections.shuffle(people, random);

        List<List<Integer>> patternsPerPerson = new ArrayList<>(people.size());

        for (String person : people) {
            List<Integer> patterns = WeekPatterns.forPerson(
                    input.dayCount(),
                    input.remotesPerPerson(),
                    input.maxConsecutiveDays(),
                    input.holidays(),
                    input.forbiddenDays().get(person));

            if (patterns.isEmpty()) {
                throw new NoFeasibleScheduleException(
                        "No valid week pattern exists for " + person
                                + " — their blocked days leave too few days for "
                                + input.remotesPerPerson() + " remote days");
            }

            Collections.shuffle(patterns, random);
            patternsPerPerson.add(patterns);
        }

        int[] used = new int[input.dayCount()];
        int[] assigned = new int[people.size()];

        if (!backtrack(0, used, assigned, patternsPerPerson, input)) {
            throw new NoFeasibleScheduleException(
                    "No schedule satisfies the configured constraints");
        }

        return toResult(people, assigned, input);
    }

    /** Catches the infeasible cases that are obvious from arithmetic, with a usable message. */
    private void rejectImpossibleInput(SolverInput input) {
        if (input.people().isEmpty()) {
            throw new NoFeasibleScheduleException("The roster is empty");
        }
        if (input.slotsPerDay().length != input.dayCount()) {
            throw new NoFeasibleScheduleException(
                    "slotsPerDay has " + input.slotsPerDay().length + " entries but there are "
                            + input.dayCount() + " days — they must match");
        }
        if (input.remotesPerPerson() > input.dayCount()) {
            throw new NoFeasibleScheduleException(
                    "Cannot give each person " + input.remotesPerPerson()
                            + " remote days in a " + input.dayCount() + " day week");
        }

        int required = input.requiredPersonDays();
        int available = input.availableSlots();

        if (required > available) {
            throw new NoFeasibleScheduleException(
                    "Not enough capacity: " + input.people().size() + " people x "
                            + input.remotesPerPerson() + " days = " + required
                            + " remote days needed, but only " + available
                            + " slots are available" + (input.holidays().isEmpty()
                            ? "" : " once holidays are removed"));
        }
    }

    /**
     * Assigns a pattern to person {@code personIndex}, then recurses.
     *
     * <p>{@code used} carries how many people are already remote on each day and is mutated in
     * place, undone on the way out, so no arrays are cloned per node.
     */
    private boolean backtrack(int personIndex,
                              int[] used,
                              int[] assigned,
                              List<List<Integer>> patternsPerPerson,
                              SolverInput input) {

        if (personIndex == assigned.length) return true;

        int peopleLeft = assigned.length - personIndex;
        if (freeCapacity(used, input) < peopleLeft * input.remotesPerPerson()) return false;

        List<Integer> candidates = new ArrayList<>();
        for (int mask : patternsPerPerson.get(personIndex)) {
            if (fits(mask, used, input)) candidates.add(mask);
        }

        // Spread the week out: try the patterns whose days are least busy first, so the load
        // lands on 10/10/10/9/9 rather than 10/10/10/10/8. This only changes the order in which
        // branches are visited, so no valid schedule is ruled out by it.
        candidates.sort(Comparator.comparingInt(mask -> currentLoad(mask, used)));

        for (int mask : candidates) {
            applyPattern(mask, used, 1);
            assigned[personIndex] = mask;

            if (backtrack(personIndex + 1, used, assigned, patternsPerPerson, input)) return true;

            applyPattern(mask, used, -1);
        }

        return false;
    }

    private int freeCapacity(int[] used, SolverInput input) {
        int free = 0;
        for (int d = 0; d < input.dayCount(); d++) {
            if (input.holidays().contains(d)) continue;
            free += input.slotsPerDay()[d] - used[d];
        }
        return free;
    }

    private boolean fits(int mask, int[] used, SolverInput input) {
        for (int d = 0; d < input.dayCount(); d++) {
            if (isRemoteOn(mask, d) && used[d] >= input.slotsPerDay()[d]) return false;
        }
        return true;
    }

    private int currentLoad(int mask, int[] used) {
        int load = 0;
        for (int d = 0; d < used.length; d++) {
            if (isRemoteOn(mask, d)) load += used[d];
        }
        return load;
    }

    private void applyPattern(int mask, int[] used, int delta) {
        for (int d = 0; d < used.length; d++) {
            if (isRemoteOn(mask, d)) used[d] += delta;
        }
    }

    private static boolean isRemoteOn(int mask, int day) {
        return ((mask >> day) & 1) == 1;
    }

    /** Turns the per-person masks into both views callers want: by day and by person. */
    private SolverResult toResult(List<String> people, int[] assigned, SolverInput input) {
        List<List<String>> peopleByDay = new ArrayList<>(input.dayCount());
        for (int d = 0; d < input.dayCount(); d++) {
            peopleByDay.add(new ArrayList<>());
        }

        Map<String, List<Integer>> daysByPerson = new LinkedHashMap<>();

        for (int p = 0; p < people.size(); p++) {
            String person = people.get(p);
            List<Integer> days = new ArrayList<>();

            for (int d = 0; d < input.dayCount(); d++) {
                if (isRemoteOn(assigned[p], d)) {
                    peopleByDay.get(d).add(person);
                    days.add(d);
                }
            }

            daysByPerson.put(person, days);
        }

        return new SolverResult(peopleByDay, daysByPerson);
    }
}
