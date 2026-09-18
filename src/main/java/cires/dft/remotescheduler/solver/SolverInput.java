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
 */
public record SolverInput(
        List<String> people,
        List<String> dayNames,
        int[] slotsPerDay,
        int remotesPerPerson,
        int maxConsecutiveDays,
        Set<Integer> holidays,
        Map<String, Set<Integer>> forbiddenDays
) {

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
}
