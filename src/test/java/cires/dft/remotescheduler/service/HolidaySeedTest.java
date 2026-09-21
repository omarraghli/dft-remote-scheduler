package cires.dft.remotescheduler.service;

import cires.dft.remotescheduler.AbstractPostgresIntegrationTest;
import cires.dft.remotescheduler.domain.Holiday;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "remote.job.enabled=false")
class HolidaySeedTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private HolidaySeed seed;

    @Autowired
    private HolidayService holidays;

    @Test
    @DisplayName("running again adds nothing while the configured dates are all stored")
    void idempotent() {
        List<LocalDate> before = starts();

        seed.run(null);

        assertThat(starts()).isEqualTo(before);
    }

    @Test
    @DisplayName("a holiday past the end of the calendar is added on the next start")
    void topsUpTheTail() {
        Holiday last = holidays.all().getLast();
        holidays.delete(last.getId());

        seed.run(null);

        Holiday restored = holidays.all().getLast();
        assertThat(restored.getStartDate()).isEqualTo(last.getStartDate());
        assertThat(restored.getName()).isEqualTo(last.getName());
        assertThat(restored.getDays()).isEqualTo(last.getDays());
    }

    @Test
    @DisplayName("a holiday an admin deleted inside the covered range stays deleted")
    void leavesTheCuratedRangeAlone() {
        List<Holiday> all = holidays.all();
        Holiday middle = all.get(all.size() / 2);
        holidays.delete(middle.getId());

        seed.run(null);

        assertThat(starts()).doesNotContain(middle.getStartDate());

        holidays.add(middle.getName(), middle.getStartDate(), middle.getDays());
    }

    /**
     * The second assertion is a canary: it starts failing six months before the shipped dates
     * run out, which is the point at which somebody has to add the next announced Aïds.
     */
    @Test
    @DisplayName("coverage is the last day the calendar accounts for, and is not running low")
    void reportsCoverage() {
        assertThat(holidays.coveredUntil()).isEqualTo(holidays.all().getLast().endDate());
        assertThat(holidays.coverageRunsLow()).isFalse();
    }

    private List<LocalDate> starts() {
        return holidays.all().stream().map(Holiday::getStartDate).toList();
    }
}
