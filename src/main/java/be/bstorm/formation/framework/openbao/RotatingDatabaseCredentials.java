package be.bstorm.formation.framework.openbao;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.vault.core.lease.SecretLeaseContainer;
import org.springframework.vault.core.lease.domain.RequestedSecret;
import org.springframework.vault.core.lease.event.SecretLeaseErrorEvent;
import org.springframework.vault.core.lease.event.SecretLeaseCreatedEvent;
import org.springframework.vault.core.lease.event.SecretLeaseEvent;
import org.springframework.vault.core.lease.event.SecretLeaseExpiredEvent;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Identifiants PostgreSQL dynamiques ({@code database/creds/<rôle>}) appliqués au pool Hikari, sans coupure :
 * <ul>
 *   <li>le bail est renouvelé par le {@link SecretLeaseContainer} ;</li>
 *   <li>avant son {@code max_ttl}, un nouvel identifiant est obtenu (rotation) : Hikari l'utilise pour les
 *       nouvelles connexions et retire les anciennes dès qu'elles sont rendues ({@code softEvictConnections}) ;</li>
 *   <li>l'ancien utilisateur est ensuite révoqué par OpenBao (sessions coupées, rôle supprimé).</li>
 * </ul>
 */
@Slf4j
public class RotatingDatabaseCredentials {

    public record Credentials(String username, String password) {
        @Override
        public String toString() {
            return "Credentials[username=" + username + ", password=***]";
        }
    }

    private final SecretLeaseContainer container;
    private final RequestedSecret secret;
    private final AtomicReference<Credentials> current = new AtomicReference<>();
    private final AtomicReference<HikariDataSource> dataSource = new AtomicReference<>();

    public RotatingDatabaseCredentials(SecretLeaseContainer container, String databaseRole) {
        this.container = container;
        this.secret = RequestedSecret.rotating("database/creds/" + databaseRole);
        container.register(secret, this::onLeaseEvent);
    }

    /** Démarre le conteneur de baux si nécessaire et retourne le premier identifiant (bloquant). */
    public Credentials obtain() {
        if (!container.isRunning()) {
            container.start();
        }
        Credentials credentials = current.get();
        if (credentials == null) {
            throw new IllegalStateException("Aucun identifiant reçu d'OpenBao pour " + secret.getPath());
        }
        return credentials;
    }

    /** Pool auquel appliquer les rotations suivantes. */
    public void bind(HikariDataSource hikari) {
        dataSource.set(hikari);
    }

    /** Force une rotation immédiate (tests, ou réaction à une fuite suspectée). */
    public void rotateNow() {
        container.rotate(secret);
    }

    public Credentials current() {
        return current.get();
    }

    void onLeaseEvent(SecretLeaseEvent event) {
        switch (event) {
            case SecretLeaseCreatedEvent created -> apply(created.getSecrets());
            case SecretLeaseExpiredEvent expired ->
                    log.warn("Bail DB expiré ({}) : une rotation va suivre", secret.getPath());
            case SecretLeaseErrorEvent error ->
                    log.error("Erreur de bail OpenBao ({}) : {}", secret.getPath(), error.getException().getMessage());
            default -> log.debug("Événement de bail {} : {}", secret.getPath(), event.getClass().getSimpleName());
        }
    }

    void apply(Map<String, Object> secrets) {
        Credentials next = new Credentials((String) secrets.get("username"), (String) secrets.get("password"));
        Credentials previous = current.getAndSet(next);
        HikariDataSource hikari = dataSource.get();
        if (hikari == null) {
            log.info("Identifiant DB obtenu d'OpenBao : {}", next.username());
            return;
        }
        hikari.getHikariConfigMXBean().setUsername(next.username());
        hikari.getHikariConfigMXBean().setPassword(next.password());
        HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
        if (pool != null) {
            pool.softEvictConnections();
        }
        log.info("Rotation de l'identifiant DB : {} → {} (connexions existantes retirées à leur libération)",
                previous == null ? "?" : previous.username(), next.username());
    }
}
