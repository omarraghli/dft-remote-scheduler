package cires.dft.remotescheduler.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything about the schedule lives in {@code application.yml} under {@code remote.*} so the
 * roster, the capacity and the rules can change without touching Java.
 */
@Validated
@ConfigurationProperties(prefix = "remote")
public class RemoteScheduleProperties {

    /** The team, in any order — the solver shuffles them anyway. */
    @NotEmpty
    private List<String> people = new ArrayList<>();

    /** Working days, in order. Weekends are simply left out. */
    @NotEmpty
    private List<String> days = new ArrayList<>();

    /** Remote capacity of each day. Must have exactly as many entries as {@link #days}. */
    @NotEmpty
    private List<Integer> slotsPerDay = new ArrayList<>();

    /** Remote days every person gets, exactly. */
    @Min(0)
    private int remotesPerPerson = 3;

    /** Longest run of remote days one person may have. 2 means "never 3 in a row". */
    @Min(1)
    private int maxConsecutiveDays = 2;

    /** Day names with no remote work, matched against {@link #days} ignoring case. */
    private List<String> holidays = new ArrayList<>();

    public List<String> getPeople() {
        return people;
    }

    public void setPeople(List<String> people) {
        this.people = people;
    }

    public List<String> getDays() {
        return days;
    }

    public void setDays(List<String> days) {
        this.days = days;
    }

    public List<Integer> getSlotsPerDay() {
        return slotsPerDay;
    }

    public void setSlotsPerDay(List<Integer> slotsPerDay) {
        this.slotsPerDay = slotsPerDay;
    }

    public int getRemotesPerPerson() {
        return remotesPerPerson;
    }

    public void setRemotesPerPerson(int remotesPerPerson) {
        this.remotesPerPerson = remotesPerPerson;
    }

    public int getMaxConsecutiveDays() {
        return maxConsecutiveDays;
    }

    public void setMaxConsecutiveDays(int maxConsecutiveDays) {
        this.maxConsecutiveDays = maxConsecutiveDays;
    }

    public List<String> getHolidays() {
        return holidays;
    }

    public void setHolidays(List<String> holidays) {
        this.holidays = holidays;
    }
}
