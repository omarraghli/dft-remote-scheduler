package cires.dft.remotescheduler.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class WeekLabelsTest {

    private static final LocalDate NOW = LocalDate.of(2026, 10, 5);

    @Test
    @DisplayName("the shown week is named by how far it is from this one")
    void relative() {
        assertThat(WeekLabels.relative(NOW, NOW)).isEqualTo("This week");
        assertThat(WeekLabels.relative(NOW.plusWeeks(1), NOW)).isEqualTo("Next week");
        assertThat(WeekLabels.relative(NOW.plusWeeks(3), NOW)).isEqualTo("In 3 weeks");
        assertThat(WeekLabels.relative(NOW.minusWeeks(1), NOW)).isEqualTo("Last week");
        assertThat(WeekLabels.relative(NOW.minusWeeks(2), NOW)).isEqualTo("2 weeks ago");
    }

    @Test
    @DisplayName("each working day carries its date, in French")
    void dayDates() {
        assertThat(WeekLabels.dayDates(NOW, 5))
                .containsExactly("5 oct.", "6 oct.", "7 oct.", "8 oct.", "9 oct.");
    }
}
