package cires.dft.remotescheduler.solver;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything the solver needs to plan one week. Deliberately free of Spring and JPA types so
 * the algorithm can be unit tested on its own.
 *
 * @param people             the roster, in the order bit positions are assigned
 * @param dayNames           the working days, in order
 * @param slotsPerDay        remote capacity of each day, same length as {@code dayNames}
 * @param remotesPerPerson   how many remote days every person must get, exactly
 * @param maxConsecutiveDays the longest run of remote days a person may have
 * @param holidays           day indices with no remote work at all
 * @param forbiddenDays      per person, day indices they cannot be remote on
 * @param preferredDays      per person, day indices they asked for — a wish, never a constraint
 */
public record SolverInput(
        List<String> people,
        List<String> dayNames,
        int[] slotsPerDay,
        int remotesPerPerson,
        int maxConsecutiveDays,
        Set<Integer> holidays,
        Map<String, Set<Integer>> forbiddenDays,
        Map<String, Set<Integer>> preferredDays
) {

    public SolverInput {
        if (preferredDays == null) preferredDays = Map.of();
    }

    /** A week nobody has expressed a wish for. */
    public SolverInput(List<String> people,
                       List<String> dayNames,
                       int[] slotsPerDay,
                       int remotesPerPerson,
                       int maxConsecutiveDays,
                       Set<Integer> holidays,
                       Map<String, Set<Integer>> forbiddenDays) {

        this(people, dayNames, slotsPerDay, remotesPerPerson, maxConsecutiveDays,
                holidays, forbiddenDays, Map.of());
    }

    public int dayCount() {
        return dayNames.size();
    }

    /** Remote days that must be handed out: one per person per required remote day. */
    public int requiredPersonDays() {
        return people.size() * remotesPerPerson;
    }

    /** Remote days that can be handed out, holidays contributing nothing. */
    public int availableSlots() {
        int total = 0;
        for (int d = 0; d < dayCount(); d++) {
            if (!holidays.contains(d)) total += slotsPerDay[d];
        }
        return total;
    }

    /** The days {@code person} asked for, as a bitmask — 0 when they asked for nothing. */
    int preferredMask(String person) {
        int mask = 0;
        for (int day : preferredDays.getOrDefault(person, Set.of())) {
            mask |= (1 << day);
        }
        return mask;
    }
}
