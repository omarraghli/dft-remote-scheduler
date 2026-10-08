package cires.dft.remotescheduler.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * A day somebody asks to be remote on every week, unless they ask for something else in one.
 *
 * <p>The template behind {@link RemotePreference}: a week with no wishes of its own for this
 * person takes these. Still a wish, never a booking.
 */
@Entity
@Table(name = "usual_preference",
        uniqueConstraints = @UniqueConstraint(name = "uk_usual_preference_person_day",
                columnNames = {"person_name", "day_index"}))
public class UsualPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "person_name", nullable = false, length = 64)
    private String personName;

    @Column(name = "day_index", nullable = false)
    private int dayIndex;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UsualPreference() {
        // for JPA
    }

    public UsualPreference(String personName, int dayIndex, Instant updatedAt) {
        this.personName = personName;
        this.dayIndex = dayIndex;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getPersonName() {
        return personName;
    }

    public int getDayIndex() {
        return dayIndex;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
