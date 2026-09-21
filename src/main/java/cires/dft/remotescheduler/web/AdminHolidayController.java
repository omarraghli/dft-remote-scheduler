package cires.dft.remotescheduler.web;

import cires.dft.remotescheduler.config.PublicHolidayProperties;
import cires.dft.remotescheduler.config.RemoteScheduleProperties;
import cires.dft.remotescheduler.domain.Holiday;
import cires.dft.remotescheduler.service.HolidayManagementException;
import cires.dft.remotescheduler.service.HolidayService;
import cires.dft.remotescheduler.service.ScheduleService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Managing the holidays that move. The whole path is admin-only, enforced in SecurityConfig.
 *
 * <p>Every change reports back what it just affected: a holiday landing on a week that is
 * already planned is the case worth catching, since those assignments were made when the day
 * was still a working one and the week has to be re-rolled.
 */
@Controller
@RequestMapping("/admin/holidays")
public class AdminHolidayController {

    /** Day names are configured in French, so the dates on this page follow. */
    private static final Locale PAGE_LOCALE = Locale.FRENCH;
    private static final DateTimeFormatter FULL =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", PAGE_LOCALE);
    private static final DateTimeFormatter SHORT =
            DateTimeFormatter.ofPattern("EEEE d", PAGE_LOCALE);
    private static final DateTimeFormatter ANNUAL =
            DateTimeFormatter.ofPattern("d MMMM", PAGE_LOCALE);

    private final HolidayService holidayService;
    private final ScheduleService scheduleService;
    private final RemoteScheduleProperties scheduleProperties;
    private final PublicHolidayProperties holidayProperties;

    public AdminHolidayController(HolidayService holidayService,
                                  ScheduleService scheduleService,
                                  RemoteScheduleProperties scheduleProperties,
                                  PublicHolidayProperties holidayProperties) {
        this.holidayService = holidayService;
        this.scheduleService = scheduleService;
        this.scheduleProperties = scheduleProperties;
        this.holidayProperties = holidayProperties;
    }

    /**
     * One stored holiday as the table prints it.
     *
     * @param when        the dates in French, e.g. {@code vendredi 20 – samedi 21 mars 2026}
     * @param weekendOnly it falls entirely outside the working days, so it changes nothing
     * @param plannedWeek a week already scheduled that still has people on one of its days
     */
    public record HolidayRow(Long id, String name, LocalDate startDate, int days,
                             String when, boolean weekendOnly, LocalDate plannedWeek) {
    }

    /** A national day, shown for reference — those never move and stay in configuration. */
    public record AnnualRow(String name, String when) {
    }

    @GetMapping
    public String list(Model model) {
        List<HolidayRow> rows = new ArrayList<>();

        for (Holiday holiday : holidayService.all()) {
            List<LocalDate> clashes = scheduleService.plannedWeeksAssigningOn(holiday.dates());

            rows.add(new HolidayRow(
                    holiday.getId(),
                    holiday.getName(),
                    holiday.getStartDate(),
                    holiday.getDays(),
                    when(holiday),
                    holiday.dates().stream().noneMatch(this::isWorkingDay),
                    clashes.isEmpty() ? null : clashes.getFirst()));
        }

        model.addAttribute("holidays", rows);
        model.addAttribute("annual", holidayProperties.getAnnual().stream()
                .map(entry -> new AnnualRow(entry.getName(),
                        ANNUAL.format(entry.monthDay())))
                .toList());
        model.addAttribute("today", scheduleService.today());

        LocalDate covered = holidayService.coveredUntil();
        model.addAttribute("coverageLow", holidayService.coverageRunsLow());
        model.addAttribute("coveredUntil", covered == null ? null : FULL.format(covered));

        return "admin/holidays";
    }

    @PostMapping
    public String add(@RequestParam String name,
                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                      @RequestParam(defaultValue = "1") int days,
                      RedirectAttributes redirect) {

        try {
            Holiday holiday = holidayService.add(name, startDate, days);
            reportAffectedWeeks(holiday, "Holiday added.", redirect);

        } catch (HolidayManagementException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/holidays";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @RequestParam String name,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                         @RequestParam(defaultValue = "1") int days,
                         RedirectAttributes redirect) {

        try {
            Holiday holiday = holidayService.update(id, name, startDate, days);
            reportAffectedWeeks(holiday, "Holiday updated.", redirect);

        } catch (HolidayManagementException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/holidays";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            holidayService.delete(id);
            redirect.addFlashAttribute("message", "Holiday removed.");

        } catch (HolidayManagementException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/holidays";
    }

    /**
     * Says what the change just broke, if anything: a week planned before the announcement still
     * has people remote on a day that is now closed, and only a re-roll fixes it.
     */
    private void reportAffectedWeeks(Holiday holiday, String done, RedirectAttributes redirect) {
        List<LocalDate> weeks = scheduleService.plannedWeeksAssigningOn(holiday.dates());

        if (weeks.isEmpty()) {
            redirect.addFlashAttribute("message", done);
            return;
        }

        redirect.addFlashAttribute("message", done);
        redirect.addFlashAttribute("staleWeeks", weeks);
    }

    /** {@code vendredi 20 mars 2026}, or {@code vendredi 20 – samedi 21 mars 2026} for a run. */
    private String when(Holiday holiday) {
        return holiday.getDays() == 1
                ? FULL.format(holiday.getStartDate())
                : SHORT.format(holiday.getStartDate()) + " – " + FULL.format(holiday.endDate());
    }

    /** Whether that date has a column at all — a holiday on a weekend changes nothing. */
    private boolean isWorkingDay(LocalDate date) {
        int index = date.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue();
        return index < scheduleProperties.getDays().size();
    }
}
