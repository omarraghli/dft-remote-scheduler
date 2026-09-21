package cires.dft.remotescheduler.service;

import cires.dft.remotescheduler.AbstractPostgresIntegrationTest;
import cires.dft.remotescheduler.domain.Holiday;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = "remote.job.enabled=false")
class HolidayServiceTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private HolidayService holidays;

    @Test
    @DisplayName("the configured dates seed the table, folding a two-day Aïd into one entry")
    void seededFromConfiguration() {
        assertThat(holidays.all()).isNotEmpty();

        Holiday aid = holidays.all().stream()
                .filter(h -> h.getStartDate().equals(LocalDate.of(2026, 3, 20)))
                .findFirst().orElseThrow();

        assertThat(aid.getName()).isEqualTo("Aïd Al Fitr");
        assertThat(aid.getDays()).isEqualTo(2);
        assertThat(aid.endDate()).isEqualTo(LocalDate.of(2026, 3, 21));
    }

    @Test
    @DisplayName("a holiday can be added, edited and removed")
    void fullLifecycle() {
        Holiday added = holidays.add("  Announced late  ", LocalDate.of(2029, 4, 3), 1);

        assertThat(added.getId()).isNotNull();
        assertThat(added.getName()).isEqualTo("Announced late");

        holidays.update(added.getId(), "Moved and lengthened", LocalDate.of(2029, 4, 4), 2);

        Holiday reloaded = holidays.require(added.getId());
        assertThat(reloaded.getName()).isEqualTo("Moved and lengthened");
        assertThat(reloaded.getStartDate()).isEqualTo(LocalDate.of(2029, 4, 4));
        assertThat(reloaded.endDate()).isEqualTo(LocalDate.of(2029, 4, 5));
        assertThat(reloaded.dates()).hasSize(2);

        holidays.delete(added.getId());

        assertThatThrownBy(() -> holidays.require(added.getId()))
                .isInstanceOf(HolidayManagementException.class);
    }

    @Test
    @DisplayName("the same holiday entered twice is refused, by name")
    void overlapIsRefused() {
        Holiday first = holidays.add("Aïd, entered once", LocalDate.of(2029, 5, 7), 2);

        try {
            assertThatThrownBy(() ->
                    holidays.add("Aïd, entered again", LocalDate.of(2029, 5, 8), 1))
                    .isInstanceOf(HolidayManagementException.class)
                    .hasMessageContaining("Aïd, entered once");

            // Editing it without moving it is still allowed: it does not overlap itself.
            holidays.update(first.getId(), "Aïd, renamed", LocalDate.of(2029, 5, 7), 2);
            assertThat(holidays.require(first.getId()).getName()).isEqualTo("Aïd, renamed");

        } finally {
            holidays.delete(first.getId());
        }
    }

    @Test
    @DisplayName("a nameless holiday, or one of no length, is refused")
    void validatesNameAndLength() {
        assertThatThrownBy(() -> holidays.add("  ", LocalDate.of(2029, 6, 4), 1))
                .isInstanceOf(HolidayManagementException.class)
                .hasMessageContaining("name");

        assertThatThrownBy(() -> holidays.add("Too long", LocalDate.of(2029, 6, 4), 15))
                .isInstanceOf(HolidayManagementException.class)
                .hasMessageContaining("1 and 14");

        assertThatThrownBy(() -> holidays.add("No days", LocalDate.of(2029, 6, 4), 0))
                .isInstanceOf(HolidayManagementException.class);
    }
}
