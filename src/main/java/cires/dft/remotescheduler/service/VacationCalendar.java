package cires.dft.remotescheduler.service;

import cires.dft.remotescheduler.config.RemoteScheduleProperties;
import cires.dft.remotescheduler.domain.Vacation;
import cires.dft.remotescheduler.solver.WeekPatterns;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * What somebody's leave does to the week it falls in.
 *
 * <p>Three things, and they are all one rule seen from different sides: the days away are not
 * theirs to be remote on, the first day they are back is one they spend in the office, and the
 * remote days they are owed shrink to what the days left can actually hold.
 *
 * <p>The last of those is the reason this is not simply another blocked day. Away Monday to
 * Wednesday leaves Thursday and Friday; Thursday is the day back, so only Friday is left, and
 * asking for the usual three remote days has no answer at all. The quota is capped at what fits
 * rather than the week failing — and it is capped, never raised, so a short leave changes
 * nothing for anybody who still has room for their three.
 *
 * <p>The day back is worked out by walking forward from the end of the leave to the first day
 * the office is actually open, so leave ending on a Friday puts them in on Monday, and on the
 * Tuesday if that Monday is a férié.
 */
@Component
public class VacationCalendar {

    /** Far enough to step over a weekend, a holiday and whatever follows them. */
    private static final int LOOKAHEAD_DAYS = 21;

    private final RemoteScheduleProperties schedule;
    private final HolidayCalendar holidays;
    private final PersonVacations vacations;

    public VacationCalendar(RemoteScheduleProperties schedule,
                            HolidayCalendar holidays,
                            PersonVacations vacations) {
        this.schedule = schedule;
        this.holidays = holidays;
        this.vacations = vacations;
    }

    /** Day indices each person is away for, in the week containing {@code date}. */
    public Map<String, Set<Integer>> awayDays(LocalDate date) {
        LocalDate monday = WeekStarts.of(date);
        Map<String, Set<Integer>> away = new LinkedHashMap<>();

        for (Vacation vacation : vacations.all()) {
            for (int index = 0; index < schedule.getDays().size(); index++) {
                if (vacation.covers(monday.plusDays(index))) {
                    away.computeIfAbsent(vacation.getPersonName(), k -> new TreeSet<>())
                            .add(index);
                }
            }
        }

        return away;
    }

    /** The first day back, per person, for the leave that ends into this week. */
    public Map<String, Integer> returnDays(LocalDate date) {
        LocalDate monday = WeekStarts.of(date);
        LocalDate lastDay = monday.plusDays(schedule.getDays().size() - 1L);
        Map<String, Integer> back = new LinkedHashMap<>();

        for (Vacation vacation : vacations.all()) {
            LocalDate firstBack = firstOpenDayFrom(vacation.dayAfter());
            if (firstBack == null || firstBack.isBefore(monday) || firstBack.isAfter(lastDay)) {
                continue;
            }

            back.put(vacation.getPersonName(), (int) (firstBack.toEpochDay() - monday.toEpochDay()));
        }

        return back;
    }

    /**
     * Every day this week that is not theirs to be remote on: the ones they are away for, and
     * the one they come back on.
     */
    public Map<String, Set<Integer>> blockedDays(LocalDate date) {
        Map<String, Set<Integer>> blocked = new HashMap<>();

        awayDays(date).forEach((person, days) ->
                blocked.computeIfAbsent(person, k -> new TreeSet<>()).addAll(days));

        returnDays(date).forEach((person, day) ->
                blocked.computeIfAbsent(person, k -> new TreeSet<>()).add(day));

        return blocked;
    }

    /**
     * A quota of their own for anybody whose week cannot hold the usual one. Only people who
     * differ appear, so a week nobody is away in produces nothing at all.
     *
     * @param closedDays day indices nobody works on — holidays and standing closures
     * @param weekQuota  what everybody else gets that week
     */
    public Map<String, Integer> quotas(LocalDate date, Set<Integer> closedDays, int weekQuota) {
        Map<String, Integer> quotas = new LinkedHashMap<>();
        int dayCount = schedule.getDays().size();

        int closedMask = 0;
        for (int day : closedDays) {
            closedMask |= (1 << day);
        }

        for (Map.Entry<String, Set<Integer>> entry : blockedDays(date).entrySet()) {
            int blocked = closedMask;
            for (int day : entry.getValue()) {
                blocked |= (1 << day);
            }

            int fits = WeekPatterns.maxRemoteDays(dayCount, schedule.getMaxConsecutiveDays(),
                    blocked);

            if (fits < weekQuota) quotas.put(entry.getKey(), fits);
        }

        return quotas;
    }

    /** Whether the office is open to that person's column at all on this date. */
    private boolean isOpen(LocalDate date) {
        List<String> dayNames = schedule.getDays();

        int index = date.getDayOfWeek().getValue() - 1;
        if (index >= dayNames.size()) return false;
        if (holidays.nameOn(date) != null) return false;

        return schedule.getHolidays().stream()
                .noneMatch(closed -> closed.equalsIgnoreCase(dayNames.get(index)));
    }

    private LocalDate firstOpenDayFrom(LocalDate date) {
        for (int step = 0; step < LOOKAHEAD_DAYS; step++) {
            LocalDate candidate = date.plusDays(step);
            if (isOpen(candidate)) return candidate;
        }
        return null;
    }
}
