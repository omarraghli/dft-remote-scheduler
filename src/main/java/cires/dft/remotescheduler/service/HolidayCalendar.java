package cires.dft.remotescheduler.service;

import cires.dft.remotescheduler.config.PublicHolidayProperties;
import cires.dft.remotescheduler.config.RemoteScheduleProperties;
import cires.dft.remotescheduler.domain.Holiday;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Which public holidays fall in a given week, and what they do to the quota.
 *
 * <p>Two sources, for two kinds of holiday. The national days never move, so they stay in
 * configuration as {@code MM-dd} rules that hold for any year. The lunar ones are announced days
 * before they fall, so they are rows an admin can edit while the app runs — those win over a
 * configured one on the same date.
 *
 * <p>Nobody works remotely on a holiday, so a short week cannot hand out the usual three remote
 * days each: one holiday leaves four working days, two leave three. The quota drops to match,
 * following {@code remote.public-holidays.quotas} — one holiday means two remote days per person,
 * two or more means one.
 *
 * <p>Holidays landing on a Saturday or a Sunday are ignored, since only the configured working
 * days have a column at all.
 */
@Component
public class HolidayCalendar {

    private final RemoteScheduleProperties schedule;
    private final PublicHolidayProperties holidays;
    private final DatedHolidays dated;

    public HolidayCalendar(RemoteScheduleProperties schedule,
                           PublicHolidayProperties holidays,
                           DatedHolidays dated) {
        this.schedule = schedule;
        this.holidays = holidays;
        this.dated = dated;
    }

    /** The holidays in the week containing {@code date}, in day order. */
    public List<PublicHoliday> inWeek(LocalDate date) {
        LocalDate monday = WeekStarts.of(date);
        List<String> dayNames = schedule.getDays();
        List<Holiday> stored = dated.all();
        List<PublicHoliday> found = new ArrayList<>();

        for (int index = 0; index < dayNames.size(); index++) {
            LocalDate day = monday.plusDays(index);
            String name = nameOf(day, stored);

            if (name != null) {
                found.add(new PublicHoliday(day, index, dayNames.get(index), name));
            }
        }

        return found;
    }

    /** The holiday columns of that week, which the solver takes as days with no remote work. */
    public Set<Integer> dayIndexes(LocalDate date) {
        Set<Integer> indexes = new LinkedHashSet<>();
        for (PublicHoliday holiday : inWeek(date)) {
            indexes.add(holiday.dayIndex());
        }
        return indexes;
    }

    /** Day index to holiday name, for the chart's column headings. */
    public Map<Integer, String> namesByDayIndex(LocalDate date) {
        Map<Integer, String> names = new LinkedHashMap<>();
        for (PublicHoliday holiday : inWeek(date)) {
            names.put(holiday.dayIndex(), holiday.name());
        }
        return names;
    }


    /** The holiday falling on exactly this date, or null — a weekend date included. */
    public String nameOn(LocalDate date) {
        return nameOf(date, dated.all());
    }

    /**
     * Remote days each person gets in that week: the configured quota, lowered when holidays make
     * it impossible. Never raised — a quota rule above the configured one is ignored.
     */
    public int remotesPerPerson(LocalDate date) {
        int configured = schedule.getRemotesPerPerson();
        int count = inWeek(date).size();
        if (count == 0) return configured;

        Map.Entry<Integer, Integer> rule =
                new TreeMap<>(holidays.getQuotas()).floorEntry(count);

        return rule == null ? configured : Math.min(configured, rule.getValue());
    }

    /** The holiday on that date, or {@code null}. A stored one wins over an annual rule. */
    private String nameOf(LocalDate date, List<Holiday> stored) {
        for (Holiday holiday : stored) {
            if (holiday.covers(date)) return holiday.getName();
        }

        MonthDay monthDay = MonthDay.from(date);
        for (PublicHolidayProperties.Annual holiday : holidays.getAnnual()) {
            if (monthDay.equals(holiday.monthDay())) return holiday.getName();
        }

        return null;
    }
}
