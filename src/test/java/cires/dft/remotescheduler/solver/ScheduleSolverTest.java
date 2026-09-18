package cires.dft.remotescheduler.solver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduleSolverTest {

    private static final List<String> DAYS =
            List.of("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi");

    private static final List<String> TEAM = List.of(
            "Sara", "Rajae", "Salma", "Adam",
            "Oussama", "Outman", "Ayoub", "Omar",
            "Nassim", "Adnan", "Anass", "Mahmoud",
            "Hamza", "Nader", "MedAli", "MedKhalil");

    private final ScheduleSolver solver = new ScheduleSolver();

    private SolverInput standardInput() {
        return new SolverInput(TEAM, DAYS, new int[]{10, 10, 10, 10, 10}, 3, 2,
                Set.of(), Map.of());
    }

    @Test
    @DisplayName("every person gets exactly their quota")
    void everyoneGetsTheirQuota() {
        SolverResult result = solver.solve(standardInput(), new Random(42));

        assertThat(result.daysByPerson()).hasSize(16);
        assertThat(result.daysByPerson().values()).allSatisfy(days -> assertThat(days).hasSize(3));
    }

    @Test
    @DisplayName("no day exceeds its slot capacity")
    void capacityIsRespected() {
        SolverResult result = solver.solve(standardInput(), new Random(7));

        assertThat(result.peopleByDay()).hasSize(5);
        assertThat(result.peopleByDay()).allSatisfy(people -> assertThat(people).hasSizeLessThanOrEqualTo(10));
    }

    @Test
    @DisplayName("nobody is remote three days in a row")
    void noThreeConsecutiveDays() {
        SolverResult result = solver.solve(standardInput(), new Random(1234));

        result.daysByPerson().forEach((person, days) -> {
            for (int i = 0; i + 2 < days.size(); i++) {
                boolean run = days.get(i) + 1 == days.get(i + 1)
                        && days.get(i + 1) + 1 == days.get(i + 2);

                assertThat(run)
                        .as("%s is remote on 3 consecutive days %s", person, days)
                        .isFalse();
            }
        });
    }

    @Test
    @DisplayName("all 48 remote days are handed out")
    void allRemoteDaysAreAssigned() {
        SolverResult result = solver.solve(standardInput(), new Random(99));

        int total = result.peopleByDay().stream().mapToInt(List::size).sum();
        assertThat(total).isEqualTo(16 * 3);
    }

    @Test
    @DisplayName("the week is balanced to within one person per day")
    void daysAreBalanced() {
        SolverResult result = solver.solve(standardInput(), new Random(5));

        List<Integer> loads = result.peopleByDay().stream().map(List::size).toList();
        int min = loads.stream().mapToInt(Integer::intValue).min().orElseThrow();
        int max = loads.stream().mapToInt(Integer::intValue).max().orElseThrow();

        assertThat(max - min)
                .as("day loads %s should differ by at most one", loads)
                .isLessThanOrEqualTo(1);
    }

    @ParameterizedTest(name = "seed {0} produces a valid week")
    @ValueSource(longs = {0, 1, 2, 3, 17, 256, 9999, 123456})
    @DisplayName("every seed produces a schedule satisfying all constraints")
    void everySeedIsValid(long seed) {
        SolverResult result = solver.solve(standardInput(), new Random(seed));

        assertThat(result.peopleByDay()).allSatisfy(p -> assertThat(p).hasSizeLessThanOrEqualTo(10));
        assertThat(result.daysByPerson().values()).allSatisfy(d -> assertThat(d).hasSize(3));

        result.daysByPerson().forEach((person, days) -> {
            for (int i = 0; i + 2 < days.size(); i++) {
                assertThat(days.get(i) + 1 == days.get(i + 1)
                        && days.get(i + 1) + 1 == days.get(i + 2)).isFalse();
            }
        });
    }

    @Test
    @DisplayName("a person cannot be remote on their vacation return day")
    void vacationReturnsAreRespected() {
        SolverInput input = new SolverInput(TEAM, DAYS, new int[]{10, 10, 10, 10, 10}, 3, 2,
                Set.of(), Map.of("Sara", Set.of(0), "Omar", Set.of(4)));

        SolverResult result = solver.solve(input, new Random(3));

        assertThat(result.daysByPerson().get("Sara")).doesNotContain(0);
        assertThat(result.daysByPerson().get("Omar")).doesNotContain(4);
    }

    @Test
    @DisplayName("a holiday leaves the standard week infeasible, and says why")
    void holidayMakesTheWeekInfeasible() {
        SolverInput input = new SolverInput(TEAM, DAYS, new int[]{10, 10, 10, 10, 10}, 3, 2,
                Set.of(0), Map.of());

        assertThatThrownBy(() -> solver.solve(input, new Random(1)))
                .isInstanceOf(NoFeasibleScheduleException.class)
                .hasMessageContaining("Not enough capacity")
                .hasMessageContaining("48")
                .hasMessageContaining("40");
    }

    @Test
    @DisplayName("a holiday works once the quota is lowered to match")
    void holidayWorksWithALowerQuota() {
        SolverInput input = new SolverInput(TEAM, DAYS, new int[]{10, 10, 10, 10, 10}, 2, 2,
                Set.of(0), Map.of());

        SolverResult result = solver.solve(input, new Random(11));

        assertThat(result.peopleByDay().get(0)).isEmpty();
        assertThat(result.daysByPerson().values()).allSatisfy(d -> assertThat(d).hasSize(2));
    }

    @Test
    @DisplayName("blocking every viable day for one person is reported against that person")
    void impossiblePersonIsNamed() {
        SolverInput input = new SolverInput(TEAM, DAYS, new int[]{10, 10, 10, 10, 10}, 3, 2,
                Set.of(), Map.of("Nader", Set.of(0, 1, 2)));

        assertThatThrownBy(() -> solver.solve(input, new Random(1)))
                .isInstanceOf(NoFeasibleScheduleException.class)
                .hasMessageContaining("Nader");
    }

    @Test
    @DisplayName("shuffling means two runs differ")
    void runsVary() {
        SolverResult first = solver.solve(standardInput(), new Random(1));
        SolverResult second = solver.solve(standardInput(), new Random(2));

        assertThat(first.daysByPerson()).isNotEqualTo(second.daysByPerson());
    }

    @Test
    @DisplayName("the solver scales well past the current team size")
    void scalesToALargerTeam() {
        List<String> bigTeam = java.util.stream.IntStream.range(0, 200)
                .mapToObj(i -> "Person" + i)
                .toList();

        SolverInput input = new SolverInput(bigTeam, DAYS,
                new int[]{120, 120, 120, 120, 120}, 3, 2, Set.of(), Map.of());

        long start = System.nanoTime();
        SolverResult result = solver.solve(input, new Random(8));
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertThat(result.daysByPerson()).hasSize(200);
        assertThat(millis).as("solved in %d ms", millis).isLessThan(2_000);
    }
}
