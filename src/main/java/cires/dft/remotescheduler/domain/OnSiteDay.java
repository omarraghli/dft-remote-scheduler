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
 * A day somebody has to be in the office, in one week.
 *
 * <p>The opposite of a {@link RemotePreference} in every way that matters: an admin sets it, and
 * it is hard — the solver treats the day as closed for that person, exactly as it treats a public
 * holiday for everyone. Only an admin can create one, which is why it records who did.
 */
@Entity
@Table(name = "on_site_day",
        uniqueConstraints = @UniqueConstraint(name = "uk_on_site_day_person_week_day",
                columnNames = {"person_name", "week_start", "day_index"}))
public class OnSiteDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "person_name", nullable = false, length = 64)
    private String personName;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "day_index", nullable = false)
    private int dayIndex;

    @Column(name = "set_by", length = 190)
    private String setBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OnSiteDay() {
        // for JPA
    }

    public OnSiteDay(String personName, LocalDate weekStart, int dayIndex, String setBy,
                     Instant createdAt) {
        this.personName = personName;
        this.weekStart = weekStart;
        this.dayIndex = dayIndex;
        this.setBy = setBy;
        this.createdAt = createdAt;
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

    public String getSetBy() {
        return setBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
