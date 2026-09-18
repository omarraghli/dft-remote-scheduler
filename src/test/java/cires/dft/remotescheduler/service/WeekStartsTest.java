package cires.dft.remotescheduler.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class WeekStartsTest {

    @Test
    @DisplayName("any day of the week maps to that week's Monday")
    void normalisesToMonday() {
        assertThat(WeekStarts.of(LocalDate.of(2026, 9, 21))).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(WeekStarts.of(LocalDate.of(2026, 9, 24))).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(WeekStarts.of(LocalDate.of(2026, 9, 27))).isEqualTo(LocalDate.of(2026, 9, 21));
    }

    @Test
    @DisplayName("the Thursday job plans the following Monday")
    void thursdayPlansNextWeek() {
        LocalDate thursday = LocalDate.of(2026, 9, 17);
        assertThat(thursday.getDayOfWeek().getValue()).isEqualTo(4);

        assertThat(WeekStarts.next(thursday)).isEqualTo(LocalDate.of(2026, 9, 21));
    }

    @Test
    @DisplayName("running on a Monday plans the next Monday, not the current one")
    void mondayPlansTheWeekAfter() {
        assertThat(WeekStarts.next(LocalDate.of(2026, 9, 21))).isEqualTo(LocalDate.of(2026, 9, 28));
    }
}
