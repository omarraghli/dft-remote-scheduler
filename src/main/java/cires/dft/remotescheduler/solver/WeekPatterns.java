package cires.dft.remotescheduler.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Enumerates the valid <em>week patterns</em> for one person: bitmasks over day indices where a
 * set bit means "remote that day".
 *
 * <p>Restricting the search to whole-week patterns is what keeps this solver small. A person's
 * pattern space is bounded by {@code 2^days} regardless of team size — for a 5 day week with a
 * 3 day quota and a 2 consecutive day limit, exactly 7 patterns survive. Both hard constraints
 * are properties of the pattern itself, so they are checked once here rather than at every node
 * of the search.
 */
public final class WeekPatterns {

    private WeekPatterns() {
    }

    /**
     * All masks with exactly {@code remotesPerPerson} bits set, no run longer than
     * {@code maxConsecutiveDays}, and no bit on a holiday or forbidden day.
     */
    public static List<Integer> forPerson(int dayCount,
                                          int remotesPerPerson,
                                          int maxConsecutiveDays,
                                          Set<Integer> holidays,
                                          Set<Integer> forbiddenDays) {

        int blockedMask = 0;
        for (int day : holidays) {
            blockedMask |= (1 << day);
        }
        if (forbiddenDays != null) {
            for (int day : forbiddenDays) {
                blockedMask |= (1 << day);
            }
        }

        List<Integer> patterns = new ArrayList<>();

        for (int mask = 0; mask < (1 << dayCount); mask++) {
            if (Integer.bitCount(mask) != remotesPerPerson) continue;
            if ((mask & blockedMask) != 0) continue;
            if (hasRunLongerThan(mask, maxConsecutiveDays)) continue;

            patterns.add(mask);
        }

        return patterns;
    }

    /**
     * True when {@code mask} contains {@code maxRun + 1} consecutive set bits.
     *
     * <p>Shifting the mask right and AND-ing it with itself once per extra day leaves a set bit
     * only where a run of that length starts, so a non-zero result means the limit is exceeded.
     * For the usual case of {@code maxRun == 2} this is {@code mask & (mask>>1) & (mask>>2)}.
     */
    static boolean hasRunLongerThan(int mask, int maxRun) {
        int overlap = mask;
        for (int shift = 1; shift <= maxRun; shift++) {
            overlap &= (mask >> shift);
            if (overlap == 0) return false;
        }
        return overlap != 0;
    }
}
