package cires.dft.remotescheduler.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * A stretch of leave: one person, away from one date to another.
 *
 * <p>One row however long it runs, a single day included, because the only question anyone asks
 * of it is whether a given day is one of theirs. Days off are not remote days — somebody away is
 * not working from home, they are not working — so the days are taken out of their week and the
 * remote days they are owed shrink with what is left.
 */
@Entity
@Table(name = "vacation")
public class Vacation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "person_name", nullable = false, length = 64)
    private String personName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Vacation() {
        // for JPA
    }

    public Vacation(String personName, LocalDate startDate, LocalDate endDate, Instant createdAt) {
        this.personName = personName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.createdAt = createdAt;
    }

    public boolean covers(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    public boolean overlaps(LocalDate otherStart, LocalDate otherEnd) {
        return !otherStart.isAfter(endDate) && !otherEnd.isBefore(startDate);
    }

    /** How many days it runs, counting both ends. */
    public int days() {
        return (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    /** The day after it ends, which is where the search for the first day back begins. */
    public LocalDate dayAfter() {
        return endDate.plusDays(1);
    }

    public void edit(String personName, LocalDate startDate, LocalDate endDate) {
        this.personName = personName;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public Long getId() {
        return id;
    }

    public String getPersonName() {
        return personName;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
