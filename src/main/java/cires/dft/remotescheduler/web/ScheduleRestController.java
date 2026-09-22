package cires.dft.remotescheduler.web;

import cires.dft.remotescheduler.config.RemoteScheduleProperties;
import cires.dft.remotescheduler.domain.WeekSchedule;
import cires.dft.remotescheduler.service.HolidayCalendar;
import cires.dft.remotescheduler.service.RosterService;
import cires.dft.remotescheduler.service.ScheduleExcelExporter;
import cires.dft.remotescheduler.service.ScheduleNotFoundException;
import cires.dft.remotescheduler.service.ScheduleService;
import cires.dft.remotescheduler.security.ServiceTokenFilter;
import cires.dft.remotescheduler.service.WeekStarts;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleRestController {

    public static final String TRIGGER = "api";
    public static final String SERVICE_TRIGGER = "service";

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ScheduleService scheduleService;
    private final ScheduleExcelExporter exporter;
    private final RemoteScheduleProperties properties;
    private final HolidayCalendar holidays;
    private final RosterService people;

    public ScheduleRestController(ScheduleService scheduleService,
                                  ScheduleExcelExporter exporter,
                                  RemoteScheduleProperties properties,
                                  HolidayCalendar holidays,
                                  RosterService people) {
        this.scheduleService = scheduleService;
        this.exporter = exporter;
        this.properties = properties;
        this.holidays = holidays;
        this.people = people;
    }

    /** Every stored schedule, newest week first. */
    @GetMapping
    public List<ScheduleResponse> list() {
        return scheduleService.findAll().stream()
                .map(schedule -> ScheduleResponse.from(schedule, people.activeNames(), properties, holidays))
                .toList();
    }

    /** This week's schedule. */
    @GetMapping("/current")
    public ScheduleResponse current() {
        WeekSchedule schedule = scheduleService.findCurrentWeek()
                .orElseThrow(() -> new ScheduleNotFoundException(
                        WeekStarts.of(scheduleService.today())));

        return ScheduleResponse.from(schedule, people.activeNames(), properties, holidays);
    }

    /** The schedule for the week containing the given date. */
    @GetMapping("/{date}")
    public ScheduleResponse byWeek(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        WeekSchedule schedule = scheduleService.findByWeek(date)
                .orElseThrow(() -> new ScheduleNotFoundException(WeekStarts.of(date)));

        return ScheduleResponse.from(schedule, people.activeNames(), properties, holidays);
    }

    /**
     * Runs the distribution on demand, without waiting for Thursday.
     *
     * @param week    any date in the target week; defaults to next week, like the job
     * @param replace overwrite an existing schedule for that week
     */
    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleResponse generate(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
            @RequestParam(defaultValue = "false") boolean replace) {

        LocalDate target = week != null ? week : WeekStarts.next(scheduleService.today());
        WeekSchedule schedule = scheduleService.generate(target, replace, trigger());

        return ScheduleResponse.from(schedule, people.activeNames(), properties, holidays);
    }

    /**
     * Records who asked for this week, so the page can say where a schedule came from — a
     * person calling the API, or a machine holding the service token.
     */
    private String trigger() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();

        return (auth != null && ServiceTokenFilter.PRINCIPAL.equals(auth.getName()))
                ? SERVICE_TRIGGER
                : TRIGGER;
    }

    /** Downloads the week as the .xlsx the team reads. */
    @GetMapping("/{date}/export")
    public ResponseEntity<byte[]> export(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        WeekSchedule schedule = scheduleService.findByWeek(date)
                .orElseThrow(() -> new ScheduleNotFoundException(WeekStarts.of(date)));

        byte[] body = exporter.toBytes(schedule);

        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + exporter.fileName(schedule) + "\"")
                .body(body);
    }
}
