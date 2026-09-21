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
import java.util.List;

/**
 * A public holiday, stored rather than configured.
 *
 * <p>One row however long it runs: Aïd is a single entry of two days, not two entries. The
 * lunar ones are announced days before they fall, so they have to be editable while the app is
 * running — that is the whole reason this is a table.
 */
@Entity
@Table(name = "public_holiday",
        uniqueConstraints = @UniqueConstraint(name = "uk_public_holiday_start_date",
                columnNames = "start_date"))
public class Holiday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** How many days it runs, counting the first. */
    @Column(name = "days", nullable = false)
    private int days = 1;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Holiday() {
        // for JPA
    }

    public Holiday(String name, LocalDate startDate, int days, Instant createdAt) {
        this.name = name;
        this.startDate = startDate;
        this.days = days;
        this.createdAt = createdAt;
    }

    /** The last day off — the same as the start for a one-day holiday. */
    public LocalDate endDate() {
        return startDate.plusDays(days - 1L);
    }

    public boolean covers(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate());
    }

    public boolean overlaps(LocalDate otherStart, int otherDays) {
        return !otherStart.isAfter(endDate())
                && !otherStart.plusDays(otherDays - 1L).isBefore(startDate);
    }

    /** Every day this holiday closes. */
    public List<LocalDate> dates() {
        return startDate.datesUntil(endDate().plusDays(1)).toList();
    }

    public void edit(String name, LocalDate startDate, int days) {
        this.name = name;
        this.startDate = startDate;
        this.days = days;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public int getDays() {
        return days;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
