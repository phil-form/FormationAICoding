package be.bstorm.formation.controller;

import be.bstorm.formation.support.SpiedAuthItSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Feature 001 — journalisation des événements de sécurité (SEC-10), découpage transactionnel (SEC-11) et identifiants
 * modifiés pendant un login (RG-25) : AC-SEC-08, AC-SEC-13, AC-68, AC-SD-01.
 */
@Tag("feature-001")
class AuthSecurityLogIT extends SpiedAuthItSupport {

    private static final String OTHER_PASSWORD = "Other-Horse-Battery-8";

    // ------------------------------------------------------------------ AC-SEC-08

    @Test
    @DisplayName("[AC-SEC-08] Échec pour identifiant inconnu : LOGIN_FAILURE UNKNOWN_USER, sans identifiant ni mot de passe saisis")
    void acsec08_unknownUserFailureIsLoggedWithoutInputs(CapturedOutput output) {
        // When
        assertThat(login("mallory-probe", "Wr0ng-P4ssw0rd-XYZ")).hasStatus(401);

        // Then
        assertThat(eventLines(output, "LOGIN_FAILURE")).anyMatch(line -> line.contains("reason=UNKNOWN_USER"));
        assertThat(output.getAll()).doesNotContain("mallory-probe").doesNotContain("Wr0ng-P4ssw0rd-XYZ");
    }

    @Test
    @DisplayName("[AC-SEC-08] Login puis refresh : LOGIN_SUCCESS et REFRESH_SUCCESS, sans secret dans les logs")
    void acsec08_successEventsAreLoggedWithoutSecrets(CapturedOutput output) {
        // Given
        UUID alice = createAlice();

        // When
        Session login = loginOk("alice", PASSWORD);
        Session refreshed = refreshOk(login.cookie());

        // Then
        assertThat(eventLines(output, "LOGIN_SUCCESS")).isNotEmpty();
        assertThat(eventLines(output, "REFRESH_SUCCESS")).isNotEmpty();
        assertThat(output.getAll())
                .doesNotContain(PASSWORD)
                .doesNotContain(login.accessToken())
                .doesNotContain(refreshed.accessToken())
                .doesNotContain(login.cookie())
                .doesNotContain(refreshed.cookie())
                .doesNotContain(passwordHash(alice));
    }

    @Test
    @DisplayName("[AC-SEC-08] Réutilisation : REFRESH_REUSE_DETECTED avec familyId")
    void acsec08_reuseIsLoggedWithFamilyId(CapturedOutput output) {
        // Given
        createAlice();
        Session login = loginOk("alice", PASSWORD);
        refreshOk(login.cookie());

        // When
        assertThat(refresh(login.cookie())).hasStatus(401);

        // Then
        String family = familyOf(login.cookie()).toString();
        assertThat(eventLines(output, "REFRESH_REUSE_DETECTED")).anyMatch(line -> line.contains("familyId=" + family));
    }

    @Test
    @DisplayName("[AC-SEC-08] Rejet CSRF : CSRF_REJECTED")
    void acsec08_csrfRejectionIsLogged(CapturedOutput output) {
        // Given
        createAlice();
        Session login = loginOk("alice", PASSWORD);

        // When
        assertThat(postWithHeaders(REFRESH, login.cookie(), ALLOWED_ORIGIN, null)).hasStatus(403);

        // Then
        assertThat(eventLines(output, "CSRF_REJECTED")).isNotEmpty();
    }

    @Test
    @DisplayName("[AC-SEC-08] 5e échec : LOGIN_FAILURE BAD_PASSWORD puis ACCOUNT_LOCKED (une seule fois)")
    void acsec08_lockIsLoggedAfterTheFailure(CapturedOutput output) {
        // Given
        UUID alice = createAlice();

        // When
        lockByFailures("alice", CLIENT_IP);

        // Then
        String all = output.getAll();
        int locked = all.indexOf("event=ACCOUNT_LOCKED userId=" + alice);
        assertThat(locked).isNotNegative();
        assertThat(all.lastIndexOf("reason=BAD_PASSWORD")).isNotNegative().isLessThan(locked);
        assertThat(eventLines(output, "ACCOUNT_LOCKED")).hasSize(1);
    }

    @Test
    @DisplayName("[AC-SEC-08] Désactivé + sans rôle + verrouillé : reason=DISABLED (priorité)")
    void acsec08_disabledHasPriorityOverNoRoleAndLocked(CapturedOutput output) {
        // Given
        UUID erin = createUser("erin", PASSWORD, Set.of(), null);
        disable(erin);
        lockUntil(erin, T0.plus(Duration.ofMinutes(10)));

        // When
        assertThat(login("erin", PASSWORD)).hasStatus(401);

        // Then
        assertThat(eventLines(output, "LOGIN_FAILURE"))
                .anyMatch(line -> line.contains("userId=" + erin) && line.contains("reason=DISABLED"));
    }

    @Test
    @DisplayName("[AC-SEC-08] Actif + sans rôle + verrouillé : reason=NO_ROLE (priorité)")
    void acsec08_noRoleHasPriorityOverLocked(CapturedOutput output) {
        // Given
        UUID frank = createUser("frank", PASSWORD, Set.of(), null);
        lockUntil(frank, T0.plus(Duration.ofMinutes(10)));

        // When
        assertThat(login("frank", PASSWORD)).hasStatus(401);

        // Then
        assertThat(eventLines(output, "LOGIN_FAILURE"))
                .anyMatch(line -> line.contains("userId=" + frank) && line.contains("reason=NO_ROLE"));
    }

    // ------------------------------------------------------------------ AC-SEC-13

    @Test
    @DisplayName("[AC-SEC-13] Aucune transaction active pendant le poivre (verify / dummyVerify / hash) ni la signature")
    void acsec13_noTransactionDuringPepperOrSigning() {
        // Given
        createAlice();
        List<Boolean> transactionActive = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive());
            return invocation.callRealMethod();
        }).when(pepper).pepper(anyString());
        doAnswer(invocation -> {
            transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive());
            return invocation.callRealMethod();
        }).when(pepper).pepper(anyString(), anyInt());
        doAnswer(invocation -> {
            transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive());
            return invocation.callRealMethod();
        }).when(jwtKeys).sign(anyString(), any(byte[].class));

        // When
        MvcTestResult unknown = login("nobody-here", WRONG_PASSWORD);
        MvcTestResult wrongPassword = login("alice", WRONG_PASSWORD);
        rotatePepper();
        MvcTestResult rehashed = login("alice", PASSWORD);
        MvcTestResult refreshed = refresh(issuedCookie(rehashed));

        // Then
        assertThat(unknown).hasStatus(401);
        assertThat(wrongPassword).hasStatus(401);
        assertThat(rehashed).hasStatusOk();
        assertThat(refreshed).hasStatusOk();
        assertThat(transactionActive).isNotEmpty().containsOnly(false);
    }

    // ------------------------------------------------------------------ AC-68

    @Test
    @DisplayName("[AC-68] (a) Mot de passe changé pendant verify : 401, aucune écriture de succès, CREDENTIALS_CHANGED")
    void ac68_passwordChangedDuringVerifyFailsLogin(CapturedOutput output) {
        // Given
        UUID alice = givenAliceWithTwoFailures();
        String h2 = passwordHasher.hash(OTHER_PASSWORD);
        Map<String, Object> reference = invalidCredentialsReference();
        clearInvocations(rateLimiter);
        updateDuringVerify("UPDATE app_user SET password_hash = ? WHERE id = ?", h2, alice);

        // When
        MvcTestResult result = login("alice", PASSWORD);

        // Then
        assertCredentialsChangedFailure(result, reference, alice, output);
        assertThat(passwordHash(alice)).isEqualTo(h2);

        // When
        Mockito.reset(passwordHasher);
        MvcTestResult withNewPassword = login("alice", OTHER_PASSWORD);

        // Then
        assertThat(withNewPassword).hasStatusOk();
    }

    @Test
    @DisplayName("[AC-68] (b) Compte désactivé pendant verify : 401, aucune écriture de succès, CREDENTIALS_CHANGED")
    void ac68_accountDisabledDuringVerifyFailsLogin(CapturedOutput output) {
        // Given
        UUID alice = givenAliceWithTwoFailures();
        String h1 = passwordHash(alice);
        Map<String, Object> reference = invalidCredentialsReference();
        clearInvocations(rateLimiter);
        updateDuringVerify("UPDATE app_user SET enabled = false WHERE id = ?", alice);

        // When
        MvcTestResult result = login("alice", PASSWORD);

        // Then
        assertCredentialsChangedFailure(result, reference, alice, output);
        assertThat(passwordHash(alice)).isEqualTo(h1);
    }

    @Test
    @DisplayName("[AC-68] (c) Compte supprimé pendant verify : 401, aucune écriture de succès, CREDENTIALS_CHANGED")
    void ac68_accountDeletedDuringVerifyFailsLogin(CapturedOutput output) {
        // Given
        UUID alice = givenAliceWithTwoFailures();
        String h1 = passwordHash(alice);
        Map<String, Object> reference = invalidCredentialsReference();
        clearInvocations(rateLimiter);
        updateDuringVerify("UPDATE app_user SET deleted_at = now() WHERE id = ?", alice);

        // When
        MvcTestResult result = login("alice", PASSWORD);

        // Then
        assertCredentialsChangedFailure(result, reference, alice, output);
        assertThat(passwordHash(alice)).isEqualTo(h1);
    }

    @Test
    @DisplayName("[AC-68] Témoin : sans modification pendant verify, le même login réussit")
    void ac68_loginSucceedsWithoutConcurrentChange() {
        // Given
        givenAliceWithTwoFailures();

        // When
        MvcTestResult result = login("alice", PASSWORD);

        // Then
        assertThat(result).hasStatusOk();
    }

    // ------------------------------------------------------------------ AC-SD-01

    @Test
    @DisplayName("[AC-SD-01] Le mot de passe n'apparaît ni en base ni dans les logs (login réussi puis échoué)")
    void acsd01_passwordIsNeitherStoredNorLogged(CapturedOutput output) {
        // Given
        createAlice();

        // When
        loginOk("alice", PASSWORD);
        assertThat(login("nobody-here", PASSWORD)).hasStatus(401);

        // Then
        assertThat(rowsContainingInAuthTables(PASSWORD)).isZero();
        assertThat(output.getAll()).doesNotContain(PASSWORD);
    }

    // ------------------------------------------------------------------ outils

    private UUID givenAliceWithTwoFailures() {
        UUID alice = createAlice();
        setFailedLoginCount(alice, 2);
        return alice;
    }

    /** verify appelle la méthode réelle puis exécute la mise à jour (auto-commit) avant de rendre la main. */
    private void updateDuringVerify(String sql, Object... args) {
        doAnswer(invocation -> {
            Object verification = invocation.callRealMethod();
            jdbc.update(sql, args);
            return verification;
        }).when(passwordHasher).verify(anyString(), anyString());
    }

    private void assertCredentialsChangedFailure(MvcTestResult result, Map<String, Object> reference, UUID alice,
                                                 CapturedOutput output) {
        assertInvalidCredentials(result);
        assertThat(jsonMap(result)).isEqualTo(reference);
        assertThat(familyCount(alice)).isZero();
        assertThat(tokenCountForUser(alice)).isZero();
        assertThat(lastLoginAt(alice)).isNull();
        assertThat(failedLoginCount(alice)).isEqualTo(2);
        verify(rateLimiter, times(1)).recordFailure(anyString());
        assertThat(output.getAll()).contains("event=LOGIN_FAILURE userId=" + alice);
        assertThat(eventLines(output, "LOGIN_FAILURE"))
                .anyMatch(line -> line.contains("userId=" + alice) && line.contains("reason=CREDENTIALS_CHANGED"));
        assertThat(output.getAll()).doesNotContain("event=LOGIN_SUCCESS");
    }
}
