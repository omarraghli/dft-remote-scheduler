package cires.dft.remotescheduler.service;

import java.util.Map;
import java.util.Set;

/**
 * What one week has been asked for and what it requires, by person.
 *
 * <p>The two are deliberately carried together: they are always read together — the solver needs
 * both, the page draws both — and a day can be in either without being in the other.
 *
 * @param preferred day indices each person would rather be remote on; a wish
 * @param onSite    day indices each person has to be in the office on; hard
 */
public record WeekPlan(Map<String, Set<Integer>> preferred, Map<String, Set<Integer>> onSite) {

    public static WeekPlan empty() {
        return new WeekPlan(Map.of(), Map.of());
    }

    public Set<Integer> preferredFor(String person) {
        return preferred.getOrDefault(person, Set.of());
    }

    public Set<Integer> onSiteFor(String person) {
        return onSite.getOrDefault(person, Set.of());
    }
}
