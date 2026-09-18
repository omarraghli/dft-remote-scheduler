package cires.dft.remotescheduler.repository;

import cires.dft.remotescheduler.domain.AppUser;
import cires.dft.remotescheduler.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    List<AppUser> findAllByOrderByEmailAsc();

    /** Used to refuse the change that would leave nobody able to administer the app. */
    long countByRoleAndActiveTrue(Role role);
}
