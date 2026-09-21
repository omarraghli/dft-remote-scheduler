package cires.dft.remotescheduler.web;

import cires.dft.remotescheduler.config.RemoteScheduleProperties;
import cires.dft.remotescheduler.domain.Vacation;
import cires.dft.remotescheduler.service.ScheduleService;
import cires.dft.remotescheduler.service.VacationManagementException;
import cires.dft.remotescheduler.service.VacationService;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Who is away, and when. The whole path is admin-only, enforced in SecurityConfig.
 *
 * <p>Like the fériés page, every change reports what it just affected: leave entered after the
 * week was planned leaves somebody remote on a day they are not even working, and only a
 * re-roll fixes that.
 */
@Controller
@RequestMapping("/admin/vacations")
public class AdminVacationController {

    /** Day names are configured in French, so the dates on this page follow. */
    private static final DateTimeFormatter FULL =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH);
    private static final DateTimeFormatter SHORT =
            DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH);

    private final VacationService vacationService;
    private final ScheduleService scheduleService;
    private final RemoteScheduleProperties properties;

    public AdminVacationController(VacationService vacationService,
                                   ScheduleService scheduleService,
                                   RemoteScheduleProperties properties) {
        this.vacationService = vacationService;
        this.scheduleService = scheduleService;
        this.properties = properties;
    }

    /**
     * One stretch of leave as the table prints it.
     *
     * @param over        it is already behind us, so nothing about it can change a schedule
     * @param plannedWeek a week already scheduled that has them remote while they are away
     */
    public record VacationRow(Long id, String person, LocalDate startDate, LocalDate endDate,
                              int days, String when, boolean over, LocalDate plannedWeek) {
    }

    @GetMapping
    public String list(Model model) {
        LocalDate today = scheduleService.today();
        List<VacationRow> rows = new ArrayList<>();

        for (Vacation vacation : vacationService.all()) {
            rows.add(new VacationRow(
                    vacation.getId(),
                    vacation.getPersonName(),
                    vacation.getStartDate(),
                    vacation.getEndDate(),
                    vacation.days(),
                    when(vacation),
                    vacation.getEndDate().isBefore(today),
                    plannedWeekFor(vacation)));
        }

        model.addAttribute("vacations", rows);
        model.addAttribute("people", properties.getPeople().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER).toList());
        model.addAttribute("today", today);
        model.addAttribute("away", rows.stream()
                .filter(row -> !row.over() && !row.startDate().isAfter(today)).count());

        return "admin/vacations";
    }

    @PostMapping
    public String add(@RequestParam String person,
                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                      RedirectAttributes redirect) {

        try {
            Vacation vacation = vacationService.add(person, startDate, endDate);
            report(vacation, "Leave added.", redirect);

        } catch (VacationManagementException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/vacations";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @RequestParam String person,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                         RedirectAttributes redirect) {

        try {
            Vacation vacation = vacationService.update(id, person, startDate, endDate);
            report(vacation, "Leave updated.", redirect);

        } catch (VacationManagementException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/vacations";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            vacationService.delete(id);
            redirect.addFlashAttribute("message", "Leave removed.");

        } catch (VacationManagementException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/vacations";
    }

    /** Says what the change just broke: a planned week with them remote while they are away. */
    private void report(Vacation vacation, String done, RedirectAttributes redirect) {
        redirect.addFlashAttribute("message", done);

        LocalDate stale = plannedWeekFor(vacation);
        if (stale != null) {
            redirect.addFlashAttribute("staleWeek", stale);
            redirect.addFlashAttribute("stalePerson", vacation.getPersonName());
        }
    }

    /**
     * The first planned week that has them remote on a day this leave covers. Only the weeks
     * the leave actually touches are looked at, which is at most a handful.
     */
    private LocalDate plannedWeekFor(Vacation vacation) {
        return scheduleService.plannedWeeksAssigningPerson(
                        vacation.getPersonName(), vacation.getStartDate(), vacation.getEndDate())
                .stream()
                .findFirst()
                .orElse(null);
    }

    /** {@code lundi 13 juillet – vendredi 24 juillet 2026}, or one date for a single day. */
    private String when(Vacation vacation) {
        return vacation.days() == 1
                ? FULL.format(vacation.getStartDate())
                : SHORT.format(vacation.getStartDate()) + " – " + FULL.format(vacation.getEndDate());
    }
}
