package cires.dft.remotescheduler.repository;

import cires.dft.remotescheduler.domain.UsualPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UsualPreferenceRepository extends JpaRepository<UsualPreference, Long> {

    List<UsualPreference> findByPersonName(String personName);

    void deleteByPersonName(String personName);
}
