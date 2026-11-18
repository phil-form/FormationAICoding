package be.bstorm.formation.framework.security;

import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitation des échecs de login par adresse IP cliente (RG-05, SEC-03), en mémoire (instance unique).
 * <ul>
 *   <li>clé : IPv4 telle quelle ; IPv6 → préfixe /64 ; IPv6 « IPv4-mapped » → l'IPv4 ; adresse non littérale → chaîne
 *       brute. Analyse par {@link InetAddress#ofLiteral(String)} uniquement : <b>jamais de résolution DNS</b> ;</li>
 *   <li>bloquée ssi {@code count >= ip-max-failures} et {@code now < windowStart + ip-window} ;</li>
 *   <li>table bornée à {@code ip-max-tracked} clés : à l'insertion dans une table pleine, les fenêtres terminées sont
 *       nettoyées puis, si besoin, la clé de plus ancien {@code windowStart} est évincée.</li>
 * </ul>
 * L'adresse reçue est {@code HttpServletRequest#getRemoteAddr()} (résolue par Tomcat {@code RemoteIpValve}) : aucun
 * en-tête d'IP n'est lu ici.
 */
@Component
public class LoginRateLimiter {

    private final int maxFailures;
    private final Duration window;
    private final int maxTracked;
    private final Clock clock;

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    private record Entry(Instant windowStart, int count) {
    }

    public LoginRateLimiter(AuthProperties properties, Clock clock) {
        AuthProperties.Login login = properties.login();
        this.maxFailures = login.ipMaxFailures();
        this.window = login.ipWindow();
        this.maxTracked = login.ipMaxTracked();
        this.clock = clock;
    }

    /** Durée restante du blocage de la clé de cette IP (RG-05), vide si non bloquée. */
    public Optional<Duration> blockedFor(String clientIp) {
        Entry entry = entries.get(key(clientIp));
        if (entry == null || entry.count() < maxFailures) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Instant end = entry.windowStart().plus(window);
        if (now.isBefore(end)) {
            return Optional.of(Duration.between(now, end));
        }
        return Optional.empty();
    }

    /** Enregistre un échec de login (401 uniquement). */
    public synchronized void recordFailure(String clientIp) {
        String key = key(clientIp);
        Instant now = clock.instant();
        Entry entry = entries.get(key);
        if (entry == null) {
            makeRoom(now);
            entries.put(key, new Entry(now, 1));
        } else if (!now.isBefore(entry.windowStart().plus(window))) {
            entries.put(key, new Entry(now, 1));
        } else {
            entries.put(key, new Entry(entry.windowStart(), entry.count() + 1));
        }
    }

    /** Vide toute la table (tests ; aucune route ne l'expose). */
    public synchronized void reset() {
        entries.clear();
    }

    private void makeRoom(Instant now) {
        if (entries.size() < maxTracked) {
            return;
        }
        Iterator<Map.Entry<String, Entry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            if (!now.isBefore(it.next().getValue().windowStart().plus(window))) {
                it.remove();
            }
        }
        while (entries.size() >= maxTracked) {
            String oldest = null;
            Instant oldestStart = null;
            for (Map.Entry<String, Entry> e : entries.entrySet()) {
                if (oldestStart == null || e.getValue().windowStart().isBefore(oldestStart)) {
                    oldest = e.getKey();
                    oldestStart = e.getValue().windowStart();
                }
            }
            if (oldest == null) {
                return;
            }
            entries.remove(oldest);
        }
    }

    /** Clé de comptage (RG-05), sans aucune résolution DNS. */
    static String key(String clientIp) {
        if (clientIp == null) {
            return "-";
        }
        InetAddress address;
        try {
            address = InetAddress.ofLiteral(clientIp);
        } catch (IllegalArgumentException e) {
            return clientIp;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address || bytes.length == 4) {
            return ipv4(bytes, 0);
        }
        if (isIpv4Mapped(bytes)) {
            return ipv4(bytes, 12);
        }
        return "v6:" + HexFormat.of().formatHex(Arrays.copyOf(bytes, 8)) + "/64";
    }

    private static boolean isIpv4Mapped(byte[] bytes) {
        if (bytes.length != 16) {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return (bytes[10] & 0xff) == 0xff && (bytes[11] & 0xff) == 0xff;
    }

    private static String ipv4(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) + "." + (bytes[offset + 1] & 0xff) + "."
                + (bytes[offset + 2] & 0xff) + "." + (bytes[offset + 3] & 0xff);
    }
}
