package cires.dft.remotescheduler.service;

import cires.dft.remotescheduler.config.RemoteScheduleProperties;
import cires.dft.remotescheduler.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lists the team from {@code remote.people} on a database that has nobody on the schedule. From
 * then on the team is edited on the Équipe page and the configuration is never read again, so a
 * hire does not wait for a redeploy.
 *
 * <p>Somebody who already has an account under a configured name — every account linked to the
 * roster before an account became the person — is put on the schedule as they are, password and
 * all. Only the names nobody has are added, as people who have not signed up yet.
 */
@Component
@Order(1)
public class PersonSeed implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PersonSeed.class);

    private final RosterService roster;
    private final AppUserRepository users;
    private final RemoteScheduleProperties properties;

    public PersonSeed(RosterService roster,
                      AppUserRepository users,
                      RemoteScheduleProperties properties) {
        this.roster = roster;
        this.users = users;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (roster.hasAnyone() || properties.getPeople().isEmpty()) return;

        int kept = 0;
        int listed = 0;

        for (String name : properties.getPeople()) {
            var existing = users.findByRosterNameIgnoreCase(name.trim());

            if (existing.isPresent()) {
                if (!existing.get().isOnSchedule()) {
                    existing.get().setOnSchedule(true);
                    kept++;
                }
            } else {
                roster.add(name);
                listed++;
            }
        }

        log.info("Put {} existing accounts on the schedule and listed {} people who have not "
                + "signed up, from remote.people. The team is edited on the Équipe page from now "
                + "on.", kept, listed);
    }
}
