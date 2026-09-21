package cires.dft.remotescheduler.web;

import cires.dft.remotescheduler.config.RemoteScheduleProperties;
import cires.dft.remotescheduler.service.HolidayCalendar;
import cires.dft.remotescheduler.service.ScheduleService;
import cires.dft.remotescheduler.service.WeekPlan;
import cires.dft.remotescheduler.service.WeekPlanException;
import cires.dft.remotescheduler.service.WeekPlanService;
import cires.dft.remotescheduler.service.WeekStarts;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The grid where an admin says who is needed in the office, and edits what people asked for on
 * behalf of anyone without an account. The whole path is admin-only, enforced in SecurityConfig.
 *
 * <p>One form per person, because the real use is pinning two or three people for Tuesday's
 * comité rather than rewriting the whole team, and a refusal can then name whose row caused it.
 */
@Controller
@RequestMapping("/admin/week")
public class AdminWeekController {

    private final ScheduleService scheduleService;
    private final WeekPlanService weekPlanService;
    private final RemoteScheduleProperties properties;
    private final HolidayCalendar holidays;

    public AdminWeekController(ScheduleService scheduleService,
                               WeekPlanService weekPlanService,
                               RemoteScheduleProperties properties,
                               HolidayCalendar holidays) {
        this.scheduleService = scheduleService;
        this.weekPlanService = weekPlanService;
        this.properties = properties;
        this.holidays = holidays;
    }

    /**
     * One roster person as the grid prints them.
     *
     * @param slug a form id safe to put in HTML, since roster names are free text
     */
    public record PersonRow(String person, String slug, Set<Integer> preferred,
                            Set<Integer> onSite) {
    }

    @GetMapping
    public String grid(@RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                       Model model) {

        LocalDate shownWeek = WeekStarts.of(week != null ? week : scheduleService.today());
        WeekPlan plan = weekPlanService.forWeek(shownWeek);

        List<PersonRow> rows = new ArrayList<>();
        for (String person : properties.getPeople().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER).toList()) {

            rows.add(new PersonRow(person, slug(person),
                    plan.preferredFor(person), plan.onSiteFor(person)));
        }

        model.addAttribute("rows", rows);
        model.addAttribute("shownWeek", shownWeek);
        model.addAttribute("prevWeek", shownWeek.minusWeeks(1));
        model.addAttribute("nextWeek", shownWeek.plusWeeks(1));
        model.addAttribute("currentWeek", WeekStarts.of(scheduleService.today()));
        model.addAttribute("weekNumber", shownWeek.get(WeekFields.ISO.weekOfWeekBasedYear()));
        model.addAttribute("weekRange", WeekLabels.range(shownWeek, properties.getDays().size()));
        model.addAttribute("dayNames", properties.getDays());
        model.addAttribute("holidayNames", holidays.namesByDayIndex(shownWeek));
        model.addAttribute("remotesPerPerson", holidays.remotesPerPerson(shownWeek));
        model.addAttribute("planned", scheduleService.findByWeek(shownWeek).isPresent());
        model.addAttribute("unplannable", scheduleService.unplannable(shownWeek).orElse(null));

        return "admin/week";
    }

    @PostMapping
    public String save(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                       @RequestParam String person,
                       @RequestParam(required = false) Set<Integer> preferred,
                       @RequestParam(required = false) Set<Integer> onSite,
                       Principal principal,
                       RedirectAttributes redirect) {

        LocalDate target = WeekStarts.of(week);
        Set<Integer> wanted = preferred == null ? Set.of() : preferred;
        Set<Integer> required = onSite == null ? Set.of() : onSite;

        try {
            scheduleService.setWeekPlan(target, person, wanted, required,
                    principal == null ? null : principal.getName());

            redirect.addFlashAttribute("message", "Saved for " + person + ".");
            scheduleService.plannedWeekContradicting(target, person, required)
                    .ifPresent(stale -> redirect.addFlashAttribute("staleWeeks", List.of(stale)));

        } catch (WeekPlanException | IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }

        redirect.addAttribute("week", target.toString());
        return "redirect:/admin/week";
    }

    private String slug(String person) {
        return person.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
