package cires.dft.remotescheduler.repository;

import cires.dft.remotescheduler.domain.TeamInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamInviteRepository extends JpaRepository<TeamInvite, Long> {

    Optional<TeamInvite> findByTokenHash(String tokenHash);

    List<TeamInvite> findByRevokedAtIsNull();

    Optional<TeamInvite> findFirstByRevokedAtIsNullOrderByCreatedAtDesc();
}
