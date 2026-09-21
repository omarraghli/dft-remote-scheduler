package cires.dft.remotescheduler.solver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WeekPatternsTest {

    @Test
    @DisplayName("a 5 day week with a 3 day quota and no 3 in a row has exactly 7 patterns")
    void sevenPatternsForTheStandardWeek() {
        List<Integer> patterns = WeekPatterns.forPerson(5, 3, 2, Set.of(), null);

        // 10 ways to pick 3 of 5 days, minus Mon-Tue-Wed, Tue-Wed-Thu and Wed-Thu-Fri
        assertThat(patterns).hasSize(7);
        assertThat(patterns).allSatisfy(mask -> assertThat(Integer.bitCount(mask)).isEqualTo(3));
    }

    @Test
    @DisplayName("holidays and forbidden days are excluded from every pattern")
    void blockedDaysNeverAppear() {
        List<Integer> patterns = WeekPatterns.forPerson(5, 3, 2, Set.of(0), Set.of(4));

        assertThat(patterns).allSatisfy(mask -> {
            assertThat((mask >> 0) & 1).isZero();
            assertThat((mask >> 4) & 1).isZero();
        });
    }

    @Test
    @DisplayName("blocking too many days leaves no pattern at all")
    void noPatternsWhenOverConstrained() {
        assertThat(WeekPatterns.forPerson(5, 3, 2, Set.of(0, 1, 2), null)).isEmpty();
    }

    @Test
    @DisplayName("run detection finds runs longer than the limit and allows runs at the limit")
    void runDetection() {
        // 0b00111 = Mon, Tue, Wed — a run of 3
        assertThat(WeekPatterns.hasRunLongerThan(0b00111, 2)).isTrue();
        // 0b01011 = Mon, Tue, Thu — longest run is 2
        assertThat(WeekPatterns.hasRunLongerThan(0b01011, 2)).isFalse();
        // with a limit of 1, two in a row is already too many
        assertThat(WeekPatterns.hasRunLongerThan(0b01011, 1)).isTrue();
        assertThat(WeekPatterns.hasRunLongerThan(0b10101, 1)).isFalse();
    }

    @Test
    @DisplayName("raising the consecutive limit admits more patterns")
    void higherLimitAdmitsMore() {
        int strict = WeekPatterns.forPerson(5, 3, 2, Set.of(), null).size();
        int relaxed = WeekPatterns.forPerson(5, 3, 3, Set.of(), null).size();

        assertThat(relaxed).isGreaterThan(strict);
        assertThat(relaxed).isEqualTo(10);
    }

    @Test
    @DisplayName("the most remote days a week can hold is what is left once the runs are counted")
    void maxRemoteDaysCountsWhatFits() {
        // Nothing blocked: four, taking Lundi, Mardi, Jeudi and Vendredi around a day in the
        // office. More than the quota anybody is given, which is why this only ever caps it.
        assertThat(WeekPatterns.maxRemoteDays(5, 2, 0b00000)).isEqualTo(4);

        // Away Lundi to Mercredi and back on the Jeudi: only Vendredi is left.
        assertThat(WeekPatterns.maxRemoteDays(5, 2, 0b01111)).isEqualTo(1);

        // Away Lundi, back Mardi: Mercredi to Vendredi is three in a row, so two of them.
        assertThat(WeekPatterns.maxRemoteDays(5, 2, 0b00011)).isEqualTo(2);

        // A day off in the middle costs nothing at all.
        assertThat(WeekPatterns.maxRemoteDays(5, 2, 0b01100)).isEqualTo(3);

        assertThat(WeekPatterns.maxRemoteDays(5, 2, 0b11111)).isZero();
    }
}
