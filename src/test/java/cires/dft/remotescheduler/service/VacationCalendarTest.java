package cires.dft.remotescheduler.service;

import cires.dft.remotescheduler.config.PublicHolidayProperties;
import cires.dft.remotescheduler.config.RemoteScheduleProperties;
import cires.dft.remotescheduler.domain.Vacation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain unit tests: what a week looks like for somebody who is away part of it. */
class VacationCalendarTest {

    /** Monday 21 September 2026, a week with no holiday in it. */
    private static final LocalDate WEEK = LocalDate.of(2026, 9, 21);

    @Test
    @DisplayName("the days away come out of the week, and the day back is spent in the office")
    void awayUntilWednesdayLeavesOneRemoteDay() {
        VacationCalendar calendar = calendar(away("Sara", "2026-09-21", "2026-09-23"));

        assertThat(calendar.awayDays(WEEK)).containsEntry("Sara", Set.of(0, 1, 2));
        assertThat(calendar.returnDays(WEEK)).containsEntry("Sara", 3);
        assertThat(calendar.blockedDays(WEEK)).containsEntry("Sara", Set.of(0, 1, 2, 3));

        // Only Vendredi is left, so one remote day is all the week can owe them.
        assertThat(calendar.quotas(WEEK, Set.of(), 3)).containsEntry("Sara", 1);
    }

    @Test
    @DisplayName("a single day off costs nobody a remote day")
    void oneDayOffKeepsTheQuota() {
        VacationCalendar calendar = calendar(away("Omar", "2026-09-23", "2026-09-23"));

        assertThat(calendar.blockedDays(WEEK)).containsEntry("Omar", Set.of(2, 3));
        // Lundi, Mardi and Vendredi still hold three remote days without three in a row.
        assertThat(calendar.quotas(WEEK, Set.of(), 3)).isEmpty();
    }

    @Test
    @DisplayName("away all week is no remote days at all, and the day back falls in the next one")
    void awayAllWeek() {
        VacationCalendar calendar = calendar(away("Adam", "2026-09-21", "2026-09-25"));

        assertThat(calendar.awayDays(WEEK)).containsEntry("Adam", Set.of(0, 1, 2, 3, 4));
        assertThat(calendar.returnDays(WEEK)).isEmpty();
        assertThat(calendar.quotas(WEEK, Set.of(), 3)).containsEntry("Adam", 0);

        // Back on the Monday after, which is an office day.
        assertThat(calendar.returnDays(WEEK.plusWeeks(1))).containsEntry("Adam", 0);
        assertThat(calendar.quotas(WEEK.plusWeeks(1), Set.of(), 3)).isEmpty();
    }

    @Test
    @DisplayName("leave ending on a Friday puts them in the office on the Monday")
    void leaveEndingOnAFridayReturnsOnMonday() {
        VacationCalendar calendar = calendar(away("Nader", "2026-09-14", "2026-09-18"));

        assertThat(calendar.awayDays(WEEK)).isEmpty();
        assertThat(calendar.returnDays(WEEK)).containsEntry("Nader", 0);
    }

    @Test
    @DisplayName("a férié on the day back moves it to the next day the office is open")
    void aHolidayPushesTheDayBack() {
        // Wednesday 18 November 2026 — Fête de l'Indépendance.
        VacationCalendar calendar = calendar(annual("11-18", "Fête de l'Indépendance"),
                away("Hamza", "2026-11-16", "2026-11-17"));

        LocalDate week = LocalDate.of(2026, 11, 16);

        assertThat(calendar.awayDays(week)).containsEntry("Hamza", Set.of(0, 1));
        assertThat(calendar.returnDays(week)).containsEntry("Hamza", 3);
    }

    @Test
    @DisplayName("a weekend inside a stretch of leave takes care of itself")
    void aStretchAcrossTwoWeeks() {
        VacationCalendar calendar = calendar(away("Salma", "2026-09-17", "2026-09-22"));

        assertThat(calendar.awayDays(LocalDate.of(2026, 9, 14)))
                .containsEntry("Salma", Set.of(3, 4));
        assertThat(calendar.awayDays(WEEK)).containsEntry("Salma", Set.of(0, 1));
        assertThat(calendar.returnDays(WEEK)).containsEntry("Salma", 2);
    }

    @Test
    @DisplayName("the quota is only ever lowered, never raised above the week's own")
    void neverRaisesTheQuota() {
        VacationCalendar calendar = calendar(away("Ayoub", "2026-11-16", "2026-11-16"));

        // A one-holiday week is already down to two remote days; leave cannot hand back a third.
        assertThat(calendar.quotas(LocalDate.of(2026, 11, 16), Set.of(2), 2)).isEmpty();
    }

    private VacationCalendar calendar(Vacation... away) {
        return calendar(none(), away);
    }

    private VacationCalendar calendar(PublicHolidayProperties holidays, Vacation... away) {
        List<Vacation> vacations = List.of(away);
        RemoteScheduleProperties properties = scheduleProperties();

        return new VacationCalendar(properties,
                new HolidayCalendar(properties, holidays, List::of),
                () -> vacations);
    }

    private RemoteScheduleProperties scheduleProperties() {
        RemoteScheduleProperties properties = new RemoteScheduleProperties();
        properties.setDays(List.of("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi"));
        properties.setRemotesPerPerson(3);
        properties.setMaxConsecutiveDays(2);
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

    private Vacation away(String person, String from, String until) {
        return new Vacation(person, LocalDate.parse(from), LocalDate.parse(until), Instant.EPOCH);
    }
}
