package cires.dft.remotescheduler.web;

import cires.dft.remotescheduler.config.RemoteScheduleProperties;
import cires.dft.remotescheduler.domain.WeekSchedule;
import cires.dft.remotescheduler.service.ScheduleAlreadyExistsException;
import cires.dft.remotescheduler.service.ScheduleService;
import cires.dft.remotescheduler.security.AppUserPrincipal;
import cires.dft.remotescheduler.service.WeekStarts;
import cires.dft.remotescheduler.solver.NoFeasibleScheduleException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.Optional;

/** The page the team looks at. */
@Controller
public class ScheduleViewController {

    public static final String TRIGGER = "web";

    /** Day names are configured in French, so the dates on the page follow. */
    private static final Locale PAGE_LOCALE = Locale.FRENCH;
    private static final DateTimeFormatter DAY_MONTH =
            DateTimeFormatter.ofPattern("d MMM", PAGE_LOCALE);
    private static final DateTimeFormatter DAY_MONTH_YEAR =
            DateTimeFormatter.ofPattern("d MMM yyyy", PAGE_LOCALE);

    private final ScheduleService scheduleService;
    private final RemoteScheduleProperties properties;

    public ScheduleViewController(ScheduleService scheduleService,
                                  RemoteScheduleProperties properties) {
        this.scheduleService = scheduleService;
        this.properties = properties;
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
                        Model model) {

        LocalDate shownWeek = WeekStarts.of(week != null ? week : scheduleService.today());
        Optional<WeekSchedule> schedule = scheduleService.findByWeek(shownWeek);

        model.addAttribute("schedule",
                schedule.map(s -> ScheduleResponse.from(s, properties)).orElse(null));
        model.addAttribute("shownWeek", shownWeek);
        model.addAttribute("prevWeek", shownWeek.minusWeeks(1));
        model.addAttribute("nextWeek", shownWeek.plusWeeks(1));
        model.addAttribute("currentWeek", WeekStarts.of(scheduleService.today()));
        model.addAttribute("weekNumber", shownWeek.get(WeekFields.ISO.weekOfWeekBasedYear()));
        model.addAttribute("weekRange", weekRange(shownWeek));
        model.addAttribute("dayNames", properties.getDays());
        model.addAttribute("todayIndex", todayIndex(shownWeek));
        // Lets the chart mark the signed-in person's own row.
        model.addAttribute("myRosterName", principal == null ? null : principal.getRosterName());
        // Sorted to match the chart's roster, so the empty week's placeholder rows line up
        // with the order people will see once it is generated.
        model.addAttribute("people", properties.getPeople().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList());
        model.addAttribute("remotesPerPerson", properties.getRemotesPerPerson());
        model.addAttribute("maxConsecutiveDays", properties.getMaxConsecutiveDays());

        return "schedule";
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

    /** e.g. {@code 21 – 25 sept. 2026}, dropping the year from the first date when it repeats. */
    private String weekRange(LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(Math.max(properties.getDays().size() - 1, 0));

        String from = weekStart.getYear() == weekEnd.getYear()
                ? DAY_MONTH.format(weekStart)
                : DAY_MONTH_YEAR.format(weekStart);

        return from + " – " + DAY_MONTH_YEAR.format(weekEnd);
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
}
