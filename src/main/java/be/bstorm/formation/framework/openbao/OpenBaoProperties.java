package be.bstorm.formation.framework.openbao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

/**
 * Connexion de l'API à OpenBao ({@code app.openbao.*}, ADR 0001). Désactivée par défaut (tests, lancement sans
 * coffre) ; activée par les profils dev et prod.
 *
 * @param enabled             active l'intégration (identifiants DB dynamiques, Transit…)
 * @param uri                 adresse d'OpenBao ({@code https://openbao:8200} en prod)
 * @param roleIdFile          fichier contenant le role_id de l'AppRole {@code api}
 * @param secretIdFile        fichier contenant le secret_id (monté en lecture seule, renouvelé à chaque déploiement)
 * @param caFile              CA interne (PEM) pour le TLS vers OpenBao ; vide = pas de TLS (dev)
 * @param databaseRole        rôle du moteur database pour le runtime (DML uniquement)
 * @param migratorRole        rôle du moteur database pour Flyway (propriétaire du schéma via SET ROLE)
 * @param schemaOwnerRole     rôle PostgreSQL propriétaire des objets (SET ROLE pendant les migrations)
 * @param leaseExpiryThreshold avance prise pour renouveler / faire tourner un bail avant son expiration
 */
@Validated
@ConfigurationProperties(prefix = "app.openbao")
public record OpenBaoProperties(
        boolean enabled,
        @NotNull URI uri,
        @NotBlank String roleIdFile,
        @NotBlank String secretIdFile,
        String caFile,
        @NotBlank String databaseRole,
        @NotBlank String migratorRole,
        @NotBlank String schemaOwnerRole,
        @NotNull Duration leaseExpiryThreshold
) {

    public boolean tls() {
        return caFile != null && !caFile.isBlank();
    }
}
