package cires.dft.remotescheduler.solver;

import java.util.List;
import java.util.Map;

/**
 * A solved week.
 *
 * @param peopleByDay for each day index, the people remote that day
 * @param daysByPerson for each person, the day indices they are remote on
 */
public record SolverResult(
        List<List<String>> peopleByDay,
        Map<String, List<Integer>> daysByPerson
) {
}
