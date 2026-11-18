package be.bstorm.formation.framework.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Journal des événements de sécurité (SEC-10), logger nommé {@code security}, une ligne par événement :
 * {@code event=<EVENT> userId=<uuid|-> ip=<ip|->[ familyId=<uuid>][ reason=<RAISON>][ count=<n>]}.
 * <p>
 * Jamais : mot de passe, identifiant saisi, hachage, poivre, jeton d'accès, jeton de rafraîchissement, cookie.
 */
public final class SecurityEvents {

    public static final String LOGGER_NAME = "security";

    private static final Logger LOG = LoggerFactory.getLogger(LOGGER_NAME);

    private SecurityEvents() {
    }

    public static void info(String event, UUID userId, String ip, UUID familyId, String reason, Integer count) {
        if (LOG.isInfoEnabled()) {
            LOG.info(line(event, userId, ip, familyId, reason, count));
        }
    }

    public static void warn(String event, UUID userId, String ip, UUID familyId, String reason, Integer count) {
        if (LOG.isWarnEnabled()) {
            LOG.warn(line(event, userId, ip, familyId, reason, count));
        }
    }

    static String line(String event, UUID userId, String ip, UUID familyId, String reason, Integer count) {
        StringBuilder sb = new StringBuilder(128)
                .append("event=").append(event)
                .append(" userId=").append(userId == null ? "-" : userId.toString())
                .append(" ip=").append(sanitize(ip));
        if (familyId != null) {
            sb.append(" familyId=").append(familyId);
        }
        if (reason != null) {
            sb.append(" reason=").append(reason);
        }
        if (count != null) {
            sb.append(" count=").append(count);
        }
        return sb.toString();
    }

    /** Adresse IP littérale : tout caractère inattendu est remplacé (aucune injection de ligne de log). */
    private static String sanitize(String ip) {
        if (ip == null || ip.isBlank()) {
            return "-";
        }
        StringBuilder sb = new StringBuilder(ip.length());
        for (int i = 0; i < ip.length() && i < 64; i++) {
            char c = ip.charAt(i);
            sb.append(Character.isLetterOrDigit(c) || c == '.' || c == ':' || c == '%' || c == '-' || c == '_' ? c : '?');
        }
        return sb.toString();
    }
}
