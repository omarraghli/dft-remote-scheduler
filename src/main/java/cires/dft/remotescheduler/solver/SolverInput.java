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
 * @param personalQuotas     per person, a quota of their own, for anybody the ordinary one
 *                           cannot apply to; everybody absent from here gets
 *                           {@code remotesPerPerson}
 */
public record SolverInput(
        List<String> people,
        List<String> dayNames,
        int[] slotsPerDay,
        int remotesPerPerson,
        int maxConsecutiveDays,
        Set<Integer> holidays,
        Map<String, Set<Integer>> forbiddenDays,
        Map<String, Set<Integer>> preferredDays,
        Map<String, Integer> personalQuotas
) {

    public SolverInput {
        if (preferredDays == null) preferredDays = Map.of();
        if (personalQuotas == null) personalQuotas = Map.of();
    }

    /** A week nobody has expressed a wish for, and where the quota is the same for everybody. */
    public SolverInput(List<String> people,
                       List<String> dayNames,
                       int[] slotsPerDay,
                       int remotesPerPerson,
                       int maxConsecutiveDays,
                       Set<Integer> holidays,
                       Map<String, Set<Integer>> forbiddenDays) {

        this(people, dayNames, slotsPerDay, remotesPerPerson, maxConsecutiveDays,
                holidays, forbiddenDays, Map.of(), Map.of());
    }

    /** A week with wishes but no quota of anybody's own. */
    public SolverInput(List<String> people,
                       List<String> dayNames,
                       int[] slotsPerDay,
                       int remotesPerPerson,
                       int maxConsecutiveDays,
                       Set<Integer> holidays,
                       Map<String, Set<Integer>> forbiddenDays,
                       Map<String, Set<Integer>> preferredDays) {

        this(people, dayNames, slotsPerDay, remotesPerPerson, maxConsecutiveDays,
                holidays, forbiddenDays, preferredDays, Map.of());
    }

    /** How many remote days this person must get, exactly. */
    public int quotaFor(String person) {
        return personalQuotas.getOrDefault(person, remotesPerPerson);
    }

    public int dayCount() {
        return dayNames.size();
    }

    /** Remote days that must be handed out, which is not people × quota once somebody is away. */
    public int requiredPersonDays() {
        int required = 0;
        for (String person : people) {
            required += quotaFor(person);
        }
        return required;
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
