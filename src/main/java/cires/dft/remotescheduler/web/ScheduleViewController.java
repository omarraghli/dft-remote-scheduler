package cires.dft.remotescheduler.web;

import cires.dft.remotescheduler.config.RemoteScheduleProperties;
import cires.dft.remotescheduler.domain.WeekSchedule;
import cires.dft.remotescheduler.service.HolidayCalendar;
import cires.dft.remotescheduler.service.RosterService;
import cires.dft.remotescheduler.service.PublicHoliday;
import cires.dft.remotescheduler.service.ScheduleAlreadyExistsException;
import cires.dft.remotescheduler.service.ScheduleService;
import cires.dft.remotescheduler.service.VacationCalendar;
import cires.dft.remotescheduler.security.AppUserPrincipal;
import cires.dft.remotescheduler.service.WeekPlan;
import cires.dft.remotescheduler.service.WeekPlanException;
import cires.dft.remotescheduler.service.WeekPlanService;
import cires.dft.remotescheduler.service.WeekStarts;
import cires.dft.remotescheduler.solver.NoFeasibleScheduleException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The page the team looks at. */
@Controller
public class ScheduleViewController {

    public static final String TRIGGER = "web";

    private final ScheduleService scheduleService;
    private final RemoteScheduleProperties properties;
    private final HolidayCalendar holidays;
    private final VacationCalendar vacations;
    private final WeekPlanService weekPlans;
    private final RosterService people;

    public ScheduleViewController(ScheduleService scheduleService,
                                  RemoteScheduleProperties properties,
                                  HolidayCalendar holidays,
                                  VacationCalendar vacations,
                                  WeekPlanService weekPlans,
                                  RosterService people) {
        this.scheduleService = scheduleService;
        this.properties = properties;
        this.holidays = holidays;
        this.vacations = vacations;
        this.weekPlans = weekPlans;
        this.people = people;
    }

    /**
     * Shows one week, addressed by any date inside it. No {@code week} parameter means the
     * current week.
     *
     * <p>The week shown does not have to exist: navigating to a week nobody has scheduled yet
     * renders the empty state with a Generate button for exactly that week, which is what makes
     * stepping backwards and forwards through the calendar useful.
     */
    @GetMapping("/")
    public String index(@RequestParam(required = false)
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                        @AuthenticationPrincipal AppUserPrincipal principal,
                        HttpServletRequest request,
                        Model model) {

        LocalDate shownWeek = WeekStarts.of(week != null ? week : scheduleService.today());
        Optional<WeekSchedule> schedule = scheduleService.findByWeek(shownWeek);
        List<PublicHoliday> weekHolidays = holidays.inWeek(shownWeek);
        List<String> roster = people.activeNames();

        model.addAttribute("schedule", schedule
                .map(s -> ScheduleResponse.from(s, roster, properties, holidays)).orElse(null));
        model.addAttribute("shownWeek", shownWeek);
        model.addAttribute("prevWeek", shownWeek.minusWeeks(1));
        model.addAttribute("nextWeek", shownWeek.plusWeeks(1));
        model.addAttribute("currentWeek", WeekStarts.of(scheduleService.today()));
        model.addAttribute("weekNumber", shownWeek.get(WeekFields.ISO.weekOfWeekBasedYear()));
        model.addAttribute("weekRange", WeekLabels.range(shownWeek, properties.getDays().size()));
        model.addAttribute("dayNames", properties.getDays());
        model.addAttribute("todayIndex", todayIndex(shownWeek));
        // Lets the chart mark the signed-in person's own row.
        model.addAttribute("myRosterName", principal == null ? null : principal.getRosterName());
        // Sorted to match the chart's roster, so the empty week's placeholder rows line up
        // with the order people will see once it is generated.
        model.addAttribute("people", roster);
        // The shown week's own quota — a holiday lowers it, and the chart would otherwise flag
        // every row as having missed a target that week never had.
        int quota = holidays.remotesPerPerson(shownWeek);
        model.addAttribute("remotesPerPerson", quota);
        model.addAttribute("quotaLowered", quota < properties.getRemotesPerPerson());
        model.addAttribute("holidays", weekHolidays.stream()
                .map(h -> new HolidayView(h.name(),
                        h.dayName() + " " + WeekLabels.DAY_MONTH.format(h.date())))
                .toList());
        model.addAttribute("holidayNames", holidays.namesByDayIndex(shownWeek));
        model.addAttribute("maxConsecutiveDays", properties.getMaxConsecutiveDays());

        // What the week has been asked for and what it requires: the pins and the leave are
        // drawn on everybody's row, the wishes only ever on your own.
        WeekPlan plan = weekPlans.forWeek(shownWeek);
        String me = principal == null ? null : principal.getRosterName();
        model.addAttribute("onSite", plan.onSite());

        Map<String, Set<Integer>> away = vacations.awayDays(shownWeek);
        model.addAttribute("away", away);
        model.addAttribute("returnDays", vacations.returnDays(shownWeek));
        model.addAttribute("myAway", me == null ? Set.of() : away.getOrDefault(me, Set.of()));
        // A row is measured against what the week owes that person, which is less for anybody
        // whose leave leaves no room for the usual three.
        model.addAttribute("expected", scheduleService.expectedRemoteDays(shownWeek));
        model.addAttribute("myPreferred", me == null ? Set.of() : plan.preferredFor(me));
        model.addAttribute("myOnSite", me == null ? Set.of() : plan.onSiteFor(me));
        model.addAttribute("canSetPreferences",
                me != null && !shownWeek.isBefore(WeekStarts.of(scheduleService.today())));

        // Leave is declared by the people taking it, so an admin learns here, not from a flash
        // message somebody else saw, that a planned week has them remote while away.
        if (request.isUserInRole("ADMIN")) {
            model.addAttribute("leaveConflicts",
                    scheduleService.leaveConflicts(scheduleService.today()));
        }

        return "schedule";
    }

    /** One holiday as the strip above the chart prints it, dated in the page's own locale. */
    public record HolidayView(String name, String when) {
    }

    /**
     * Which column is today, so the page can mark it — or {@code -1} when the week being
     * viewed is not the current one, or today falls outside the configured working days.
     */
    private int todayIndex(LocalDate shownWeek) {
        LocalDate today = scheduleService.today();
        if (!WeekStarts.of(today).equals(shownWeek)) return -1;

        int index = (int) java.time.temporal.ChronoUnit.DAYS.between(shownWeek, today);
        return index < properties.getDays().size() ? index : -1;
    }

    /**
     * The Generate and Replace buttons, both acting on the week being viewed.
     *
     * <p>Generate sends {@code replace=false}, so a week that already has a schedule is refused
     * rather than quietly overwritten — the same rule the Thursday job follows. Replacing is a
     * separate, explicitly labelled button.
     */
    @PostMapping("/generate")
    public String generate(@RequestParam(required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                           @RequestParam(defaultValue = "false") boolean replace,
                           RedirectAttributes redirectAttributes) {

        LocalDate target = WeekStarts.of(week != null ? week : scheduleService.today());

        try {
            WeekSchedule schedule = scheduleService.generate(target, replace, TRIGGER);
            redirectAttributes.addFlashAttribute("message",
                    (replace ? "Schedule replaced for the week of " : "Schedule generated for the week of ")
                            + schedule.getWeekStart());

        } catch (ScheduleAlreadyExistsException | NoFeasibleScheduleException
                 | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        // Always land back on the week the user was looking at, generated or not.
        redirectAttributes.addAttribute("week", target.toString());
        return "redirect:/";
    }

    /**
     * The days you would rather be remote on, for the week you are looking at.
     *
     * <p>Who they are for comes from the signed-in account and never from the request, so this
     * cannot be used to rewrite somebody else's week. An admin setting days for other people
     * does it on the grid at {@code /admin/week}, which is behind {@code /admin/**}.
     *
     * <p>The on-site days go back unchanged: they are not this person's to set, and the whole
     * row is replaced in one go.
     */
    @PostMapping("/preferences")
    public String preferences(@RequestParam(required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                              @RequestParam(required = false) Set<Integer> preferred,
                              @AuthenticationPrincipal AppUserPrincipal principal,
                              RedirectAttributes redirectAttributes) {

        LocalDate target = WeekStarts.of(week != null ? week : scheduleService.today());
        String me = principal == null ? null : principal.getRosterName();

        if (me == null) {
            redirectAttributes.addFlashAttribute("error",
                    "You are not on the schedule, so there are no days to set. An admin can put "
                            + "you on it on the Équipe page.");
            redirectAttributes.addAttribute("week", target.toString());
            return "redirect:/";
        }

        try {
            scheduleService.setWeekPlan(target, me,
                    preferred == null ? Set.of() : preferred,
                    weekPlans.forWeek(target).onSiteFor(me),
                    principal.getUsername());

            redirectAttributes.addFlashAttribute("message",
                    scheduleService.findByWeek(target).isPresent()
                            ? "Saved — this week is already planned, so it takes a re-roll for "
                                    + "them to count."
                            : "Saved. They are taken into account the next time this week is "
                                    + "planned.");

        } catch (WeekPlanException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        redirectAttributes.addAttribute("week", target.toString());
        return "redirect:/";
    }
}
