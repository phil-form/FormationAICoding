package be.bstorm.formation.service;

import be.bstorm.formation.entity.SessionRevocationReason;
import be.bstorm.formation.entity.User;
import be.bstorm.formation.framework.entity.SoftDeleter;
import be.bstorm.formation.support.SpiedAuthItSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Feature 001 — {@link AuthService} appelé directement : RG-16 (AC-41, AC-69) et RG-17 (AC-42). */
@Tag("feature-001")
class AuthServiceIT extends SpiedAuthItSupport {

    private static final UUID F1 = UUID.fromString("0193a5f2-0000-7000-8000-0000000000f1");
    private static final UUID F2 = UUID.fromString("0193a5f2-0000-7000-8000-0000000000f2");
    private static final UUID F3 = UUID.fromString("0193a5f2-0000-7000-8000-0000000000f3");
    private static final UUID F4 = UUID.fromString("0193a5f2-0000-7000-8000-0000000000f4");

    @Autowired
    AuthService authService;
    @Autowired
    SoftDeleter softDeleter;

    @Test
    @DisplayName("[AC-41] revokeAllSessions révoque les familles actives, conserve les raisons existantes, idempotent, journalisé")
    void ac41_revokeAllSessionsRevokesActiveFamiliesOnly(CapturedOutput output) {
        // Given
        UUID alice = createAlice();
        Session first = loginOk("alice", PASSWORD);
        Session second = loginOk("alice", PASSWORD);
        Session loggedOut = loginOk("alice", PASSWORD);
        assertThat(logout(loggedOut.cookie())).hasStatus(204);
        Instant revocationTime = T0.plusSeconds(60);
        clock.set(revocationTime);

        // When
        authService.revokeAllSessions(alice, SessionRevocationReason.PASSWORD_CHANGED);

        // Then
        assertThat(revocationReason(familyOf(first.cookie()))).isEqualTo("PASSWORD_CHANGED");
        assertThat(revocationReason(familyOf(second.cookie()))).isEqualTo("PASSWORD_CHANGED");
        assertThat(revocationReason(familyOf(loggedOut.cookie()))).isEqualTo("LOGOUT");
        assertThat(refresh(first.cookie())).hasStatus(401);
        assertThat(refresh(second.cookie())).hasStatus(401);
        assertThat(eventLines(output, "SESSIONS_REVOKED")).anyMatch(line ->
                line.contains("event=SESSIONS_REVOKED userId=" + alice + " ip=-")
                        && line.contains("reason=PASSWORD_CHANGED")
                        && line.contains("count=2"));

        // When
        clock.set(T0.plusSeconds(120));
        authService.revokeAllSessions(alice, SessionRevocationReason.PASSWORD_CHANGED);

        // Then
        assertThat(revokedAt(familyOf(first.cookie()))).isEqualTo(revocationTime);
        assertThat(revocationReason(familyOf(first.cookie()))).isEqualTo("PASSWORD_CHANGED");
        assertThat(revocationReason(familyOf(loggedOut.cookie()))).isEqualTo("LOGOUT");
    }

    @Test
    @DisplayName("[AC-41] Transaction englobante annulée : aucune révocation et aucune ligne SESSIONS_REVOKED")
    void ac41_rolledBackRevocationIsNeitherAppliedNorLogged(CapturedOutput output) {
        // Given
        UUID alice = createAlice();
        Session session = loginOk("alice", PASSWORD);

        // When
        tx.executeWithoutResult(status -> {
            authService.revokeAllSessions(alice, SessionRevocationReason.USER_DISABLED);
            status.setRollbackOnly();
        });

        // Then
        assertThat(revokedAt(familyOf(session.cookie()))).isNull();
        assertThat(output.getAll()).doesNotContain("event=SESSIONS_REVOKED");
    }

    @Test
    @DisplayName("[AC-42] Purge : supprime les familles expirées ou révoquées depuis plus de 7 jours et leurs jetons")
    void ac42_purgeDeletesOldExpiredOrRevokedFamilies() {
        // Given
        UUID alice = createAlice();
        Instant now = T0;
        insertFamily(F1, alice, now.minus(Duration.ofDays(8)));
        insertRevokedFamily(F2, alice, now.plus(Duration.ofDays(1)), now.minus(Duration.ofDays(8)));
        insertFamily(F3, alice, now.minus(Duration.ofDays(6)));
        insertFamily(F4, alice, now.plus(Duration.ofDays(1)));
        insertToken(F1, "purge-f1");
        insertToken(F2, "purge-f2");
        insertToken(F3, "purge-f3");
        insertToken(F4, "purge-f4");

        // When
        int purged = authService.purgeExpiredSessions();

        // Then
        assertThat(purged).isEqualTo(2);
        assertThat(familyExists(F1)).isFalse();
        assertThat(familyExists(F2)).isFalse();
        assertThat(familyExists(F3)).isTrue();
        assertThat(familyExists(F4)).isTrue();
        assertThat(tokenCountInFamily(F1)).isZero();
        assertThat(tokenCountInFamily(F2)).isZero();
        assertThat(tokenCountInFamily(F3)).isEqualTo(1);
        assertThat(tokenCountInFamily(F4)).isEqualTo(1);
    }

    @Test
    @DisplayName("[AC-69] (a) Désactivation par dirty checking : revokeAllSessions envoie l'UPDATE app_user avant la révocation")
    void ac69_pendingDisableIsFlushedBeforeRevocation() {
        // Given
        UUID alice = createAlice();
        Session session = loginOk("alice", PASSWORD);

        // When
        Boolean[] reads = tx.execute(status -> {
            User user = users.findById(alice).orElseThrow();
            user.setEnabled(false);
            Boolean before = jdbc.queryForObject("select enabled from app_user where id = ?", Boolean.class, alice);
            authService.revokeAllSessions(alice, SessionRevocationReason.USER_DISABLED);
            Boolean after = jdbc.queryForObject("select enabled from app_user where id = ?", Boolean.class, alice);
            return new Boolean[]{before, after};
        });

        // Then
        assertThat(reads).containsExactly(true, false);
        assertThat(revocationReason(familyOf(session.cookie()))).isEqualTo("USER_DISABLED");
        assertThat(refresh(session.cookie())).hasStatus(401);
    }

    @Test
    @DisplayName("[AC-69] (b) Suppression logique par SoftDeleter : revokeAllSessions envoie l'UPDATE app_user avant la révocation")
    void ac69_pendingSoftDeleteIsFlushedBeforeRevocation() {
        // Given
        UUID alice = createAlice();
        Session session = loginOk("alice", PASSWORD);

        // When
        OffsetDateTime[] reads = tx.execute(status -> {
            User user = users.findById(alice).orElseThrow();
            softDeleter.delete(user);
            OffsetDateTime before = jdbc.queryForObject("select deleted_at from app_user where id = ?",
                    OffsetDateTime.class, alice);
            authService.revokeAllSessions(alice, SessionRevocationReason.USER_DISABLED);
            OffsetDateTime after = jdbc.queryForObject("select deleted_at from app_user where id = ?",
                    OffsetDateTime.class, alice);
            return new OffsetDateTime[]{before, after};
        });

        // Then
        assertThat(reads[0]).isNull();
        assertThat(reads[1]).isNotNull();
        assertThat(revocationReason(familyOf(session.cookie()))).isEqualTo("USER_DISABLED");
        assertThat(refresh(session.cookie())).hasStatus(401);
    }

    private void insertFamily(UUID id, UUID userId, Instant absoluteExpiresAt) {
        jdbc.update("INSERT INTO refresh_token_family (id, created_at, updated_at, version, user_id, absolute_expires_at) "
                        + "VALUES (?, ?, ?, 0, ?, ?)",
                id, odt(T0.minus(Duration.ofDays(9))), odt(T0.minus(Duration.ofDays(9))), userId, odt(absoluteExpiresAt));
    }

    private void insertRevokedFamily(UUID id, UUID userId, Instant absoluteExpiresAt, Instant revokedAt) {
        jdbc.update("INSERT INTO refresh_token_family (id, created_at, updated_at, version, user_id, absolute_expires_at, "
                        + "revoked_at, revocation_reason) VALUES (?, ?, ?, 0, ?, ?, ?, 'LOGOUT')",
                id, odt(T0.minus(Duration.ofDays(9))), odt(revokedAt), userId, odt(absoluteExpiresAt), odt(revokedAt));
    }

    private void insertToken(UUID familyId, String seed) {
        jdbc.update("INSERT INTO refresh_token (id, created_at, updated_at, version, family_id, token_hash, expires_at) "
                        + "VALUES (?, ?, ?, 0, ?, ?, ?)",
                UUID.nameUUIDFromBytes(seed.getBytes()), odt(T0.minus(Duration.ofDays(9))),
                odt(T0.minus(Duration.ofDays(9))), familyId, sha256Hex(seed), odt(T0.minus(Duration.ofDays(8))));
    }

    private boolean familyExists(UUID familyId) {
        return jdbc.queryForObject("SELECT count(*) FROM refresh_token_family WHERE id = ?", Integer.class, familyId) == 1;
    }
}
