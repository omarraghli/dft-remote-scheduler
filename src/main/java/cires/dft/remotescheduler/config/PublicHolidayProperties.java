package cires.dft.remotescheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Moroccan public holidays, under {@code remote.public-holidays.*}.
 *
 * <p>Two lists because the two kinds behave differently. The national days fall on the same
 * Gregorian date every year and are listed once as {@code annual}. The religious ones follow the
 * lunar calendar and are announced year by year, so they are listed as {@code dated} — explicit
 * dates that somebody updates once a year rather than an approximation the app computes and gets
 * wrong by a day.
 */
@Validated
@ConfigurationProperties(prefix = "remote.public-holidays")
public class PublicHolidayProperties {

    private List<Annual> annual = new ArrayList<>();

    private List<Dated> dated = new ArrayList<>();

    /**
     * Remote days per person in a short week, keyed by how many holidays fall in it. A count with
     * no entry of its own uses the highest entry below it, so {@code 2: 1} also covers three.
     * Empty means holidays never change the quota.
     */
    private Map<Integer, Integer> quotas = new LinkedHashMap<>();

    /** A holiday on a fixed Gregorian date, e.g. the 18th of November every year. */
    public static class Annual {

        /** {@code MM-dd}, e.g. {@code 11-18}. */
        private String day;

        private String name;

        public MonthDay monthDay() {
            String value = day == null ? "" : day.trim();
            try {
                return MonthDay.parse(value.startsWith("--") ? value : "--" + value);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException(
                        "remote.public-holidays.annual has day \"" + day
                                + "\", which is not a MM-dd date — e.g. 11-18 for 18 November");
            }
        }

        public String getDay() {
            return day;
        }

        public void setDay(String day) {
            this.day = day;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    /** A holiday on one particular date, for the lunar ones that move every year. */
    public static class Dated {

        private LocalDate date;

        private String name;

        public LocalDate getDate() {
            return date;
        }

        public void setDate(LocalDate date) {
            this.date = date;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public List<Annual> getAnnual() {
        return annual;
    }

    public void setAnnual(List<Annual> annual) {
        this.annual = annual;
    }

    public List<Dated> getDated() {
        return dated;
    }

    public void setDated(List<Dated> dated) {
        this.dated = dated;
    }

    public Map<Integer, Integer> getQuotas() {
        return quotas;
    }

    public void setQuotas(Map<Integer, Integer> quotas) {
        this.quotas = quotas;
    }
}
