package cires.dft.remotescheduler.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A day somebody asked to be remote on, in one week.
 *
 * <p>A wish, not a booking: the solver grants as many as the week has room for and drops the
 * rest. One row per day asked for, so asking for nothing leaves no rows at all.
 */
@Entity
@Table(name = "remote_preference",
        uniqueConstraints = @UniqueConstraint(name = "uk_remote_preference_person_week_day",
                columnNames = {"person_name", "week_start", "day_index"}))
public class RemotePreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "person_name", nullable = false, length = 64)
    private String personName;

    /** The Monday the week starts on, as everything else here is keyed. */
    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "day_index", nullable = false)
    private int dayIndex;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RemotePreference() {
        // for JPA
    }

    public RemotePreference(String personName, LocalDate weekStart, int dayIndex,
                            Instant updatedAt) {
        this.personName = personName;
        this.weekStart = weekStart;
        this.dayIndex = dayIndex;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getPersonName() {
        return personName;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public int getDayIndex() {
        return dayIndex;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
