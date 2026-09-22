package cires.dft.remotescheduler.repository;

import cires.dft.remotescheduler.domain.WeekSchedule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WeekScheduleRepository extends JpaRepository<WeekSchedule, Long> {

    @EntityGraph(attributePaths = "assignments")
    Optional<WeekSchedule> findByWeekStart(LocalDate weekStart);

    boolean existsByWeekStart(LocalDate weekStart);

    @EntityGraph(attributePaths = "assignments")
    List<WeekSchedule> findAllByOrderByWeekStartDesc();

    @EntityGraph(attributePaths = "assignments")
    Optional<WeekSchedule> findFirstByWeekStartLessThanEqualOrderByWeekStartDesc(LocalDate date);

    @EntityGraph(attributePaths = "assignments")
    Optional<WeekSchedule> findFirstByOrderByWeekStartDesc();

    @EntityGraph(attributePaths = "assignments")
    List<WeekSchedule> findByWeekStartGreaterThanEqualOrderByWeekStartAsc(LocalDate weekStart);
}
