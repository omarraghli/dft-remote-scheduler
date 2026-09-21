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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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

    /** One roster person as the grid prints them. */
    public record PersonRow(String person, Set<Integer> preferred, Set<Integer> onSite) {
    }

    /**
     * A ticked box, as the grid submits it: {@code Sara|2}. The whole grid is one form and one
     * Save, so each box has to say who and which day it belongs to — the checkbox name alone
     * cannot, and a form per row meant sixteen Save buttons for one decision.
     */
    private record Tick(String person, int dayIndex) {

        static Tick parse(String value) {
            int split = value.lastIndexOf('|');
            if (split < 1) throw new IllegalArgumentException("Malformed selection: " + value);

            try {
                return new Tick(value.substring(0, split),
                        Integer.parseInt(value.substring(split + 1)));

            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Malformed selection: " + value);
            }
        }
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

            rows.add(new PersonRow(person, plan.preferredFor(person), plan.onSiteFor(person)));
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

    /**
     * The whole grid, saved in one go. Every roster person is replaced by what came back, so a
     * box somebody unticked clears rather than lingering because its row was not submitted.
     */
    @PostMapping
    public String save(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                       @RequestParam(required = false) List<String> preferred,
                       @RequestParam(required = false) List<String> onSite,
                       Principal principal,
                       RedirectAttributes redirect) {

        LocalDate target = WeekStarts.of(week);

        try {
            Map<String, Set<Integer>> wanted = byPerson(preferred);
            Map<String, Set<Integer>> required = byPerson(onSite);

            scheduleService.setWeekPlan(target, properties.getPeople(),
                    new WeekPlan(wanted, required),
                    principal == null ? null : principal.getName());

            redirect.addFlashAttribute("message", "Saved.");

            List<String> stale = scheduleService.peopleContradictingPlannedWeek(target, required);
            if (!stale.isEmpty()) {
                redirect.addFlashAttribute("staleWeek", target);
                redirect.addFlashAttribute("stalePeople", stale);
            }

        } catch (WeekPlanException | IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }

        redirect.addAttribute("week", target.toString());
        return "redirect:/admin/week";
    }

    /** Folds the ticked boxes back into the day indices each person holds. */
    private Map<String, Set<Integer>> byPerson(List<String> ticks) {
        Map<String, Set<Integer>> byPerson = new HashMap<>();
        if (ticks == null) return byPerson;

        for (String tick : ticks) {
            Tick parsed = Tick.parse(tick);
            byPerson.computeIfAbsent(parsed.person(), k -> new TreeSet<>()).add(parsed.dayIndex());
        }

        return byPerson;
    }
}
