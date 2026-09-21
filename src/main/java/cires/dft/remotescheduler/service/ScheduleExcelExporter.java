package cires.dft.remotescheduler.service;

import cires.dft.remotescheduler.config.RemoteScheduleProperties;
import cires.dft.remotescheduler.domain.WeekSchedule;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Renders a stored {@link WeekSchedule} as the .xlsx the team is used to reading. */
@Component
public class ScheduleExcelExporter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final RemoteScheduleProperties properties;
    private final HolidayCalendar holidays;

    public ScheduleExcelExporter(RemoteScheduleProperties properties, HolidayCalendar holidays) {
        this.properties = properties;
        this.holidays = holidays;
    }

    /** The workbook as bytes, for serving over HTTP. */
    public byte[] toBytes(WeekSchedule schedule) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            write(workbook, schedule);
            workbook.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new UncheckedIOException("Could not build the schedule workbook", e);
        }
    }

    /** e.g. {@code remote-schedule-2026-W39.xlsx} — sorts chronologically and reads clearly. */
    public String fileName(WeekSchedule schedule) {
        WeekFields weekFields = WeekFields.ISO;
        int week = schedule.getWeekStart().get(weekFields.weekOfWeekBasedYear());
        int year = schedule.getWeekStart().get(weekFields.weekBasedYear());
        return "remote-schedule-%d-W%02d.xlsx".formatted(year, week);
    }

    private void write(Workbook workbook, WeekSchedule schedule) {
        Sheet sheet = workbook.createSheet("Remote Schedule");

        CellStyle titleStyle = titleStyle(workbook);
        CellStyle headerStyle = headerStyle(workbook);
        CellStyle nameStyle = nameStyle(workbook);

        List<String> days = properties.getDays();
        Map<Integer, List<String>> peopleByDay = schedule.peopleByDayIndex();
        Map<Integer, String> holidayNames = holidays.namesByDayIndex(schedule.getWeekStart());

        Row titleRow = sheet.createRow(0);
        titleRow.createCell(0).setCellValue(
                "Semaine du " + DATE.format(schedule.getWeekStart()));
        titleRow.getCell(0).setCellStyle(titleStyle);

        Row headerRow = sheet.createRow(2);
        for (int d = 0; d < days.size(); d++) {
            int count = peopleByDay.getOrDefault(d, List.of()).size();
            int capacity = properties.getSlotsPerDay().get(d);
            String holiday = holidayNames.get(d);

            headerRow.createCell(d).setCellValue(holiday != null
                    ? days.get(d) + " — " + holiday
                    : days.get(d) + " (" + count + "/" + capacity + ")");
            headerRow.getCell(d).setCellStyle(headerStyle);
        }

        int maxRows = peopleByDay.values().stream().mapToInt(List::size).max().orElse(0);

        for (int r = 0; r < maxRows; r++) {
            Row row = sheet.createRow(r + 3);

            for (int d = 0; d < days.size(); d++) {
                List<String> people = peopleByDay.getOrDefault(d, List.of());
                if (r >= people.size()) continue;

                row.createCell(d).setCellValue(people.get(r));
                row.getCell(d).setCellStyle(nameStyle);
            }
        }

        for (int d = 0; d < days.size(); d++) {
            sheet.setColumnWidth(d, 18 * 256);
        }
        sheet.createFreezePane(0, 3);
    }

    private CellStyle titleStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private CellStyle headerStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        applyBorders(style);
        return style;
    }

    private CellStyle nameStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        applyBorders(style);
        return style;
    }

    private void applyBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
}
