package cires.dft.remotescheduler.service;

import cires.dft.remotescheduler.config.SecurityProperties;
import cires.dft.remotescheduler.domain.TeamInvite;
import cires.dft.remotescheduler.repository.TeamInviteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * The team's join link. One live at a time: issuing a new one retires the last, which is also
 * how a link that went somewhere it should not is shut.
 */
@Service
public class TeamInviteService {

    private static final Logger log = LoggerFactory.getLogger(TeamInviteService.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TeamInviteRepository invites;
    private final SecurityProperties properties;
    private final Clock clock;

    public TeamInviteService(TeamInviteRepository invites,
                             SecurityProperties properties,
                             Clock clock) {
        this.invites = invites;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * @return the token in clear — the only time it exists readable, so it is shown to the admin
     *         once and only its hash is kept
     */
    @Transactional
    public String issue(String issuedBy) {
        Instant now = clock.instant();
        invites.findByRevokedAtIsNull().forEach(invite -> invite.revoke(now));

        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        TeamInvite invite = invites.save(new TeamInvite(hash(token), issuedBy, now,
                now.plus(properties.getJoin().getTtl())));

        log.info("{} issued a join link, good until {}", issuedBy, invite.getExpiresAt());
        return token;
    }

    @Transactional
    public void revoke() {
        Instant now = clock.instant();
        invites.findByRevokedAtIsNull().forEach(invite -> invite.revoke(now));
        log.info("Join link revoked");
    }

    /** The link currently out there, if it still works — for the admin page to describe. */
    @Transactional(readOnly = true)
    public Optional<TeamInvite> live() {
        return invites.findFirstByRevokedAtIsNullOrderByCreatedAtDesc()
                .filter(invite -> invite.isLive(clock.instant()));
    }

    /** @throws UserManagementException when the link is unknown, revoked or expired */
    @Transactional(readOnly = true)
    public TeamInvite requireValid(String token) {
        if (token == null || token.isBlank()) throw invalid();

        TeamInvite invite = invites.findByTokenHash(hash(token)).orElseThrow(this::invalid);
        if (!invite.isLive(clock.instant())) throw invalid();

        return invite;
    }

    public String allowedDomain() {
        String domain = properties.getJoin().getAllowedDomain();
        return domain == null ? "" : domain.trim().toLowerCase();
    }

    private UserManagementException invalid() {
        return new UserManagementException(
                "This join link has expired or been replaced. Ask an admin for the current one.");
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
