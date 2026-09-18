package cires.dft.remotescheduler.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One person working remotely on one day of a {@link WeekSchedule}. */
@Entity
@Table(name = "remote_assignment",
        indexes = {
                @Index(name = "idx_remote_assignment_week", columnList = "week_schedule_id"),
                @Index(name = "idx_remote_assignment_person", columnList = "person_name")
        })
public class RemoteAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "week_schedule_id", nullable = false)
    private WeekSchedule weekSchedule;

    @Column(name = "person_name", nullable = false, length = 64)
    private String personName;

    /** Position in the configured day list, so ordering survives renaming a day. */
    @Column(name = "day_index", nullable = false)
    private int dayIndex;

    /** The day label as configured, denormalised so old schedules still read correctly. */
    @Column(name = "day_name", nullable = false, length = 32)
    private String dayName;

    protected RemoteAssignment() {
        // for JPA
    }

    RemoteAssignment(WeekSchedule weekSchedule, String personName, int dayIndex, String dayName) {
        this.weekSchedule = weekSchedule;
        this.personName = personName;
        this.dayIndex = dayIndex;
        this.dayName = dayName;
    }

    public Long getId() {
        return id;
    }

    public WeekSchedule getWeekSchedule() {
        return weekSchedule;
    }

    public String getPersonName() {
        return personName;
    }

    public int getDayIndex() {
        return dayIndex;
    }

    public String getDayName() {
        return dayName;
    }
}
