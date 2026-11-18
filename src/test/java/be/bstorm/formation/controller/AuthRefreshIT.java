package be.bstorm.formation.controller;

import be.bstorm.formation.framework.crypto.CryptoException;
import be.bstorm.formation.framework.security.Role;
import be.bstorm.formation.support.SpiedAuthItSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static be.bstorm.formation.support.JwtTestTokens.payload;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

/** Feature 001 — refresh : RG-08 à RG-11, RG-15, RG-22, RG-23 (AC-27 à AC-34, AC-46 à AC-50, AC-56, AC-58, AC-63). */
@Tag("feature-001")
class AuthRefreshIT extends SpiedAuthItSupport {

    private static final Duration HOUR = Duration.ofHours(1);

    @Test
    @DisplayName("[AC-27] Refresh : nouveau jeton d'accès, nouveau cookie dans la même famille, ancien jeton marqué utilisé")
    void ac27_refreshRotatesTokenWithinFamily() {
        // Given
        createAlice();
        Session login = loginOk("alice", PASSWORD);
        Instant refreshAt = T0.plus(HOUR);
        clock.set(refreshAt);

        // When
        MvcTestResult result = refresh(login.cookie());

        // Then
        assertThat(result).hasStatusOk();
        String accessToken = jsonString(result, "$.accessToken");
        assertThat(accessToken).isNotEqualTo(login.accessToken());
        assertThat(((Number) payload(accessToken).get("iat")).longValue()).isEqualTo(refreshAt.getEpochSecond());
        List<SetCookie> cookies = refreshCookies(result);
        assertThat(cookies).hasSize(1);
        String newCookie = cookies.getFirst().value();
        assertThat(newCookie).isNotEmpty().isNotEqualTo(login.cookie());
        assertThat(cookies.getFirst().attr("Max-Age")).isEqualTo("28800");
        assertThat(usedAt(login.cookie())).isEqualTo(refreshAt);
        assertThat(familyOf(newCookie)).isEqualTo(familyOf(login.cookie()));
        assertThat(tokenExpiresAt(newCookie)).isEqualTo(T0.plus(Duration.ofHours(9)));
    }

    @Test
    @DisplayName("[AC-28] Le refresh relit les rôles et le dépôt actuels en base")
    void ac28_refreshUsesCurrentRolesAndDepot() {
        // Given
        UUID alice = createUser("alice", PASSWORD, Set.of(Role.DRIVER), DEPOT);
        Session login = loginOk("alice", PASSWORD);
        UUID newDepot = UUID.fromString("0193a5f2-0000-7000-8000-0000000000d2");
        addRole(alice, Role.ADMIN);
        setDepot(alice, newDepot);

        // When
        MvcTestResult result = refresh(login.cookie());

        // Then
        assertThat(result).hasStatusOk();
        assertThat(jsonStrings(result, "$.user.roles")).containsExactly("DRIVER", "ADMIN");
        assertThat(jsonString(result, "$.user.depotId")).isEqualTo(newDepot.toString());
        Map<String, Object> claims = payload(jsonString(result, "$.accessToken"));
        assertThat((List<Object>) claims.get("roles")).containsExactly((Object) "DRIVER", "ADMIN");
        assertThat(claims).containsEntry("depot_id", newDepot.toString());
    }

    @Test
    @DisplayName("[AC-29] Réutilisation d'un jeton déjà utilisé : 401 et révocation REUSE_DETECTED de toute la famille")
    void ac29_reuseRevokesWholeFamily() {
        // Given
        createAlice();
        Session login = loginOk("alice", PASSWORD);
        clock.set(T0.plusSeconds(60));
        Session refreshed = refreshOk(login.cookie());
        Instant reuseAt = T0.plusSeconds(120);
        clock.set(reuseAt);

        // When
        MvcTestResult reuse = refresh(login.cookie());

        // Then
        assertInvalidSession(reuse);
        UUID family = familyOf(login.cookie());
        assertThat(revokedAt(family)).isEqualTo(reuseAt);
        assertThat(revocationReason(family)).isEqualTo("REUSE_DETECTED");
        assertInvalidSession(refresh(refreshed.cookie()));
    }

    @Test
    @DisplayName("[AC-30] Deux refresh simultanés avec le même jeton : un 200, un 401, famille révoquée REUSE_DETECTED")
    void ac30_concurrentRefreshWithSameToken() throws Exception {
        // Given
        createAlice();
        Session login = loginOk("alice", PASSWORD);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        // When
        List<Integer> statuses;
        try {
            Future<MvcTestResult> first = pool.submit(() -> {
                start.await();
                return refresh(login.cookie());
            });
            Future<MvcTestResult> second = pool.submit(() -> {
                start.await();
                return refresh(login.cookie());
            });
            start.countDown();
            statuses = List.of(first.get(30, TimeUnit.SECONDS).getResponse().getStatus(),
                    second.get(30, TimeUnit.SECONDS).getResponse().getStatus());
        } finally {
            pool.shutdownNow();
        }

        // Then
        assertThat(statuses).containsExactlyInAnyOrder(200, 401);
        assertThat(revocationReason(familyOf(login.cookie()))).isEqualTo("REUSE_DETECTED");
    }

    @Test
    @DisplayName("[AC-31] Expiration d'inactivité : refresh accepté à T0 + 8 h - 1 s, refusé à T0 + 8 h")
    void ac31_idleExpiryBoundary() {
        // Given
        createAlice();
        Session first = loginOk("alice", PASSWORD);
        Session second = loginOk("alice", PASSWORD);

        // When
        clock.set(T0.plus(Duration.ofHours(8)).minusSeconds(1));
        MvcTestResult beforeExpiry = refresh(first.cookie());
        clock.set(T0.plus(Duration.ofHours(8)));
        MvcTestResult atExpiry = refresh(second.cookie());

        // Then
        assertThat(beforeExpiry).hasStatusOk();
        assertInvalidSession(atExpiry);
    }

    @Test
    @DisplayName("[AC-32] Expiration absolue : le jeton émis à T0 + 21 h expire à T0 + 24 h (Max-Age=10800)")
    void ac32_absoluteExpiryCapsTokenLifetime() {
        // Given
        createAlice();
        String c0 = loginOk("alice", PASSWORD).cookie();
        clock.set(T0.plus(Duration.ofHours(7)));
        String c1 = refreshOk(c0).cookie();
        clock.set(T0.plus(Duration.ofHours(14)));
        String c2 = refreshOk(c1).cookie();
        clock.set(T0.plus(Duration.ofHours(21)));

        // When
        MvcTestResult last = refresh(c2);

        // Then
        assertThat(last).hasStatusOk();
        assertThat(refreshCookies(last).getFirst().attr("Max-Age")).isEqualTo("10800");
        String c3 = issuedCookie(last);
        assertThat(tokenExpiresAt(c3)).isEqualTo(T0.plus(Duration.ofHours(24)));

        // When
        clock.set(T0.plus(Duration.ofHours(24)));

        // Then
        assertInvalidSession(refresh(c3));
    }

    @Test
    @DisplayName("[AC-33] Sans cookie, cookie inconnu, famille révoquée : même 401 et cookie effacé")
    void ac33_rejectedRefreshesAreIdenticalAndClearTheCookie() {
        // Given
        createAlice();
        Session loggedOut = loginOk("alice", PASSWORD);
        assertThat(logout(loggedOut.cookie())).hasStatus(204);

        // When
        MvcTestResult noCookie = refresh(null);
        MvcTestResult unknownCookie = refresh(UNKNOWN_REFRESH_TOKEN);
        MvcTestResult revokedFamily = refresh(loggedOut.cookie());

        // Then
        assertInvalidSession(noCookie);
        assertInvalidSession(unknownCookie);
        assertInvalidSession(revokedFamily);
        assertThat(jsonMap(unknownCookie)).isEqualTo(jsonMap(noCookie));
        assertThat(jsonMap(revokedFamily)).isEqualTo(jsonMap(noCookie));
    }

    @Test
    @DisplayName("[AC-34] Utilisateur désactivé : refresh 401 et famille révoquée USER_DISABLED")
    void ac34_disabledUserRefreshRevokesFamily() {
        // Given
        UUID alice = createAlice();
        Session login = loginOk("alice", PASSWORD);
        disable(alice);

        // When
        MvcTestResult result = refresh(login.cookie());

        // Then
        assertInvalidSession(result);
        assertThat(revocationReason(familyOf(login.cookie()))).isEqualTo("USER_DISABLED");
    }

    @Test
    @DisplayName("[AC-34] Utilisateur supprimé logiquement : refresh 401 et famille révoquée USER_DISABLED")
    void ac34_deletedUserRefreshRevokesFamily() {
        // Given
        UUID alice = createAlice();
        Session login = loginOk("alice", PASSWORD);
        softDelete(alice);

        // When
        MvcTestResult result = refresh(login.cookie());

        // Then
        assertInvalidSession(result);
        assertThat(revocationReason(familyOf(login.cookie()))).isEqualTo("USER_DISABLED");
    }

    @Test
    @DisplayName("[AC-46] Jeton déjà utilisé d'une famille révoquée par logout : 401, raison LOGOUT et revoked_at inchangés")
    void ac46_revokedFamilyKeepsItsReasonOnReuse() {
        // Given
        createAlice();
        Session login = loginOk("alice", PASSWORD);
        Session refreshed = refreshOk(login.cookie());
        assertThat(logout(refreshed.cookie())).hasStatus(204);
        UUID family = familyOf(login.cookie());
        Instant revokedAtLogout = revokedAt(family);
        clock.set(T0.plusSeconds(600));

        // When
        MvcTestResult result = refresh(login.cookie());

        // Then
        assertInvalidSession(result);
        assertThat(revocationReason(family)).isEqualTo("LOGOUT");
        assertThat(revokedAt(family)).isEqualTo(revokedAtLogout);
    }

    @Test
    @DisplayName("[AC-47] Jeton expiré d'un utilisateur désactivé : 401 sans révocation (étape 6 avant étape 7)")
    void ac47_expiredTokenOfDisabledUserIsNotRevoked() {
        // Given
        UUID alice = createAlice();
        Session login = loginOk("alice", PASSWORD);
        disable(alice);
        clock.set(T0.plus(Duration.ofHours(8)));

        // When
        MvcTestResult result = refresh(login.cookie());

        // Then
        assertInvalidSession(result);
        assertThat(revokedAt(familyOf(login.cookie()))).isNull();
    }

    @Test
    @DisplayName("[AC-47] Famille expirée : 401 sans révocation")
    void ac47_expiredFamilyIsNotRevoked() {
        // Given
        createAlice();
        String[] cookies = refreshSequenceUntil21h();
        clock.set(T0.plus(Duration.ofHours(24)));

        // When
        MvcTestResult result = refresh(cookies[3]);

        // Then
        assertInvalidSession(result);
        assertThat(revokedAt(familyOf(cookies[3]))).isNull();
    }

    @Test
    @DisplayName("[AC-47] Jeton déjà utilisé d'une famille expirée : 401 sans révocation (étape 4 avant étape 5)")
    void ac47_usedTokenOfExpiredFamilyIsNotRevoked() {
        // Given
        createAlice();
        String[] cookies = refreshSequenceUntil21h();
        clock.set(T0.plus(Duration.ofHours(24)));

        // When
        MvcTestResult result = refresh(cookies[2]);

        // Then
        assertInvalidSession(result);
        assertThat(revokedAt(familyOf(cookies[2]))).isNull();
    }

    @Test
    @DisplayName("[AC-48] Jeton déjà utilisé d'un utilisateur désactivé : REUSE_DETECTED (étape 5 avant étape 7)")
    void ac48_reuseIsDetectedBeforeDisabledUser() {
        // Given
        UUID alice = createAlice();
        Session login = loginOk("alice", PASSWORD);
        refreshOk(login.cookie());
        disable(alice);

        // When
        MvcTestResult result = refresh(login.cookie());

        // Then
        assertInvalidSession(result);
        assertThat(revocationReason(familyOf(login.cookie()))).isEqualTo("REUSE_DETECTED");
    }

    @Test
    @DisplayName("[AC-50] Logout et refresh simultanés : 204 et 200/401 (jamais 409), famille révoquée LOGOUT")
    void ac50_concurrentLogoutAndRefresh() throws Exception {
        // Given
        createAlice();
        Session login = loginOk("alice", PASSWORD);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        // When
        MvcTestResult logoutResult;
        MvcTestResult refreshResult;
        try {
            Future<MvcTestResult> logoutFuture = pool.submit(() -> {
                start.await();
                return logout(login.cookie());
            });
            Future<MvcTestResult> refreshFuture = pool.submit(() -> {
                start.await();
                return refresh(login.cookie());
            });
            start.countDown();
            logoutResult = logoutFuture.get(30, TimeUnit.SECONDS);
            refreshResult = refreshFuture.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        // Then
        assertThat(logoutResult).hasStatus(204);
        assertThat(refreshResult.getResponse().getStatus()).isIn(200, 401);
        assertThat(revocationReason(familyOf(login.cookie()))).isEqualTo("LOGOUT");
        assertThat(refresh(login.cookie())).hasStatus(401);
        if (refreshResult.getResponse().getStatus() == 200) {
            assertThat(refresh(issuedCookie(refreshResult))).hasStatus(401);
        }
    }

    @Test
    @DisplayName("[AC-56] Tous les rôles retirés : refresh 401 et famille révoquée USER_DISABLED")
    void ac56_refreshAfterAllRolesRemovedRevokesFamily() {
        // Given
        UUID alice = createAlice();
        Session login = loginOk("alice", PASSWORD);
        removeAllRoles(alice);

        // When
        MvcTestResult result = refresh(login.cookie());

        // Then
        assertInvalidSession(result);
        assertThat(revocationReason(familyOf(login.cookie()))).isEqualTo("USER_DISABLED");
    }

    @Test
    @DisplayName("[AC-58] Plusieurs cookies __Secure-refresh : seul le premier est lu (valide puis inconnu → 200)")
    void ac58_firstRefreshCookieIsUsedWhenValidComesFirst() {
        // Given
        createAlice();
        Session login = loginOk("alice", PASSWORD);

        // When
        MvcTestResult result = csrfPost(REFRESH, CLIENT_IP, login.cookie(), UNKNOWN_REFRESH_TOKEN).exchange();

        // Then
        assertThat(result).hasStatusOk();
    }

    @Test
    @DisplayName("[AC-58] Plusieurs cookies __Secure-refresh : inconnu puis valide → 401, le valide n'est pas consommé")
    void ac58_firstRefreshCookieIsUsedWhenUnknownComesFirst() {
        // Given
        createAlice();
        Session login = loginOk("alice", PASSWORD);

        // When
        MvcTestResult result = csrfPost(REFRESH, CLIENT_IP, UNKNOWN_REFRESH_TOKEN, login.cookie()).exchange();

        // Then
        assertThat(result).hasStatus(401);
        assertThat(usedAt(login.cookie())).isNull();
    }

    @Test
    @DisplayName("[AC-63] Échec de signature au refresh : 500 sans écriture, le jeton reste utilisable")
    void ac63_signingFailureOnRefreshWritesNothing() {
        // Given
        createAlice();
        Session login = loginOk("alice", PASSWORD);
        UUID family = familyOf(login.cookie());
        doThrow(new CryptoException("signing unavailable")).when(jwtKeys).sign(anyString(), any(byte[].class));

        // When
        MvcTestResult failed = refresh(login.cookie());

        // Then
        assertThat(failed).hasStatus(500);
        assertNoSetCookie(failed);
        assertThat(usedAt(login.cookie())).isNull();
        assertThat(revokedAt(family)).isNull();
        assertThat(tokenCountInFamily(family)).isEqualTo(1);

        // When
        Mockito.reset(jwtKeys);
        MvcTestResult retried = refresh(login.cookie());

        // Then
        assertThat(retried).hasStatusOk();
    }

    @Test
    @DisplayName("[AC-SEC-05] Le jeton de rafraîchissement n'apparaît dans aucun corps ni en clair en base ; HttpOnly partout")
    void acsec05_refreshTokenNeverInBodyOrDatabaseAndAlwaysHttpOnly() {
        // Given
        createAlice();

        // When
        Session login = loginOk("alice", PASSWORD);
        Session refreshed = refreshOk(login.cookie());
        MvcTestResult rejected = refresh(UNKNOWN_REFRESH_TOKEN);

        // Then
        for (String cookie : List.of(login.cookie(), refreshed.cookie())) {
            assertThat(body(login.result())).doesNotContain(cookie);
            assertThat(body(refreshed.result())).doesNotContain(cookie);
            assertThat(rowsContainingInAuthTables(cookie)).isZero();
        }
        for (MvcTestResult result : List.of(login.result(), refreshed.result(), rejected)) {
            assertThat(refreshCookies(result)).isNotEmpty().allSatisfy(c -> assertThat(c.has("HttpOnly")).isTrue());
        }
    }

    @Test
    @DisplayName("[AC-SEC-06] Rotation à chaque usage, jeton à usage unique, réutilisation = famille révoquée")
    void acsec06_rotationAndReuseDetection() {
        // Given
        createAlice();
        Session a = loginOk("alice", PASSWORD);
        Session b = refreshOk(a.cookie());
        Session c = refreshOk(b.cookie());
        assertThat(List.of(a.cookie(), b.cookie(), c.cookie())).doesNotHaveDuplicates();

        // When
        MvcTestResult reuse = refresh(b.cookie());

        // Then
        assertInvalidSession(reuse);
        assertThat(revocationReason(familyOf(c.cookie()))).isEqualTo("REUSE_DETECTED");
        assertInvalidSession(refresh(c.cookie()));
    }

    @Test
    @DisplayName("[AC-SEC-07] La session est bornée côté serveur : logout puis refresh refusé")
    void acsec07_sessionEndsServerSide() {
        // Given
        createAlice();
        Session login = loginOk("alice", PASSWORD);

        // When
        assertThat(logout(login.cookie())).hasStatus(204);

        // Then
        assertInvalidSession(refresh(login.cookie()));
        assertThat(revokedAt(familyOf(login.cookie()))).isNotNull();
    }

    @Test
    @DisplayName("[AC-SD-02] Après login et refresh, seules les empreintes SHA-256 des cookies sont stockées")
    void acsd02_onlyRefreshTokenHashesAreStored() {
        // Given
        createAlice();

        // When
        Session login = loginOk("alice", PASSWORD);
        Session refreshed = refreshOk(login.cookie());

        // Then
        for (String cookie : List.of(login.cookie(), refreshed.cookie())) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_token WHERE token_hash = ?", Integer.class,
                    sha256Hex(cookie))).isEqualTo(1);
            assertThat(rowsContainingInAuthTables(cookie)).isZero();
        }
        assertThat(refreshed.result().getResponse().getHeaders(HttpHeaders.SET_COOKIE)).hasSize(1);
    }

    /** Séquence d'AC-32 : login à T0, refresh à T0 + 7 h, 14 h, 21 h ; renvoie les cookies c0 … c3. */
    private String[] refreshSequenceUntil21h() {
        String c0 = loginOk("alice", PASSWORD).cookie();
        clock.set(T0.plus(Duration.ofHours(7)));
        String c1 = refreshOk(c0).cookie();
        clock.set(T0.plus(Duration.ofHours(14)));
        String c2 = refreshOk(c1).cookie();
        clock.set(T0.plus(Duration.ofHours(21)));
        String c3 = refreshOk(c2).cookie();
        return new String[]{c0, c1, c2, c3};
    }
}
