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
    @DisplayName("a person is never given a day they are blocked on")
    void blockedDaysAreRespected() {
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

    @Test
    @DisplayName("a week nobody has a view on comes out exactly as it did before wishes existed")
    void emptyPreferencesChangeNothing() {
        SolverInput withoutWishes = standardInput();
        SolverInput withEmptyWishes = new SolverInput(TEAM, DAYS, new int[]{10, 10, 10, 10, 10},
                3, 2, Set.of(), Map.of(), Map.of());

        assertThat(solver.solve(withEmptyWishes, new Random(5)).daysByPerson())
                .isEqualTo(solver.solve(withoutWishes, new Random(5)).daysByPerson());
    }

    @Test
    @DisplayName("the days somebody asked for are the days they get, when the week has room")
    void aPreferenceIsGrantedWhenItFits() {
        SolverInput input = new SolverInput(TEAM, DAYS, new int[]{10, 10, 10, 10, 10}, 3, 2,
                Set.of(), Map.of(), Map.of("Sara", Set.of(0, 2)));

        SolverResult result = solver.solve(input, new Random(21));

        assertThat(result.daysByPerson().get("Sara")).contains(0, 2);
    }

    @Test
    @DisplayName("everybody wanting the same day still leaves everybody their quota")
    void oversubscribedPreferencesStillGiveEveryoneTheirQuota() {
        Map<String, Set<Integer>> allWantMonday = TEAM.stream()
                .collect(java.util.stream.Collectors.toMap(person -> person, person -> Set.of(0)));

        SolverInput input = new SolverInput(TEAM, DAYS, new int[]{10, 10, 10, 10, 10}, 3, 2,
                Set.of(), Map.of(), allWantMonday);

        SolverResult result = solver.solve(input, new Random(13));

        assertThat(result.peopleByDay().get(0)).hasSize(10);
        assertThat(result.daysByPerson().values()).allSatisfy(days -> assertThat(days).hasSize(3));

        result.daysByPerson().forEach((person, days) -> {
            for (int i = 0; i + 2 < days.size(); i++) {
                assertThat(days.get(i) + 1 == days.get(i + 1)
                        && days.get(i + 1) + 1 == days.get(i + 2))
                        .as("%s has three days in a row", person).isFalse();
            }
        });
    }

    @Test
    @DisplayName("the whole team wanting the same two days does not slow the search down")
    void popularDaysDoNotBlowUpTheSearch() {
        Map<String, Set<Integer>> allWantTheEnds = TEAM.stream()
                .collect(java.util.stream.Collectors.toMap(person -> person,
                        person -> Set.of(0, 4)));

        SolverInput input = new SolverInput(TEAM, DAYS, new int[]{10, 10, 10, 10, 10}, 3, 2,
                Set.of(), Map.of(), allWantTheEnds);

        long start = System.nanoTime();
        SolverResult result = solver.solve(input, new Random(4));
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertThat(result.daysByPerson()).hasSize(16);
        assertThat(millis).as("solved in %d ms", millis).isLessThan(500);
    }

    @Test
    @DisplayName("a day nobody can be remote on is a wish that goes unheard, not a failure")
    void aPreferenceOnAHolidayIsIgnored() {
        Map<String, Set<Integer>> allWantTheHoliday = TEAM.stream()
                .collect(java.util.stream.Collectors.toMap(person -> person, person -> Set.of(0)));

        SolverInput input = new SolverInput(TEAM, DAYS, new int[]{10, 10, 10, 10, 10}, 2, 2,
                Set.of(0), Map.of(), allWantTheHoliday);

        SolverResult result = solver.solve(input, new Random(6));

        assertThat(result.peopleByDay().get(0)).isEmpty();
        assertThat(result.daysByPerson().values()).allSatisfy(days -> assertThat(days).hasSize(2));
    }

    @Test
    @DisplayName("a day somebody is held in the office on is never given, however much they want it")
    void beingHeldInTheOfficeBeatsWantingTheDay() {
        SolverInput input = new SolverInput(TEAM, DAYS, new int[]{10, 10, 10, 10, 10}, 3, 2,
                Set.of(), Map.of("Sara", Set.of(1)), Map.of("Sara", Set.of(1, 3)));

        SolverResult result = solver.solve(input, new Random(17));

        assertThat(result.daysByPerson().get("Sara")).doesNotContain(1).contains(3);
    }

    @Test
    @DisplayName("holding one person in the office on Monday and Friday leaves them no week")
    void holdingSomeoneOnBothEndsOfTheWeekIsReportedAgainstThem() {
        SolverInput input = new SolverInput(TEAM, DAYS, new int[]{10, 10, 10, 10, 10}, 3, 2,
                Set.of(), Map.of("Nader", Set.of(0, 4)));

        assertThatThrownBy(() -> solver.solve(input, new Random(1)))
                .isInstanceOf(NoFeasibleScheduleException.class)
                .hasMessageContaining("Nader");
    }

    @Test
    @DisplayName("too many people held on one day is explained by the day, not by a dead end")
    void tooManyPeopleHeldOnOneDayIsExplained() {
        Map<String, Set<Integer>> heldOnMonday = TEAM.subList(0, 9).stream()
                .collect(java.util.stream.Collectors.toMap(person -> person, person -> Set.of(0)));

        SolverInput input = new SolverInput(TEAM, DAYS, new int[]{10, 10, 10, 10, 10}, 3, 2,
                Set.of(), heldOnMonday);

        assertThatThrownBy(() -> solver.solve(input, new Random(1)))
                .isInstanceOf(NoFeasibleScheduleException.class)
                .hasMessageContaining("Lundi")
                .hasMessageContaining("blocked days");
    }
}
