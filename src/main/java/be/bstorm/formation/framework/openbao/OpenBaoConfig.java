package be.bstorm.formation.framework.openbao;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.vault.authentication.AppRoleAuthentication;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions.RoleId;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions.SecretId;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.config.AbstractVaultConfiguration;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.lease.SecretLeaseContainer;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.vault.support.SslConfiguration.KeyStoreConfiguration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client OpenBao (Spring Vault) : authentification AppRole, TLS 1.3 avec la CA interne en prod, renouvellement
 * automatique du token (périodique) et des baux.
 * <p>
 * Le {@code secret_id} est lu dans un fichier monté en lecture seule : jamais en variable d'environnement.
 */
@Configuration
@ConditionalOnBooleanProperty("app.openbao.enabled")
@RequiredArgsConstructor
public class OpenBaoConfig extends AbstractVaultConfiguration {

    private final OpenBaoProperties properties;

    @Override
    public VaultEndpoint vaultEndpoint() {
        return VaultEndpoint.from(properties.uri());
    }

    @Override
    public ClientAuthentication clientAuthentication() {
        AppRoleAuthenticationOptions options = AppRoleAuthenticationOptions.builder()
                .roleId(RoleId.provided(readSecretFile(properties.roleIdFile())))
                .secretId(SecretId.provided(readSecretFile(properties.secretIdFile())))
                .build();
        return new AppRoleAuthentication(options, restOperations());
    }

    @Override
    public SslConfiguration sslConfiguration() {
        if (!properties.tls()) {
            return SslConfiguration.unconfigured();
        }
        KeyStoreConfiguration trustStore = KeyStoreConfiguration.of(
                new FileSystemResource(properties.caFile()), null, SslConfiguration.PEM_KEYSTORE_TYPE);
        return SslConfiguration.forTrustStore(trustStore).withEnabledProtocols("TLSv1.3");
    }

    @Override
    @Bean
    public SecretLeaseContainer secretLeaseContainer() throws Exception {
        SecretLeaseContainer container = super.secretLeaseContainer();
        // Marge confortable : le nouvel identifiant DB est obtenu bien avant que l'ancien ne soit révoqué.
        container.setExpiryThreshold(properties.leaseExpiryThreshold());
        return container;
    }

    @Bean
    OpenBaoReadiness openBaoReadiness(VaultOperations vaultOperations) {
        return new OpenBaoReadiness(vaultOperations, properties.uri());
    }

    @Bean
    OpenBaoHealthIndicator openBaoHealthIndicator(VaultOperations vaultOperations) {
        return new OpenBaoHealthIndicator(vaultOperations);
    }

    static String readSecretFile(String file) {
        try {
            String value = Files.readString(Path.of(file)).strip();
            if (value.isEmpty()) {
                throw new IllegalStateException("Fichier de secret OpenBao vide : " + file);
            }
            return value;
        } catch (IOException e) {
            throw new UncheckedIOException("Fichier de secret OpenBao illisible : " + file
                    + " (monté en lecture seule ? voir docs/ops/OPENBAO.md)", e);
        }
    }
}
