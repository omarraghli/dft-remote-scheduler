package cires.dft.remotescheduler.web;

import cires.dft.remotescheduler.config.RemoteScheduleProperties;
import cires.dft.remotescheduler.domain.Vacation;
import cires.dft.remotescheduler.security.AppUserPrincipal;
import cires.dft.remotescheduler.service.HolidayCalendar;
import cires.dft.remotescheduler.service.RosterService;
import cires.dft.remotescheduler.service.ScheduleService;
import cires.dft.remotescheduler.service.UserManagementException;
import cires.dft.remotescheduler.service.UserService;
import cires.dft.remotescheduler.service.VacationCalendar;
import cires.dft.remotescheduler.service.VacationManagementException;
import cires.dft.remotescheduler.service.VacationService;
import cires.dft.remotescheduler.service.WeekStarts;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Leave, declared by the people taking it, and a calendar of everybody's so nobody books the
 * same fortnight as the rest of their team without knowing.
 *
 * <p>Whose leave a change is for always comes from the signed-in account, never the request,
 * exactly as {@code POST /preferences} does. Admins record leave for anybody at
 * {@code /admin/vacations}.
 */
@Controller
@RequestMapping("/leave")
public class LeaveController {

    /** How far ahead the team calendar looks. Two months covers anything being planned now. */
    private static final int WEEKS_SHOWN = 8;

    /** How far ahead "coming up" looks. */
    private static final int COMING_UP_DAYS = 30;

    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH);

    private final VacationService vacationService;
    private final VacationCalendar vacationCalendar;
    private final HolidayCalendar holidays;
    private final ScheduleService scheduleService;
    private final RosterService people;
    private final UserService userService;
    private final RemoteScheduleProperties properties;

    public LeaveController(VacationService vacationService,
                           VacationCalendar vacationCalendar,
                           HolidayCalendar holidays,
                           ScheduleService scheduleService,
                           RosterService people,
                           UserService userService,
                           RemoteScheduleProperties properties) {
        this.vacationService = vacationService;
        this.vacationCalendar = vacationCalendar;
        this.holidays = holidays;
        this.scheduleService = scheduleService;
        this.people = people;
        this.userService = userService;
        this.properties = properties;
    }

    /** One of your own stretches, and whether it is still yours to change. */
    public record MyLeave(Long id, LocalDate startDate, LocalDate endDate, String when, int days,
                          boolean over, boolean planned) {
    }

    /** Somebody away, as the lists above the calendar print them. */
    public record Away(String person, String when, boolean me) {
    }

    public record WeekColumn(LocalDate monday, int number, String range, boolean current) {
    }

    /**
     * One person across the calendar: per week, per day, what that day is — {@code away},
     * {@code holiday}, or empty for an ordinary working day.
     */
    public record CalendarRow(String person, boolean me, List<List<String>> weeks, int awayDays) {
    }

    @GetMapping
    public String page(@AuthenticationPrincipal AppUserPrincipal principal, Model model) {
        LocalDate today = scheduleService.today();
        LocalDate firstWeek = WeekStarts.of(today);
        String me = myName(principal);

        model.addAttribute("me", me);
        model.addAttribute("today", today);
        model.addAttribute("dayNames", properties.getDays());

        List<MyLeave> mine = new ArrayList<>();
        if (me != null) {
            for (Vacation vacation : vacationService.all()) {
                if (!vacation.getPersonName().equalsIgnoreCase(me)) continue;
                boolean over = vacation.getEndDate().isBefore(today);
                // The last few that are over are enough to remember by; the rest is history.
                if (over && vacation.getEndDate().isBefore(today.minusDays(60))) continue;

                mine.add(new MyLeave(vacation.getId(), vacation.getStartDate(),
                        vacation.getEndDate(), when(vacation), vacation.days(), over,
                        !scheduleService.plannedWeeksAssigningPerson(vacation.getPersonName(),
                                vacation.getStartDate(), vacation.getEndDate()).isEmpty()));
            }
        }
        model.addAttribute("mine", mine);

        List<Vacation> upcoming = vacationService.overlapping(today, today.plusDays(COMING_UP_DAYS));
        model.addAttribute("awayNow", upcoming.stream()
                .filter(v -> v.covers(today))
                .sorted(Comparator.comparing(Vacation::getEndDate))
                .map(v -> new Away(v.getPersonName(), "back after " + DAY.format(v.getEndDate()),
                        v.getPersonName().equalsIgnoreCase(String.valueOf(me))))
                .toList());
        model.addAttribute("comingUp", upcoming.stream()
                .filter(v -> v.getStartDate().isAfter(today))
                .sorted(Comparator.comparing(Vacation::getStartDate))
                .map(v -> new Away(v.getPersonName(), when(v),
                        v.getPersonName().equalsIgnoreCase(String.valueOf(me))))
                .toList());

        List<WeekColumn> weeks = new ArrayList<>();
        List<Map<String, Set<Integer>>> awayByWeek = new ArrayList<>();
        List<Set<Integer>> holidaysByWeek = new ArrayList<>();

        for (int w = 0; w < WEEKS_SHOWN; w++) {
            LocalDate monday = firstWeek.plusWeeks(w);
            weeks.add(new WeekColumn(monday, monday.get(WeekFields.ISO.weekOfWeekBasedYear()),
                    WeekLabels.range(monday, properties.getDays().size()), w == 0));
            awayByWeek.add(vacationCalendar.awayDays(monday));
            holidaysByWeek.add(holidays.namesByDayIndex(monday).keySet());
        }

        List<CalendarRow> rows = new ArrayList<>();
        for (String person : people.activeNames()) {
            List<List<String>> cells = new ArrayList<>();
            int awayDays = 0;

            for (int w = 0; w < WEEKS_SHOWN; w++) {
                Set<Integer> away = awayByWeek.get(w).getOrDefault(person, Set.of());
                List<String> days = new ArrayList<>();

                for (int d = 0; d < properties.getDays().size(); d++) {
                    if (holidaysByWeek.get(w).contains(d)) {
                        days.add("holiday");
                    } else if (away.contains(d)) {
                        days.add("away");
                        awayDays++;
                    } else {
                        days.add("");
                    }
                }
                cells.add(days);
            }

            rows.add(new CalendarRow(person, person.equalsIgnoreCase(String.valueOf(me)),
                    cells, awayDays));
        }

        model.addAttribute("weeks", weeks);
        model.addAttribute("rows", rows);

        return "leave";
    }

    @PostMapping
    public String add(@AuthenticationPrincipal AppUserPrincipal principal,
                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                      RedirectAttributes redirect) {

        String me = myName(principal);
        if (me == null) return notLinked(redirect);

        try {
            Vacation vacation = vacationService.addOwn(me, startDate, endDate);
            report(vacation, "Leave added.", redirect);

        } catch (VacationManagementException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/leave";
    }

    @PostMapping("/{id}")
    public String update(@AuthenticationPrincipal AppUserPrincipal principal,
                         @PathVariable Long id,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                         RedirectAttributes redirect) {

        String me = myName(principal);
        if (me == null) return notLinked(redirect);

        try {
            Vacation vacation = vacationService.updateOwn(me, id, startDate, endDate);
            report(vacation, "Leave updated.", redirect);

        } catch (VacationManagementException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/leave";
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal AppUserPrincipal principal,
                         @PathVariable Long id,
                         RedirectAttributes redirect) {

        String me = myName(principal);
        if (me == null) return notLinked(redirect);

        try {
            vacationService.deleteOwn(me, id);
            redirect.addFlashAttribute("message", "Leave removed.");

        } catch (VacationManagementException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/leave";
    }

    /**
     * Who else is off over the same days — the warning that stands in for an approval step —
     * and whether a week already planned now has them remote while away.
     */
    private void report(Vacation vacation, String done, RedirectAttributes redirect) {
        redirect.addFlashAttribute("message", done);

        List<Vacation> others = vacationService.othersAway(vacation.getPersonName(),
                vacation.getStartDate(), vacation.getEndDate());
        if (!others.isEmpty()) {
            redirect.addFlashAttribute("overlap", others.stream()
                    .map(v -> v.getPersonName() + " (" + when(v) + ")")
                    .collect(Collectors.joining(", ")));
        }

        if (!scheduleService.plannedWeeksAssigningPerson(vacation.getPersonName(),
                vacation.getStartDate(), vacation.getEndDate()).isEmpty()) {
            redirect.addFlashAttribute("planned", true);
        }
    }

    private String notLinked(RedirectAttributes redirect) {
        redirect.addFlashAttribute("error", "You are not on the schedule, so there is no "
                + "leave to record for you. Ask an admin to put you on it.");
        return "redirect:/leave";
    }

    /**
     * Read from the account rather than the session: a rename made while they were signed in
     * would otherwise leave them unable to touch their own leave until they signed out.
     */
    private String myName(AppUserPrincipal principal) {
        if (principal == null) return null;

        String name = principal.getRosterName();
        if (principal.getId() != null) {
            try {
                name = userService.require(principal.getId()).getRosterName();
            } catch (UserManagementException e) {
                return null;
            }
        }

        return name == null ? null : people.activeName(name).orElse(null);
    }

    private String when(Vacation vacation) {
        return vacation.days() == 1
                ? DAY.format(vacation.getStartDate())
                : DAY.format(vacation.getStartDate()) + " – " + DAY.format(vacation.getEndDate());
    }
}
