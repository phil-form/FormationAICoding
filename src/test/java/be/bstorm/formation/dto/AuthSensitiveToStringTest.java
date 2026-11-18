package be.bstorm.formation.dto;

import be.bstorm.formation.dto.request.LoginRequest;
import be.bstorm.formation.dto.response.CurrentUserResponse;
import be.bstorm.formation.dto.response.TokenResponse;
import be.bstorm.formation.framework.security.Role;
import be.bstorm.formation.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Feature 001 — AC-SD-03 : {@code toString()} masqués (SD-03). */
@Tag("feature-001")
class AuthSensitiveToStringTest {

    private static final String PASSWORD = "Correct-Horse-Battery-9";
    private static final String ACCESS_TOKEN = "eyJhbGciOiJFUzI1NiJ9.eyJzdWIiOiJhbGljZSJ9.c2lnbmF0dXJlLXZhbHVl";
    private static final String REFRESH_TOKEN = "Zm9vYmFyYmF6cXV4LXJlZnJlc2gtdG9rZW4tdmFsdWU";

    private static TokenResponse tokenResponse() {
        return new TokenResponse(ACCESS_TOKEN, "Bearer", 300,
                new CurrentUserResponse(UUID.fromString("0193a5f2-0000-7000-8000-0000000000a1"), List.of(Role.DRIVER), null));
    }

    @Test
    @DisplayName("[AC-SD-03] LoginRequest.toString ne contient ni le mot de passe ni l'identifiant")
    void acsd03_loginRequestToStringIsMasked() {
        // When
        String text = new LoginRequest("alice", PASSWORD).toString();

        // Then
        assertThat(text).doesNotContain(PASSWORD).doesNotContain("alice");
    }

    @Test
    @DisplayName("[AC-SD-03] TokenResponse.toString ne contient pas le jeton d'accès")
    void acsd03_tokenResponseToStringIsMasked() {
        // When
        String text = tokenResponse().toString();

        // Then
        assertThat(text).doesNotContain(ACCESS_TOKEN);
    }

    @Test
    @DisplayName("[AC-SD-03] IssuedTokens.toString ne contient ni le jeton de rafraîchissement ni le jeton d'accès")
    void acsd03_issuedTokensToStringIsMasked() {
        // When
        String text = new AuthService.IssuedTokens(tokenResponse(), REFRESH_TOKEN, Duration.ofHours(8)).toString();

        // Then
        assertThat(text).doesNotContain(REFRESH_TOKEN).doesNotContain(ACCESS_TOKEN);
    }
}
