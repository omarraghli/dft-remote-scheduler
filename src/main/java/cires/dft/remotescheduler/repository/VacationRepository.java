package cires.dft.remotescheduler.repository;

import cires.dft.remotescheduler.domain.Vacation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface VacationRepository extends JpaRepository<Vacation, Long> {

    List<Vacation> findAllByOrderByStartDateAscPersonNameAsc();

    /** Everything overlapping a window, which is how a week asks who is away in it. */
    List<Vacation> findByStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDate until,
                                                                         LocalDate from);
}
