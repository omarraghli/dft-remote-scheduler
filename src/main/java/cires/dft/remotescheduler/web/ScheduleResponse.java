package cires.dft.remotescheduler.web;

import cires.dft.remotescheduler.config.RemoteScheduleProperties;
import cires.dft.remotescheduler.domain.WeekSchedule;
import cires.dft.remotescheduler.service.HolidayCalendar;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** The JSON shape of a stored schedule, and what the page renders. */
public record ScheduleResponse(
        Long id,
        LocalDate weekStart,
        Instant generatedAt,
        String generatedBy,
        int totalRemoteDays,
        int remotesPerPerson,
        List<DayView> days,
        List<PersonRow> roster,
        Map<String, Integer> remoteDaysPerPerson
) {

    /** One day of the week, with the people remote on it — and the holiday closing it, if any. */
    public record DayView(int index, String name, String holiday,
                          int capacity, int count, List<String> people) {

        /** How full the day is, 0-100, for the meter under each column heading. */
        public int fillPercent() {
            return capacity == 0 ? 0 : Math.round(count * 100f / capacity);
        }
    }

    /**
     * One person's week, as a cell per day.
     *
     * <p>This is the view the day-by-day lists could not give: a person's days read straight
     * across, so the quota and any run of consecutive days are visible at a glance.
     */
    public record PersonRow(String name, List<Cell> cells, int count) {
    }

    /**
     * One person on one day.
     *
     * @param remote    whether they work remotely that day
     * @param joinLeft  the previous day is also remote — the two are drawn as one bar
     * @param joinRight the next day is also remote
     */
    public record Cell(int dayIndex, boolean remote, boolean joinLeft, boolean joinRight) {
    }

    /**
     * Builds the view from the stored schedule, laying the days out from the current
     * configuration so a day nobody was assigned to still appears, empty, rather than vanishing.
     */
    public static ScheduleResponse from(WeekSchedule schedule,
                                        Collection<String> roster,
                                        RemoteScheduleProperties props,
                                        HolidayCalendar holidays) {

        Map<Integer, List<String>> peopleByDay = schedule.peopleByDayIndex();
        List<String> dayNames = props.getDays();
        Map<Integer, String> holidayNames = holidays.namesByDayIndex(schedule.getWeekStart());

        List<DayView> days = new ArrayList<>(dayNames.size());
        List<Set<String>> remoteOnDay = new ArrayList<>(dayNames.size());
        int total = 0;

        for (int index = 0; index < dayNames.size(); index++) {
            List<String> people = peopleByDay.getOrDefault(index, List.of());
            int capacity = index < props.getSlotsPerDay().size()
                    ? props.getSlotsPerDay().get(index)
                    : people.size();

            days.add(new DayView(index, dayNames.get(index), holidayNames.get(index),
                    capacity, people.size(), people));
            remoteOnDay.add(new HashSet<>(people));
            total += people.size();
        }

        return new ScheduleResponse(
                schedule.getId(),
                schedule.getWeekStart(),
                schedule.getGeneratedAt(),
                schedule.getGeneratedBy(),
                total,
                holidays.remotesPerPerson(schedule.getWeekStart()),
                days,
                buildRoster(roster, remoteOnDay),
                schedule.remoteDaysPerPerson());
    }

    /**
     * Everyone who should appear as a row: the team as it stands, plus anyone the stored week
     * assigned who has since left it, so an older schedule still renders in full. Sorted by name
     * because the page is scanned by looking yourself up.
     */
    private static List<PersonRow> buildRoster(Collection<String> team,
                                               List<Set<String>> remoteOnDay) {

        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(team);
        remoteOnDay.forEach(names::addAll);

        List<PersonRow> roster = new ArrayList<>(names.size());

        for (String name : names) {
            List<Cell> cells = new ArrayList<>(remoteOnDay.size());
            int count = 0;

            for (int d = 0; d < remoteOnDay.size(); d++) {
                boolean remote = remoteOnDay.get(d).contains(name);
                boolean left = d > 0 && remoteOnDay.get(d - 1).contains(name);
                boolean right = d + 1 < remoteOnDay.size() && remoteOnDay.get(d + 1).contains(name);

                cells.add(new Cell(d, remote, remote && left, remote && right));
                if (remote) count++;
            }

            roster.add(new PersonRow(name, cells, count));
        }

        return roster;
    }
}
