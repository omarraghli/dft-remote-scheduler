package cires.dft.remotescheduler.repository;

import cires.dft.remotescheduler.domain.AppUser;
import cires.dft.remotescheduler.domain.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    List<AppUser> findAllByOrderByEmailAsc();

    Optional<AppUser> findByRosterNameIgnoreCase(String rosterName);

    /** The team: everybody planned for, joined or not. */
    List<AppUser> findByActiveTrueAndOnScheduleTrue();

    /**
     * The same, locked — two people picking one name on the join page in the same second must
     * not both get it.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where lower(u.rosterName) = lower(:name)")
    Optional<AppUser> lockByRosterName(@Param("name") String name);

    boolean existsByRoleAndActiveTrueAndPasswordHashIsNotNull(Role role);

    /** Used to refuse the change that would leave nobody able to administer the app. */
    long countByRoleAndActiveTrue(Role role);
}
