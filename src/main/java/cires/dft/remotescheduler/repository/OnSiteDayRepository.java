package cires.dft.remotescheduler.repository;

import cires.dft.remotescheduler.domain.OnSiteDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface OnSiteDayRepository extends JpaRepository<OnSiteDay, Long> {

    List<OnSiteDay> findByWeekStart(LocalDate weekStart);

    void deleteByWeekStartAndPersonName(LocalDate weekStart, String personName);
}
