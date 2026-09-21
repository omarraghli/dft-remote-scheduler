package cires.dft.remotescheduler.repository;

import cires.dft.remotescheduler.domain.RemotePreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface RemotePreferenceRepository extends JpaRepository<RemotePreference, Long> {

    List<RemotePreference> findByWeekStart(LocalDate weekStart);

    void deleteByWeekStartAndPersonName(LocalDate weekStart, String personName);
}
