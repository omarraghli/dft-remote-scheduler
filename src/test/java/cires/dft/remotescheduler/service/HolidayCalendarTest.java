package cires.dft.remotescheduler.service;

import cires.dft.remotescheduler.config.PublicHolidayProperties;
import cires.dft.remotescheduler.config.RemoteScheduleProperties;
import cires.dft.remotescheduler.domain.Holiday;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain unit tests: the calendar is a function of its configuration, its holidays and a date. */
class HolidayCalendarTest {

    @Test
    @DisplayName("an annual holiday is found in any year, on the right column")
    void annualHolidayLandsOnItsDay() {
        HolidayCalendar calendar = calendar(annual("11-06", "Marche Verte"));

        List<PublicHoliday> found = calendar.inWeek(LocalDate.of(2026, 11, 2));

        assertThat(found).hasSize(1);
        assertThat(found.getFirst().date()).isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(found.getFirst().dayIndex()).isEqualTo(4);
        assertThat(found.getFirst().dayName()).isEqualTo("Vendredi");
        assertThat(found.getFirst().name()).isEqualTo("Marche Verte");

        // Same holiday, another year: in 2028 it falls on the Monday instead.
        assertThat(calendar.inWeek(LocalDate.of(2028, 11, 6)))
                .singleElement().extracting(PublicHoliday::dayIndex).isEqualTo(0);
    }

    @Test
    @DisplayName("any day of the week finds the same holidays")
    void resolvesFromAnyDayOfTheWeek() {
        HolidayCalendar calendar = calendar(annual("11-06", "Marche Verte"));

        assertThat(calendar.dayIndexes(LocalDate.of(2026, 11, 4))).containsExactly(4);
        assertThat(calendar.namesByDayIndex(LocalDate.of(2026, 11, 6)))
                .isEqualTo(Map.of(4, "Marche Verte"));
    }

    @Test
    @DisplayName("a stored holiday closes every day it runs")
    void aTwoDayHolidayClosesBothDays() {
        HolidayCalendar calendar = calendar(none(),
                holiday("Aïd Al Fitr", "2026-03-19", 2));

        List<PublicHoliday> found = calendar.inWeek(LocalDate.of(2026, 3, 16));

        assertThat(found).extracting(PublicHoliday::dayIndex).containsExactly(3, 4);
        assertThat(found).extracting(PublicHoliday::name)
                .containsExactly("Aïd Al Fitr", "Aïd Al Fitr");
    }

    @Test
    @DisplayName("a stored holiday wins over an annual one on the same date")
    void storedHolidayWins() {
        HolidayCalendar calendar = calendar(annual("03-20", "Nothing in particular"),
                holiday("Aïd Al Fitr", "2026-03-20", 1));

        assertThat(calendar.inWeek(LocalDate.of(2026, 3, 16)))
                .singleElement()
                .extracting(PublicHoliday::name).isEqualTo("Aïd Al Fitr");
    }

    @Test
    @DisplayName("a holiday falling on a weekend has no column, so it is ignored")
    void weekendHolidaysAreIgnored() {
        HolidayCalendar calendar = calendar(annual("01-09", "A Saturday"));

        assertThat(calendar.inWeek(LocalDate.of(2027, 1, 4))).isEmpty();
    }

    @Test
    @DisplayName("one holiday leaves two remote days, two or more leave one")
    void holidaysLowerTheQuota() {
        HolidayCalendar calendar = calendar(annual("11-06", "Marche Verte"),
                holiday("Aïd Al Adha", "2026-08-20", 2),
                holiday("Three days off", "2026-11-30", 3));

        assertThat(calendar.remotesPerPerson(LocalDate.of(2026, 9, 21))).isEqualTo(3);
        assertThat(calendar.remotesPerPerson(LocalDate.of(2026, 11, 2))).isEqualTo(2);
        assertThat(calendar.remotesPerPerson(LocalDate.of(2026, 8, 17))).isEqualTo(1);
        // Three holidays has no rule of its own and falls back to the one for two.
        assertThat(calendar.remotesPerPerson(LocalDate.of(2026, 11, 30))).isEqualTo(1);
    }

    @Test
    @DisplayName("the quota is never raised above the configured one")
    void quotaIsOnlyEverLowered() {
        RemoteScheduleProperties schedule = scheduleProperties();
        schedule.setRemotesPerPerson(1);

        HolidayCalendar calendar = new HolidayCalendar(
                schedule, annual("11-06", "Marche Verte"), List::of);

        assertThat(calendar.remotesPerPerson(LocalDate.of(2026, 11, 2))).isEqualTo(1);
    }

    @Test
    @DisplayName("a day that is not MM-dd is rejected by name")
    void rejectsAMalformedAnnualDay() {
        HolidayCalendar calendar = calendar(annual("6 novembre", "Marche Verte"));

        assertThatThrownBy(() -> calendar.inWeek(LocalDate.of(2026, 11, 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("6 novembre");
    }

    private HolidayCalendar calendar(PublicHolidayProperties holidays, Holiday... stored) {
        List<Holiday> dated = List.of(stored);
        return new HolidayCalendar(scheduleProperties(), holidays, () -> dated);
    }

    private RemoteScheduleProperties scheduleProperties() {
        RemoteScheduleProperties properties = new RemoteScheduleProperties();
        properties.setDays(List.of("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi"));
        properties.setRemotesPerPerson(3);
        return properties;
    }

    private PublicHolidayProperties none() {
        PublicHolidayProperties properties = new PublicHolidayProperties();
        properties.setQuotas(Map.of(1, 2, 2, 1));
        return properties;
    }

    private PublicHolidayProperties annual(String day, String name) {
        PublicHolidayProperties.Annual entry = new PublicHolidayProperties.Annual();
        entry.setDay(day);
        entry.setName(name);

        PublicHolidayProperties properties = none();
        properties.getAnnual().add(entry);
        return properties;
    }

    private Holiday holiday(String name, String startDate, int days) {
        return new Holiday(name, LocalDate.parse(startDate), days, Instant.EPOCH);
    }
}
