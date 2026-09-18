package cires.dft.remotescheduler.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** One generated week, identified by the Monday it starts on. */
@Entity
@Table(name = "week_schedule",
        uniqueConstraints = @UniqueConstraint(name = "uk_week_schedule_week_start",
                columnNames = "week_start"))
public class WeekSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The Monday of the week this schedule covers. Unique — one schedule per week. */
    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    /** How it came to exist, so a manual re-run can be told apart from the Thursday job. */
    @Column(name = "generated_by", nullable = false, length = 32)
    private String generatedBy;

    @OneToMany(mappedBy = "weekSchedule",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    @OrderBy("dayIndex ASC, personName ASC")
    private List<RemoteAssignment> assignments = new ArrayList<>();

    protected WeekSchedule() {
        // for JPA
    }

    public WeekSchedule(LocalDate weekStart, Instant generatedAt, String generatedBy) {
        this.weekStart = weekStart;
        this.generatedAt = generatedAt;
        this.generatedBy = generatedBy;
    }

    public void addAssignment(String personName, int dayIndex, String dayName) {
        RemoteAssignment assignment = new RemoteAssignment(this, personName, dayIndex, dayName);
        assignments.add(assignment);
    }

    /** Remote people per day index, days with nobody included as empty lists. */
    public Map<Integer, List<String>> peopleByDayIndex() {
        Map<Integer, List<String>> byDay = new TreeMap<>();
        for (RemoteAssignment assignment : assignments) {
            byDay.computeIfAbsent(assignment.getDayIndex(), k -> new ArrayList<>())
                    .add(assignment.getPersonName());
        }
        return byDay;
    }

    /** Remote day count per person, used by the UI and the consistency checks. */
    public Map<String, Integer> remoteDaysPerPerson() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (RemoteAssignment assignment : assignments) {
            counts.merge(assignment.getPersonName(), 1, Integer::sum);
        }
        return counts;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public String getGeneratedBy() {
        return generatedBy;
    }

    public List<RemoteAssignment> getAssignments() {
        return assignments;
    }
}
