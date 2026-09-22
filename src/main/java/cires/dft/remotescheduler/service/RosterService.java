package cires.dft.remotescheduler.service;

import cires.dft.remotescheduler.domain.AppUser;
import cires.dft.remotescheduler.repository.AppUserRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Who the schedule plans for: every active account that is on the schedule, joined or not. The
 * one place that answers it, so the solver, the pickers and every check of a typed name agree.
 *
 * <p>Every other table refers to a person by name, as text, rather than by id. So a rename is
 * done here across all of them in one transaction, or old weeks would grow a second row for the
 * same person.
 */
@Service
public class RosterService {

    private static final Logger log = LoggerFactory.getLogger(RosterService.class);

    private static final int MAX_NAME_LENGTH = 64;

    /** Every table besides app_user that stores a person's name, and the column it is in. */
    private static final List<String[]> NAME_COLUMNS = List.of(
            new String[] {"remote_assignment", "person_name"},
            new String[] {"remote_preference", "person_name"},
            new String[] {"on_site_day", "person_name"},
            new String[] {"vacation", "person_name"});

    private final AppUserRepository users;
    private final EntityManager entityManager;
    private final Clock clock;

    public RosterService(AppUserRepository users, EntityManager entityManager, Clock clock) {
        this.users = users;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /** Everybody the schedule plans for, sorted the way the chart lists them. */
    @Transactional(readOnly = true)
    public List<String> activeNames() {
        return users.findByActiveTrueAndOnScheduleTrue().stream()
                .map(AppUser::getRosterName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    /**
     * Matches a typed name against the team, ignoring case.
     *
     * @return the name as the schedule spells it, if they are on the schedule and active
     */
    @Transactional(readOnly = true)
    public Optional<String> activeName(String typed) {
        if (typed == null || typed.isBlank()) return Optional.empty();

        return users.findByRosterNameIgnoreCase(typed.trim())
                .filter(user -> user.isActive() && user.isOnSchedule())
                .map(AppUser::getRosterName);
    }

    @Transactional(readOnly = true)
    public boolean hasAnyone() {
        return !users.findByActiveTrueAndOnScheduleTrue().isEmpty();
    }

    /** Puts somebody on the team before they have signed up. They claim it with the join link. */
    @Transactional
    public AppUser add(String name) {
        String clean = requireUsableName(name, null);

        AppUser saved = users.save(AppUser.unjoined(clean, clock.instant()));
        log.info("{} joined the roster", clean);

        return saved;
    }

    /**
     * Renames somebody everywhere they are named, past schedules included — a rename is a
     * correction, not a new person.
     */
    @Transactional
    public void rename(Long id, String newName) {
        AppUser user = users.findById(id)
                .orElseThrow(() -> new UserManagementException("No account with id " + id));
        String clean = requireUsableName(newName, id);
        String old = user.getRosterName();

        if (clean.equals(old)) return;

        user.setRosterName(clean);
        users.flush();

        if (old != null) {
            for (String[] column : NAME_COLUMNS) {
                entityManager.createNativeQuery(
                                "UPDATE " + column[0] + " SET " + column[1] + " = :new"
                                        + " WHERE " + column[1] + " = :old")
                        .setParameter("new", clean)
                        .setParameter("old", old)
                        .executeUpdate();
            }
            // The rows just rewritten may be sitting in the persistence context under the old name.
            entityManager.clear();
        }

        log.info("{} renamed to {} everywhere", old, clean);
    }

    /** @return the name trimmed, once it is known to be usable and nobody else's */
    String requireUsableName(String name, Long ignoringId) {
        String clean = name == null ? "" : name.trim().replaceAll("\\s+", " ");

        if (clean.isEmpty()) {
            throw new UserManagementException("A name is required.");
        }
        if (clean.length() > MAX_NAME_LENGTH) {
            throw new UserManagementException(
                    "Names run at most " + MAX_NAME_LENGTH + " characters.");
        }
        if (clean.contains("|")) {
            // The week grid separates a name from a day with it.
            throw new UserManagementException("A name cannot contain \"|\".");
        }

        users.findByRosterNameIgnoreCase(clean)
                .filter(existing -> !existing.getId().equals(ignoringId))
                .ifPresent(existing -> {
                    throw new UserManagementException(existing.getRosterName()
                            + (existing.isActive() ? " is already on the team."
                                                   : " left the team — reactivate them instead."));
                });

        return clean;
    }
}
