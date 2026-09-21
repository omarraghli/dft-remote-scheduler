package cires.dft.remotescheduler.solver;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

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
 *
 * <h2>Wishes are not constraints</h2>
 * Preferred days only ever reorder the search: they never reach {@link WeekPatterns}, so no valid
 * week is ruled out by one and no wish can bend the quota, the consecutive-day limit or the daily
 * slots. Days somebody cannot be remote on are the opposite — those are hard, and arrive in
 * {@link SolverInput#forbiddenDays()} alongside the holidays.
 */
@Component
public class ScheduleSolver {

    /**
     * How far each wish-aware pass may go before it falls back. Measured in visited nodes: a real
     * week costs a few hundred, the contrived ones tens of thousands, so this is loose enough
     * never to fire on a week anybody will actually ask for and tight enough to stay quick.
     */
    private static final int WISH_NODE_BUDGET = 100_000;

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

        // People who asked for days go first, so a popular day is claimed by those who wanted it
        // rather than by whoever the shuffle put at the front. Sorting is stable, so the shuffle
        // still decides the order within each group — that is the draw when a day is
        // over-subscribed — and with nobody wishing the order is exactly the shuffled one.
        people.sort(Comparator.comparingInt(
                person -> input.preferredDays().getOrDefault(person, Set.of()).isEmpty() ? 1 : 0));

        List<List<Integer>> patternsPerPerson = new ArrayList<>(people.size());
        int[] preferredMasks = new int[people.size()];

        for (int p = 0; p < people.size(); p++) {
            String person = people.get(p);

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
            preferredMasks[p] = input.preferredMask(person);
        }

        int[] assigned = new int[people.size()];

        // Chasing wishes can walk the search into a corner it has to unwind a long way out of —
        // a whole team asking for the same two days is the case, and only four of them can have
        // both. So the greedy pass gets a budget, and past it the week is planned with the
        // wishes demoted to a tie-break, then without them at all. Each fallback grants less of
        // what people asked for and none of them bends a rule; a week always comes out.
        if (search(assigned, patternsPerPerson, preferredMasks, input,
                new int[]{WISH_NODE_BUDGET}, true)) {
            return toResult(people, assigned, input);
        }
        if (search(assigned, patternsPerPerson, preferredMasks, input,
                new int[]{WISH_NODE_BUDGET}, false)) {
            return toResult(people, assigned, input);
        }
        if (!search(assigned, patternsPerPerson, new int[people.size()], input, null, false)) {
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

        rejectUnreachableCapacity(input, required);
    }

    /**
     * The same arithmetic again, counting only the slots people can actually reach: a day absorbs
     * at most {@code min(slots, people free that day)}, so blocking enough people on one day
     * makes a week impossible while the plain slot count still looks fine. An upper bound, so it
     * can never reject a week that has an answer — and with nobody blocked it is the slot count.
     */
    private void rejectUnreachableCapacity(SolverInput input, int required) {
        int reachable = 0;
        int tightestDay = -1;
        int tightestFree = Integer.MAX_VALUE;

        for (int d = 0; d < input.dayCount(); d++) {
            if (input.holidays().contains(d)) continue;

            int free = freePeopleOn(d, input);
            reachable += Math.min(input.slotsPerDay()[d], free);

            if (free < tightestFree) {
                tightestFree = free;
                tightestDay = d;
            }
        }

        if (required > reachable) {
            throw new NoFeasibleScheduleException(
                    "Not enough capacity once blocked days are taken out: " + required
                            + " remote days needed, but only " + reachable + " can be placed — "
                            + tightestFree + " of " + input.people().size()
                            + " people are free on " + input.dayNames().get(tightestDay));
        }
    }

    private int freePeopleOn(int day, SolverInput input) {
        int free = 0;
        for (String person : input.people()) {
            Set<Integer> blocked = input.forbiddenDays().get(person);
            if (blocked == null || !blocked.contains(day)) free++;
        }
        return free;
    }

    /** One whole pass of the search, from an empty week. */
    private boolean search(int[] assigned,
                           List<List<Integer>> patternsPerPerson,
                           int[] preferredMasks,
                           SolverInput input,
                           int[] budget,
                           boolean wishesLead) {

        return backtrack(0, new int[input.dayCount()], assigned, patternsPerPerson,
                preferredMasks, input, budget, wishesLead);
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
                              int[] preferredMasks,
                              SolverInput input,
                              int[] budget,
                              boolean wishesLead) {

        if (personIndex == assigned.length) return true;
        if (budget != null && --budget[0] < 0) return false;

        int peopleLeft = assigned.length - personIndex;
        if (freeCapacity(used, input) < peopleLeft * input.remotesPerPerson()) return false;

        // Look ahead: once a day is full it is full for everybody, so anyone still waiting whose
        // every pattern touches a full day is already stuck and this branch is dead. Without
        // this the search only finds that out several people later and unwinds one step at a
        // time, which is what a week where everybody wants the same two days would cost.
        int fullDays = fullDays(used, input);
        for (int p = personIndex; p < assigned.length; p++) {
            if (!hasFittingPattern(patternsPerPerson.get(p), fullDays)) return false;
        }

        List<Integer> candidates = new ArrayList<>();
        for (int mask : patternsPerPerson.get(personIndex)) {
            if ((mask & fullDays) == 0) candidates.add(mask);
        }

        // Two orderings, whichever leads. Wishes: the patterns granting most of what this
        // person asked for first. Balance: the ones whose days are least busy first, so a week
        // nobody has a view on lands on 10/10/10/9/9 rather than 10/10/10/10/8. Both are only
        // orderings, so neither rules out a valid schedule — and with no wish the wish key is
        // the same for every candidate and this is the balance rule alone.
        int preferred = preferredMasks[personIndex];
        Comparator<Integer> byWish =
                Comparator.comparingInt(mask -> Integer.bitCount(preferred & ~mask));
        Comparator<Integer> byLoad = Comparator.comparingInt(mask -> currentLoad(mask, used));

        candidates.sort(wishesLead ? byWish.thenComparing(byLoad) : byLoad.thenComparing(byWish));

        for (int mask : candidates) {
            applyPattern(mask, used, 1);
            assigned[personIndex] = mask;

            if (backtrack(personIndex + 1, used, assigned, patternsPerPerson, preferredMasks,
                    input, budget, wishesLead)) {
                return true;
            }

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

    /** The days with no slot left, as a bitmask: a pattern fits when it touches none of them. */
    private int fullDays(int[] used, SolverInput input) {
        int full = 0;
        for (int d = 0; d < input.dayCount(); d++) {
            if (used[d] >= input.slotsPerDay()[d]) full |= (1 << d);
        }
        return full;
    }

    private boolean hasFittingPattern(List<Integer> patterns, int fullDays) {
        for (int mask : patterns) {
            if ((mask & fullDays) == 0) return true;
        }
        return false;
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
