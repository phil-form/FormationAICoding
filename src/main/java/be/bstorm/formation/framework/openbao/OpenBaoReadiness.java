package be.bstorm.formation.framework.openbao;

import lombok.RequiredArgsConstructor;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.VaultHealth;

import java.net.URI;

/**
 * Vérifie au démarrage qu'OpenBao est joignable, initialisé et descellé : sinon l'API refuse de démarrer avec un
 * message explicite (plutôt qu'une erreur de connexion DB obscure).
 */
@RequiredArgsConstructor
public class OpenBaoReadiness {

    private final VaultOperations vaultOperations;
    private final URI uri;

    public void assertReady() {
        VaultHealth health;
        try {
            health = vaultOperations.opsForSys().health();
        } catch (RuntimeException e) {
            throw new IllegalStateException("OpenBao injoignable (" + uri + ") : l'API ne peut pas obtenir ses "
                    + "secrets. Vérifier le conteneur openbao (docs/ops/OPENBAO.md).", e);
        }
        if (!health.isInitialized()) {
            throw new IllegalStateException("OpenBao (" + uri + ") n'est pas initialisé : "
                    + "lancer docker/prod/openbao/bootstrap.sh.");
        }
        if (health.isSealed()) {
            throw new IllegalStateException("OpenBao (" + uri + ") est SCELLÉ : descellement requis "
                    + "(docker/prod/openbao/unseal.sh, 3 parts Shamir).");
        }
    }
}
