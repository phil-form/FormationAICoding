package be.bstorm.formation.controller;

import be.bstorm.formation.repository.UserRepository;
import be.bstorm.formation.support.AuthItSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

/** Feature 001 — AC-SEC-08 : PASSWORD_REHASHED seulement si {@code updatePasswordHash} = 1 (RG-14, SEC-10). */
@Tag("feature-001")
@MockitoSpyBean(types = UserRepository.class)
class AuthRehashLogIT extends AuthItSupport {

    @Test
    @DisplayName("[AC-SEC-08] Hachage modifié entre-temps (updatePasswordHash = 0) : login 200, aucun PASSWORD_REHASHED")
    void acsec08_noRehashEventWhenHashChangedMeanwhile(CapturedOutput output) {
        // Given
        UUID alice = createAlice();
        String before = passwordHash(alice);
        rotatePepper();
        doReturn(0).when(users).updatePasswordHash(any(), any(), any(), any());

        // When
        MvcTestResult result = login("alice", PASSWORD);

        // Then
        assertThat(result).hasStatusOk();
        assertThat(passwordHash(alice)).isEqualTo(before);
        assertThat(output.getAll()).doesNotContain("event=PASSWORD_REHASHED");
    }

    @Test
    @DisplayName("[AC-SEC-08] Re-hachage appliqué : PASSWORD_REHASHED userId=<alice>")
    void acsec08_rehashEventIsLoggedWhenApplied(CapturedOutput output) {
        // Given
        UUID alice = createAlice();
        rotatePepper();

        // When
        MvcTestResult result = login("alice", PASSWORD);

        // Then
        assertThat(result).hasStatusOk();
        assertThat(output.getAll()).contains("event=PASSWORD_REHASHED userId=" + alice);
    }
}
