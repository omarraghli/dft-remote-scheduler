package cires.dft.remotescheduler.repository;

import cires.dft.remotescheduler.domain.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    List<Holiday> findAllByOrderByStartDateAsc();
}
