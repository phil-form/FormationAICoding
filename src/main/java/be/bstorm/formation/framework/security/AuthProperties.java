package be.bstorm.formation.framework.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Configuration de l'authentification ({@code app.auth.*}, feature 001). */
@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(@Valid @NotNull Jwt jwt, @Valid @NotNull Refresh refresh,
                             @Valid @NotNull Login login, @Valid @NotNull Password password) {

    public AuthProperties {
        if (refresh != null && refresh.idleTtl() != null && refresh.absoluteTtl() != null
                && refresh.absoluteTtl().compareTo(refresh.idleTtl()) < 0) {
            throw new IllegalArgumentException("app.auth.refresh.absolute-ttl doit être >= app.auth.refresh.idle-ttl");
        }
    }

    public record Jwt(@NotBlank String issuer, @NotBlank String audience,
                      @NotNull Duration accessTokenTtl, @NotNull Duration keyCacheRefreshInterval) {
    }

    /** absoluteTtl >= idleTtl. */
    public record Refresh(@NotNull Duration idleTtl, @NotNull Duration absoluteTtl,
                          @NotNull Duration purgeRetention, @NotBlank String purgeCron) {
    }

    public record Login(@Min(1) int maxFailedAttempts, @NotNull Duration lockDuration,
                        @Min(1) int ipMaxFailures, @NotNull Duration ipWindow, @Min(1) int ipMaxTracked) {
    }

    public record Password(@Min(16) int saltLength, @Min(32) int hashLength, @Min(1) int parallelism,
                           @Min(19456) int memoryKib, @Min(2) int iterations,
                           @Min(1) int maxConcurrentHashes, @NotNull Duration hashAcquireTimeout) {
    }
}
