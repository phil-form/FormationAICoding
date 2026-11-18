package be.bstorm.formation.controller;

import be.bstorm.formation.framework.security.Role;
import be.bstorm.formation.support.SpiedAuthItSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Feature 001 — login : RG-01 à RG-05, RG-15, RG-24 (analyse § 4 « Login », § 7.3). */
@Tag("feature-001")
class AuthLoginIT extends SpiedAuthItSupport {

    private static final String DISTINCT_PASSWORD = "Distinct-Pass-777";

    @Test
    @DisplayName("[AC-01] Login réussi : jeton d'accès, utilisateur courant, une famille et un jeton de rafraîchissement")
    void ac01_loginReturnsAccessTokenAndCurrentUserAndCreatesFamily() {
        // Given
        UUID alice = createAlice();

        // When
        MvcTestResult result = login("alice", PASSWORD);

        // Then
        assertThat(result).hasStatusOk();
        String accessToken = jsonString(result, "$.accessToken");
        assertThat(accessToken).isNotBlank();
        assertThat(accessToken.split("\\.", -1)).hasSize(3);
        assertThat(jsonString(result, "$.tokenType")).isEqualTo("Bearer");
        assertThat(jsonLong(result, "$.expiresIn")).isEqualTo(300L);
        assertThat(jsonString(result, "$.user.id")).isEqualTo(alice.toString());
        assertThat(jsonStrings(result, "$.user.roles")).containsExactly("DRIVER", "FLEET_MANAGER");
        assertThat(jsonString(result, "$.user.depotId")).isEqualTo(DEPOT.toString());

        assertThat(familyCount(alice)).isEqualTo(1);
        String cookie = issuedCookie(result);
        UUID family = familyOf(cookie);
        assertThat(absoluteExpiresAt(family)).isEqualTo(T0.plus(Duration.ofHours(24)));
        assertThat(revokedAt(family)).isNull();
        assertThat(tokenCountInFamily(family)).isEqualTo(1);
        assertThat(tokenExpiresAt(cookie)).isEqualTo(T0.plus(Duration.ofHours(8)));
        assertThat(usedAt(cookie)).isNull();
    }

    @Test
    @DisplayName("[AC-02] Cookie __Secure-refresh HttpOnly/Secure/Strict, absent du corps, seule son empreinte SHA-256 en base")
    void ac02_refreshCookieAttributesAndOnlyItsSha256IsStored() {
        // Given
        createAlice();

        // When
        MvcTestResult result = login("alice", PASSWORD);

        // Then
        List<SetCookie> cookies = refreshCookies(result);
        assertThat(cookies).hasSize(1);
        SetCookie cookie = cookies.getFirst();
        assertThat(cookie.value()).matches("[A-Za-z0-9_-]{43}");
        assertThat(cookie.has("HttpOnly")).isTrue();
        assertThat(cookie.has("Secure")).isTrue();
        assertThat(cookie.attr("SameSite")).isEqualToIgnoringCase("Strict");
        assertThat(cookie.attr("Path")).isEqualTo("/api/v1/auth");
        assertThat(cookie.attr("Max-Age")).isEqualTo("28800");
        assertThat(cookie.has("Domain")).isFalse();
        assertThat(body(result)).doesNotContain(cookie.value());
        assertThat(jdbc.queryForObject("SELECT token_hash FROM refresh_token", String.class))
                .isEqualTo(sha256Hex(cookie.value()));
        assertThat(rowsContainingInAuthTables(cookie.value())).isZero();
    }

    @Test
    @DisplayName("[AC-03] L'identifiant est insensible à la casse et aux espaces de bord")
    void ac03_usernameIsNormalizedBeforeLookup() {
        // Given
        createAlice();

        // When
        MvcTestResult result = login("  ALICE ", PASSWORD);

        // Then
        assertThat(result).hasStatusOk();
    }

    @Test
    @DisplayName("[AC-04] Identifiant inconnu, mauvais mot de passe et identifiant hors format : même 401")
    void ac04_everyCredentialFailureGivesTheSameUnauthorizedResponse() {
        // Given
        createAlice();

        // When
        MvcTestResult unknown = login("nobody-here", WRONG_PASSWORD);
        MvcTestResult wrongPassword = login("alice", WRONG_PASSWORD);
        MvcTestResult outOfFormat = login("alice@example", "Any-Password-1");

        // Then
        assertInvalidCredentials(unknown);
        assertInvalidCredentials(wrongPassword);
        assertInvalidCredentials(outOfFormat);
        assertThat(jsonMap(wrongPassword)).isEqualTo(jsonMap(unknown));
        assertThat(jsonMap(outOfFormat)).isEqualTo(jsonMap(unknown));
    }

    @Test
    @DisplayName("[AC-05] Compte désactivé ou supprimé logiquement : 401 identique, aucune famille")
    void ac05_disabledOrDeletedAccountCannotLogIn() {
        // Given
        UUID bob = createUser("bob", PASSWORD, Set.of(Role.DRIVER), null);
        disable(bob);
        UUID carol = createUser("carol", PASSWORD, Set.of(Role.DRIVER), null);
        softDelete(carol);
        Map<String, Object> reference = invalidCredentialsReference();

        // When
        MvcTestResult bobResult = login("bob", PASSWORD);
        MvcTestResult carolResult = login("carol", PASSWORD);

        // Then
        assertInvalidCredentials(bobResult);
        assertInvalidCredentials(carolResult);
        assertThat(jsonMap(bobResult)).isEqualTo(reference);
        assertThat(jsonMap(carolResult)).isEqualTo(reference);
        assertThat(familyCount(bob)).isZero();
        assertThat(familyCount(carol)).isZero();
    }

    @Test
    @DisplayName("[AC-06] Le 5e mauvais mot de passe verrouille le compte 15 minutes et remet le compteur à 0")
    void ac06_fifthFailureLocksAccountForFifteenMinutes() {
        // Given
        UUID alice = createAlice();

        // When
        for (int i = 0; i < 4; i++) {
            assertInvalidCredentials(login("alice", WRONG_PASSWORD));
        }

        // Then
        assertThat(failedLoginCount(alice)).isEqualTo(4);
        assertThat(lockedUntil(alice)).isNull();

        // When
        Instant t = T0.plusSeconds(30);
        clock.set(t);
        MvcTestResult fifth = login("alice", WRONG_PASSWORD);

        // Then
        assertInvalidCredentials(fifth);
        assertThat(failedLoginCount(alice)).isZero();
        assertThat(lockedUntil(alice)).isEqualTo(t.plus(Duration.ofMinutes(15)));
    }

    @Test
    @DisplayName("[AC-07] Compte verrouillé : bon mot de passe refusé jusqu'à T + 15 min, accepté à T + 15 min")
    void ac07_lockedAccountRejectsCorrectPasswordUntilLockEnds() {
        // Given
        UUID alice = createAlice();
        Map<String, Object> reference = invalidCredentialsReference();
        lockByFailures("alice", CLIENT_IP);
        Instant lockEnd = T0.plus(Duration.ofMinutes(15));
        assertThat(lockedUntil(alice)).isEqualTo(lockEnd);

        // When
        clock.set(T0.plus(Duration.ofMinutes(14)).plusSeconds(59));
        MvcTestResult duringLock = login("alice", PASSWORD);

        // Then
        assertInvalidCredentials(duringLock);
        assertThat(jsonMap(duringLock)).isEqualTo(reference);
        assertThat(lockedUntil(alice)).isEqualTo(lockEnd);
        assertThat(failedLoginCount(alice)).isZero();

        // When
        clock.set(lockEnd);
        MvcTestResult afterLock = login("alice", PASSWORD);

        // Then
        assertThat(afterLock).hasStatusOk();
        assertThat(lockedUntil(alice)).isNull();
    }

    @Test
    @DisplayName("[AC-08] Un login réussi remet le compteur à 0 et fixe last_login_at")
    void ac08_successfulLoginResetsCounterAndSetsLastLogin() {
        // Given
        UUID alice = createAlice();
        setFailedLoginCount(alice, 3);

        // When
        MvcTestResult result = login("alice", PASSWORD);

        // Then
        assertThat(result).hasStatusOk();
        assertThat(failedLoginCount(alice)).isZero();
        assertThat(lastLoginAt(alice)).isEqualTo(T0);
    }

    @Test
    @DisplayName("[AC-09] Le verrouillage n'affecte pas une session déjà ouverte")
    void ac09_lockDoesNotAffectOpenSession() {
        // Given
        UUID alice = createAlice();
        Session session = loginOk("alice", PASSWORD);
        lockByFailures("alice", CLIENT_IP);
        assertThat(lockedUntil(alice)).isNotNull();

        // When
        MvcTestResult result = refresh(session.cookie());

        // Then
        assertThat(result).hasStatusOk();
    }

    @Test
    @DisplayName("[AC-10] 20 échecs depuis une IP : 429 + Retry-After même avec de bons identifiants ; autre IP non bloquée")
    void ac10_ipBlockedAfterTwentyFailures() {
        // Given
        createAlice();
        failUnknownLogins("203.0.113.10", 20);

        // When
        clock.set(T0.plusSeconds(60));
        MvcTestResult blocked = login("alice", PASSWORD, "203.0.113.10");

        // Then
        assertProblem(blocked, 429, "Too Many Requests", "Too many failed login attempts. Retry later.");
        assertThat(blocked).hasHeader(HttpHeaders.RETRY_AFTER, "240");
        assertNoSetCookie(blocked);
        assertThat(login("alice", PASSWORD, "203.0.113.11")).hasStatusOk();
    }

    @Test
    @DisplayName("[AC-11] Le blocage IP dure jusqu'à la fin exacte de la fenêtre (Retry-After: 1 à T0 + 4 min 59 s)")
    void ac11_ipBlockEndsExactlyAtWindowEnd() {
        // Given
        createAlice();
        failUnknownLogins("203.0.113.10", 20);

        // When
        clock.set(T0.plus(Duration.ofMinutes(4)).plusSeconds(59));
        MvcTestResult lastSecond = login("alice", PASSWORD, "203.0.113.10");

        // Then
        assertThat(lastSecond).hasStatus(429).hasHeader(HttpHeaders.RETRY_AFTER, "1");

        // When
        clock.set(T0.plus(Duration.ofMinutes(5)));
        MvcTestResult windowEnd = login("alice", PASSWORD, "203.0.113.10");

        // Then
        assertThat(windowEnd).hasStatusOk();
    }

    @Test
    @DisplayName("[AC-12] Les requêtes de login invalides (400) ne comptent pas comme échecs IP")
    void ac12_validationErrorsDoNotCountAsIpFailures() {
        // Given
        createAlice();
        for (int i = 0; i < 25; i++) {
            assertThat(loginRaw("{\"username\":\"\",\"password\":\"x\"}", "203.0.113.12")).hasStatus(400);
        }

        // When
        MvcTestResult result = login("alice", PASSWORD, "203.0.113.12");

        // Then
        assertThat(result).hasStatusOk();
    }

    static Stream<Arguments> invalidLoginBodies() {
        String longUsername = "u".repeat(101);
        String longPassword = "P".repeat(129);
        return Stream.of(
                Arguments.of("{\"password\":\"" + DISTINCT_PASSWORD + "\"}", "username", DISTINCT_PASSWORD),
                Arguments.of("{\"username\":\"\",\"password\":\"" + DISTINCT_PASSWORD + "\"}", "username", DISTINCT_PASSWORD),
                Arguments.of("{\"username\":\"" + longUsername + "\",\"password\":\"" + DISTINCT_PASSWORD + "\"}",
                        "username", longUsername),
                Arguments.of("{\"username\":\"alice\"}", "password", null),
                Arguments.of("{\"username\":\"alice\",\"password\":\"\"}", "password", null),
                Arguments.of("{\"username\":\"alice\",\"password\":\"" + longPassword + "\"}", "password", longPassword));
    }

    @ParameterizedTest(name = "champ {1}")
    @MethodSource("invalidLoginBodies")
    @DisplayName("[AC-13] Champ absent, vide ou trop long : 400 Validation Failed sans écho de la valeur")
    void ac13_invalidFieldIsRejectedWithoutEchoingValue(String requestBody, String field, String secretValue) {
        // Given
        createAlice();

        // When
        MvcTestResult result = loginRaw(requestBody, CLIENT_IP);

        // Then
        assertProblem(result, 400, "Validation Failed", null);
        assertThat(jsonStrings(result, "$.errors[*].field")).contains(field);
        if (secretValue != null) {
            assertThat(body(result)).doesNotContain(secretValue);
        }
    }

    @Test
    @DisplayName("[AC-14] Champ inconnu : 400 ; Content-Type text/plain : 415")
    void ac14_unknownFieldIs400AndNonJsonIs415() {
        // Given
        createAlice();

        // When
        MvcTestResult unknownField = loginRaw(
                "{\"username\":\"alice\",\"password\":\"" + PASSWORD + "\",\"role\":\"ADMIN\"}", CLIENT_IP);
        MvcTestResult textPlain = mvc.post().uri(LOGIN).contentType(MediaType.TEXT_PLAIN)
                .content("alice " + PASSWORD).with(ip(CLIENT_IP)).exchange();

        // Then
        assertThat(unknownField).hasStatus(400);
        assertThat(textPlain).hasStatus(415);
    }

    @Test
    @DisplayName("[AC-52] Un mauvais mot de passe après expiration du verrou efface le verrou et compte 1 échec")
    void ac52_failureAfterExpiredLockClearsLock() {
        // Given
        UUID alice = createAlice();
        lockByFailures("alice", CLIENT_IP);
        assertThat(lockedUntil(alice)).isEqualTo(T0.plus(Duration.ofMinutes(15)));

        // When
        clock.set(T0.plus(Duration.ofMinutes(16)));
        MvcTestResult result = login("alice", WRONG_PASSWORD);

        // Then
        assertInvalidCredentials(result);
        assertThat(failedLoginCount(alice)).isEqualTo(1);
        assertThat(lockedUntil(alice)).isNull();
    }

    @Test
    @DisplayName("[AC-56] Compte actif sans aucun rôle : 401 identique, aucune famille")
    void ac56_accountWithoutRoleCannotLogIn() {
        // Given
        UUID dave = createUser("dave", PASSWORD, Set.of(), null);
        Map<String, Object> reference = invalidCredentialsReference();

        // When
        MvcTestResult result = login("dave", PASSWORD);

        // Then
        assertInvalidCredentials(result);
        assertThat(jsonMap(result)).isEqualTo(reference);
        assertThat(familyCount(dave)).isZero();
    }

    @Test
    @DisplayName("[AC-59] Le 429 expose Retry-After au front (Access-Control-Expose-Headers)")
    void ac59_retryAfterIsExposedThroughCors() {
        // Given
        createAlice();
        failUnknownLogins("203.0.113.59", 20);

        // When
        MvcTestResult result = mvc.post().uri(LOGIN).contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("alice", PASSWORD))
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .with(ip("203.0.113.59")).exchange();

        // Then
        assertThat(result).hasStatus(429);
        assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
                .containsIgnoringCase("Retry-After");
    }

    @Test
    @DisplayName("[AC-61] La validation (400 / 415) passe avant le contrôle IP et ne débloque ni ne compte rien")
    void ac61_validationRunsBeforeIpCheck() {
        // Given
        createAlice();
        failUnknownLogins("203.0.113.30", 20);

        // When
        MvcTestResult invalid = loginRaw("{\"username\":\"\",\"password\":\"x\"}", "203.0.113.30");
        MvcTestResult textPlain = mvc.post().uri(LOGIN).contentType(MediaType.TEXT_PLAIN)
                .content("alice").with(ip("203.0.113.30")).exchange();
        MvcTestResult correct = login("alice", PASSWORD, "203.0.113.30");

        // Then
        assertProblem(invalid, 400, "Validation Failed", null);
        assertThat(textPlain).hasStatus(415);
        assertThat(correct).hasStatus(429);
    }

    @Test
    @DisplayName("[AC-65] Un login réussi ne remet pas le compteur IP à zéro")
    void ac65_successDoesNotResetIpCounter() {
        // Given
        createAlice();
        failUnknownLogins("203.0.113.40", 19);
        clock.set(T0.plusSeconds(30));
        assertThat(login("alice", PASSWORD, "203.0.113.40")).hasStatusOk();
        clock.set(T0.plusSeconds(60));
        assertThat(login("unknown-20", WRONG_PASSWORD, "203.0.113.40")).hasStatus(401);

        // When
        clock.set(T0.plusSeconds(90));
        MvcTestResult result = login("alice", PASSWORD, "203.0.113.40");

        // Then
        assertThat(result).hasStatus(429);
    }

    @Test
    @DisplayName("[AC-SEC-02] dummyVerify pour inconnu / désactivé / verrouillé / sans rôle, verify pour mauvais mot de passe ; corps identiques")
    void acsec02_sameVerificationCostForEveryFailureCause() {
        // Given
        createAlice();
        UUID bob = createUser("bob", PASSWORD, Set.of(Role.DRIVER), null);
        disable(bob);
        UUID carol = createUser("carol", PASSWORD, Set.of(Role.DRIVER), null);
        lockUntil(carol, T0.plus(Duration.ofMinutes(10)));
        createUser("dave", PASSWORD, Set.of(), null);
        clearInvocations(passwordHasher);
        List<Map<String, Object>> bodies = new ArrayList<>();

        // When / Then : chemins sans hachage réel
        for (String username : List.of("nobody-here", "bob", "carol", "dave")) {
            MvcTestResult result = login(username, PASSWORD);
            assertInvalidCredentials(result);
            verify(passwordHasher, times(1)).dummyVerify(anyString());
            verify(passwordHasher, never()).verify(anyString(), anyString());
            bodies.add(jsonMap(result));
            clearInvocations(passwordHasher);
        }

        // When / Then : compte actif, mauvais mot de passe
        MvcTestResult wrongPassword = login("alice", WRONG_PASSWORD);
        assertInvalidCredentials(wrongPassword);
        verify(passwordHasher, times(1)).verify(anyString(), anyString());
        verify(passwordHasher, never()).dummyVerify(anyString());
        bodies.add(jsonMap(wrongPassword));

        assertThat(bodies).hasSize(5).allSatisfy(b -> assertThat(b).isEqualTo(bodies.getFirst()));
    }

    @Test
    @DisplayName("[AC-SEC-03] IP bloquée : 429 sans aucune vérification de mot de passe")
    void acsec03_blockedIpSkipsPasswordVerification() {
        // Given
        createAlice();
        failUnknownLogins("203.0.113.33", 20);
        assertThat(login("alice", PASSWORD, "203.0.113.33")).hasStatus(429);
        clearInvocations(passwordHasher);

        // When
        MvcTestResult result = login("alice", PASSWORD, "203.0.113.33");

        // Then
        assertThat(result).hasStatus(429);
        verify(passwordHasher, never()).verify(anyString(), anyString());
        verify(passwordHasher, never()).dummyVerify(anyString());
    }

    @Test
    @DisplayName("[AC-SEC-09] Login en application/x-www-form-urlencoded : 415")
    void acsec09_formUrlEncodedLoginIsUnsupported() {
        // Given
        createAlice();

        // When
        MvcTestResult result = mvc.post().uri(LOGIN).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("username=alice&password=" + PASSWORD).with(ip(CLIENT_IP)).exchange();

        // Then
        assertThat(result).hasStatus(415);
    }

    @Test
    @DisplayName("[AC-SEC-10] Réponses login / refresh : Cache-Control no-store, aucun champ secret dans le corps")
    void acsec10_responsesAreNotCacheableAndCarryNoSecretField() {
        // Given
        createAlice();

        // When
        Session login = loginOk("alice", PASSWORD);
        Session refreshed = refreshOk(login.cookie());

        // Then
        for (MvcTestResult result : List.of(login.result(), refreshed.result())) {
            assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
            assertThat(body(result))
                    .doesNotContain("\"password\"")
                    .doesNotContain("\"passwordHash\"")
                    .doesNotContain("\"refreshToken\"");
        }
    }

    @Test
    @DisplayName("[AC-SEC-11] Bornes de taille : identifiant 100 / mot de passe 128 acceptés en validation, 101 / 129 refusés")
    void acsec11_fieldSizeBounds() {
        // Given
        createAlice();

        // When / Then
        assertThat(login("a".repeat(100), PASSWORD)).hasStatus(401);
        assertThat(login("a".repeat(101), PASSWORD)).hasStatus(400);
        assertThat(login("alice", "P".repeat(128))).hasStatus(401);
        assertThat(login("alice", "P".repeat(129))).hasStatus(400);
    }
}
