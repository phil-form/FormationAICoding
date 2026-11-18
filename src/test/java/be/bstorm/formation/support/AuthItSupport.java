package be.bstorm.formation.support;

import be.bstorm.formation.TestcontainersConfig;
import be.bstorm.formation.entity.User;
import be.bstorm.formation.framework.crypto.InMemoryPasswordPepper;
import be.bstorm.formation.framework.crypto.PasswordPepper;
import be.bstorm.formation.framework.security.AuthCookies;
import be.bstorm.formation.framework.security.AuthProperties;
import be.bstorm.formation.framework.security.LoginRateLimiter;
import be.bstorm.formation.framework.security.Role;
import be.bstorm.formation.framework.security.jwt.InMemoryJwtKeyProvider;
import be.bstorm.formation.framework.security.jwt.JwtKeyProvider;
import be.bstorm.formation.framework.security.password.PasswordHasher;
import be.bstorm.formation.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Socle des tests d'intégration de la feature 001 (MockMvc + PostgreSQL Testcontainers).
 * <ul>
 *   <li>pas de {@code @Transactional} (conventions § 4) : nettoyage SQL explicite avant chaque test ;</li>
 *   <li>horloge réglable {@link MutableClock} remise à {@link #T0} ; limiteur IP vidé avant chaque test ;</li>
 *   <li>origine CORS autorisée de test = {@link #ALLOWED_ORIGIN}.</li>
 * </ul>
 */
@SpringBootTest(properties = "app.security.cors.allowed-origins=" + AuthItSupport.ALLOWED_ORIGIN)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, AuthTestConfig.class})
@ExtendWith(OutputCaptureExtension.class)
public abstract class AuthItSupport {

    public static final String ALLOWED_ORIGIN = "https://app.example.test";
    public static final Instant T0 = Instant.parse("2026-01-15T08:00:00Z");

    public static final String LOGIN = "/api/v1/auth/login";
    public static final String REFRESH = "/api/v1/auth/refresh";
    public static final String LOGOUT = "/api/v1/auth/logout";
    public static final String ME = "/api/v1/auth/me";
    public static final String CSRF_HEADER = "X-Requested-With";

    public static final String CLIENT_IP = "192.0.2.10";
    public static final String PASSWORD = "Correct-Horse-Battery-9";
    public static final String WRONG_PASSWORD = "Wrong-Horse-Battery-0";
    public static final UUID DEPOT = UUID.fromString("0193a5f2-0000-7000-8000-0000000000d1");
    /** Jeton de rafraîchissement bien formé (43 caractères base64url) mais inconnu. */
    public static final String UNKNOWN_REFRESH_TOKEN = "A".repeat(43);

    @Autowired
    protected MockMvcTester mvc;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected TransactionTemplate tx;
    @Autowired
    protected UserRepository users;
    @Autowired
    protected PasswordHasher passwordHasher;
    @Autowired
    protected LoginRateLimiter rateLimiter;
    @Autowired
    protected JwtKeyProvider jwtKeys;
    @Autowired
    protected PasswordPepper pepper;
    @Autowired
    protected MutableClock clock;
    @Autowired
    protected AuthProperties authProperties;

    @BeforeEach
    void resetAuthenticationState() {
        jdbc.update("DELETE FROM refresh_token");
        jdbc.update("DELETE FROM refresh_token_family");
        jdbc.update("DELETE FROM app_user_role");
        jdbc.update("DELETE FROM app_user");
        rateLimiter.reset();
        clock.set(T0);
    }

    // ------------------------------------------------------------------ utilisateurs

    protected UUID createUser(String username, String rawPassword, Set<Role> roles, UUID depotId) {
        return createUserWithHash(username, passwordHasher.hash(rawPassword), roles, depotId);
    }

    protected UUID createUserWithHash(String username, String passwordHash, Set<Role> roles, UUID depotId) {
        User user = User.builder()
                .username(username)
                .passwordHash(passwordHash)
                .roles(new HashSet<>(roles))
                .depotId(depotId)
                .build();
        return users.save(user).getId();
    }

    /** alice : active, rôles DRIVER + FLEET_MANAGER, dépôt {@link #DEPOT}, mot de passe {@link #PASSWORD}. */
    protected UUID createAlice() {
        return createUser("alice", PASSWORD, Set.of(Role.DRIVER, Role.FLEET_MANAGER), DEPOT);
    }

    protected void disable(UUID userId) {
        jdbc.update("UPDATE app_user SET enabled = false WHERE id = ?", userId);
    }

    protected void softDelete(UUID userId) {
        jdbc.update("UPDATE app_user SET deleted_at = ? WHERE id = ?", odt(clock.instant()), userId);
    }

    protected void setFailedLoginCount(UUID userId, int count) {
        jdbc.update("UPDATE app_user SET failed_login_count = ? WHERE id = ?", count, userId);
    }

    protected void lockUntil(UUID userId, Instant until) {
        jdbc.update("UPDATE app_user SET locked_until = ? WHERE id = ?", odt(until), userId);
    }

    protected void removeAllRoles(UUID userId) {
        jdbc.update("DELETE FROM app_user_role WHERE user_id = ?", userId);
    }

    protected void addRole(UUID userId, Role role) {
        jdbc.update("INSERT INTO app_user_role (user_id, role) VALUES (?, ?)", userId, role.name());
    }

    protected void setDepot(UUID userId, UUID depotId) {
        jdbc.update("UPDATE app_user SET depot_id = ? WHERE id = ?", depotId, userId);
    }

    protected void setPasswordHash(UUID userId, String hash) {
        jdbc.update("UPDATE app_user SET password_hash = ? WHERE id = ?", hash, userId);
    }

    protected int failedLoginCount(UUID userId) {
        return jdbc.queryForObject("SELECT failed_login_count FROM app_user WHERE id = ?", Integer.class, userId);
    }

    protected Instant lockedUntil(UUID userId) {
        return instant("SELECT locked_until FROM app_user WHERE id = ?", userId);
    }

    protected Instant lastLoginAt(UUID userId) {
        return instant("SELECT last_login_at FROM app_user WHERE id = ?", userId);
    }

    protected String passwordHash(UUID userId) {
        return jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id = ?", String.class, userId);
    }

    protected long userVersion(UUID userId) {
        return jdbc.queryForObject("SELECT version FROM app_user WHERE id = ?", Long.class, userId);
    }

    /** Version de poivre d'un hachage stocké {@code p<version>$…}. */
    protected static int pepperVersion(String storedHash) {
        return Integer.parseInt(storedHash.substring(1, storedHash.indexOf('$')));
    }

    protected void rotatePepper() {
        ((InMemoryPasswordPepper) pepper).rotate();
    }

    protected void rotateJwtKey() {
        ((InMemoryJwtKeyProvider) jwtKeys).rotate();
    }

    // ------------------------------------------------------------------ sessions en base

    protected int familyCount(UUID userId) {
        return jdbc.queryForObject("SELECT count(*) FROM refresh_token_family WHERE user_id = ?", Integer.class, userId);
    }

    protected int tokenCountForUser(UUID userId) {
        return jdbc.queryForObject("SELECT count(*) FROM refresh_token t JOIN refresh_token_family f "
                + "ON f.id = t.family_id WHERE f.user_id = ?", Integer.class, userId);
    }

    protected UUID familyOf(String refreshCookie) {
        return jdbc.queryForObject("SELECT family_id FROM refresh_token WHERE token_hash = ?", UUID.class,
                sha256Hex(refreshCookie));
    }

    protected String revocationReason(UUID familyId) {
        return jdbc.queryForObject("SELECT revocation_reason FROM refresh_token_family WHERE id = ?", String.class,
                familyId);
    }

    protected Instant revokedAt(UUID familyId) {
        return instant("SELECT revoked_at FROM refresh_token_family WHERE id = ?", familyId);
    }

    protected Instant absoluteExpiresAt(UUID familyId) {
        return instant("SELECT absolute_expires_at FROM refresh_token_family WHERE id = ?", familyId);
    }

    protected long familyVersion(UUID familyId) {
        return jdbc.queryForObject("SELECT version FROM refresh_token_family WHERE id = ?", Long.class, familyId);
    }

    protected int tokenCountInFamily(UUID familyId) {
        return jdbc.queryForObject("SELECT count(*) FROM refresh_token WHERE family_id = ?", Integer.class, familyId);
    }

    protected Instant usedAt(String refreshCookie) {
        return instant("SELECT used_at FROM refresh_token WHERE token_hash = ?", sha256Hex(refreshCookie));
    }

    protected Instant tokenExpiresAt(String refreshCookie) {
        return instant("SELECT expires_at FROM refresh_token WHERE token_hash = ?", sha256Hex(refreshCookie));
    }

    protected long tokenVersion(String refreshCookie) {
        return jdbc.queryForObject("SELECT version FROM refresh_token WHERE token_hash = ?", Long.class,
                sha256Hex(refreshCookie));
    }

    /** Nombre de lignes de la table dont la représentation texte contient la valeur. */
    protected int rowsContaining(String table, String value) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " t WHERE strpos(t::text, ?) > 0",
                Integer.class, value);
    }

    protected int rowsContainingInAuthTables(String value) {
        return rowsContaining("app_user", value) + rowsContaining("app_user_role", value)
                + rowsContaining("refresh_token_family", value) + rowsContaining("refresh_token", value);
    }

    protected Instant instant(String sql, Object... args) {
        OffsetDateTime value = jdbc.queryForObject(sql, OffsetDateTime.class, args);
        return value == null ? null : value.toInstant();
    }

    protected static OffsetDateTime odt(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    // ------------------------------------------------------------------ requêtes HTTP

    protected static RequestPostProcessor ip(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    protected static String loginJson(String username, String password) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("password", password);
        return JwtTestTokens.toJson(body);
    }

    protected MvcTestResult login(String username, String password) {
        return login(username, password, CLIENT_IP);
    }

    protected MvcTestResult login(String username, String password, String clientIp) {
        return loginRaw(loginJson(username, password), clientIp);
    }

    protected MvcTestResult loginRaw(String body, String clientIp) {
        return mvc.post().uri(LOGIN).contentType(MediaType.APPLICATION_JSON).content(body)
                .with(ip(clientIp)).exchange();
    }

    /** POST avec en-têtes CSRF valides et, dans l'ordre, les cookies {@code __Secure-refresh} donnés. */
    protected MockMvcTester.MockMvcRequestBuilder csrfPost(String uri, String clientIp, String... refreshCookies) {
        MockMvcTester.MockMvcRequestBuilder builder = mvc.post().uri(uri)
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(CSRF_HEADER, "XMLHttpRequest")
                .with(ip(clientIp));
        if (refreshCookies.length > 0) {
            builder.cookie(Arrays.stream(refreshCookies)
                    .map(value -> new Cookie(AuthCookies.REFRESH_COOKIE, value))
                    .toArray(Cookie[]::new));
        }
        return builder;
    }

    /** POST avec un cookie de rafraîchissement et des en-têtes {@code Origin} / {@code X-Requested-With} choisis (null = absent). */
    protected MvcTestResult postWithHeaders(String uri, String refreshCookie, String origin, String requestedWith) {
        MockMvcTester.MockMvcRequestBuilder builder = mvc.post().uri(uri).with(ip(CLIENT_IP));
        if (refreshCookie != null) {
            builder.cookie(new Cookie(AuthCookies.REFRESH_COOKIE, refreshCookie));
        }
        if (origin != null) {
            builder.header(HttpHeaders.ORIGIN, origin);
        }
        if (requestedWith != null) {
            builder.header(CSRF_HEADER, requestedWith);
        }
        return builder.exchange();
    }

    protected MvcTestResult refresh(String refreshCookie) {
        return refreshCookie == null
                ? csrfPost(REFRESH, CLIENT_IP).exchange()
                : csrfPost(REFRESH, CLIENT_IP, refreshCookie).exchange();
    }

    protected MvcTestResult logout(String refreshCookie) {
        return refreshCookie == null
                ? csrfPost(LOGOUT, CLIENT_IP).exchange()
                : csrfPost(LOGOUT, CLIENT_IP, refreshCookie).exchange();
    }

    protected MvcTestResult me(String accessToken) {
        return mvc.get().uri(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .with(ip(CLIENT_IP)).exchange();
    }

    protected Session loginOk(String username, String password) {
        MvcTestResult result = login(username, password);
        assertThat(result).hasStatusOk();
        return new Session(jsonString(result, "$.accessToken"), issuedCookie(result), result);
    }

    protected Session refreshOk(String refreshCookie) {
        MvcTestResult result = refresh(refreshCookie);
        assertThat(result).hasStatusOk();
        return new Session(jsonString(result, "$.accessToken"), issuedCookie(result), result);
    }

    /** 20 logins échoués (identifiants inconnus {@code unknown-01} …) depuis l'adresse donnée : clé IP bloquée (RG-05). */
    protected void failUnknownLogins(String clientIp, int count) {
        for (int i = 1; i <= count; i++) {
            assertThat(login("unknown-%02d".formatted(i), WRONG_PASSWORD, clientIp)).hasStatus(401);
        }
    }

    /** 5 mauvais mots de passe : compte verrouillé à l'instant courant de l'horloge (RG-04). */
    protected void lockByFailures(String username, String clientIp) {
        for (int i = 0; i < 5; i++) {
            assertThat(login(username, WRONG_PASSWORD, clientIp)).hasStatus(401);
        }
    }

    /** Corps de référence d'un échec de login (identifiant inconnu, adresse IP dédiée). */
    protected Map<String, Object> invalidCredentialsReference() {
        MvcTestResult reference = login("nobody-reference", WRONG_PASSWORD, "192.0.2.250");
        assertInvalidCredentials(reference);
        return jsonMap(reference);
    }

    public record Session(String accessToken, String cookie, MvcTestResult result) {
    }

    // ------------------------------------------------------------------ lecture des réponses

    protected static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    protected static String jsonString(MvcTestResult result, String path) {
        Object value = JsonPath.read(body(result), path);
        return value == null ? null : value.toString();
    }

    protected static long jsonLong(MvcTestResult result, String path) {
        Number value = JsonPath.read(body(result), path);
        return value.longValue();
    }

    protected static List<String> jsonStrings(MvcTestResult result, String path) {
        List<?> values = JsonPath.read(body(result), path);
        return values.stream().map(String::valueOf).toList();
    }

    protected static Map<String, Object> jsonMap(MvcTestResult result) {
        return JsonPath.parse(body(result)).read("$");
    }

    protected static List<SetCookie> refreshCookies(MvcTestResult result) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .map(SetCookie::parse)
                .filter(cookie -> cookie.name().equals(AuthCookies.REFRESH_COOKIE))
                .toList();
    }

    /** Valeur de l'unique cookie {@code __Secure-refresh} émis. */
    protected static String issuedCookie(MvcTestResult result) {
        List<SetCookie> cookies = refreshCookies(result);
        assertThat(cookies).hasSize(1);
        assertThat(cookies.getFirst().value()).isNotEmpty();
        return cookies.getFirst().value();
    }

    protected static void assertNoSetCookie(MvcTestResult result) {
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    protected static void assertProblem(MvcTestResult result, int status, String title, String detail) {
        assertThat(result).hasStatus(status).hasContentType("application/problem+json");
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo(title);
        if (detail != null) {
            assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo(detail);
        }
    }

    /** RG-03 : 401 « Invalid credentials. », sans Set-Cookie. */
    protected static void assertInvalidCredentials(MvcTestResult result) {
        assertProblem(result, 401, "Unauthorized", "Invalid credentials.");
        assertNoSetCookie(result);
    }

    /** RG-11 : 401 « Invalid or expired session. » et cookie effacé. */
    protected static void assertInvalidSession(MvcTestResult result) {
        assertProblem(result, 401, "Unauthorized", "Invalid or expired session.");
        assertRefreshCookieCleared(result);
    }

    protected static void assertRefreshCookieCleared(MvcTestResult result) {
        List<SetCookie> cookies = refreshCookies(result);
        assertThat(cookies).hasSize(1);
        SetCookie cleared = cookies.getFirst();
        assertThat(cleared.value()).isEmpty();
        assertThat(cleared.attr("Max-Age")).isEqualTo("0");
        assertThat(cleared.attr("Path")).isEqualTo("/api/v1/auth");
        assertThat(cleared.has("HttpOnly")).isTrue();
        assertThat(cleared.has("Secure")).isTrue();
        assertThat(cleared.attr("SameSite")).isEqualToIgnoringCase("Strict");
    }

    protected static List<String> eventLines(CapturedOutput output, String event) {
        return output.getAll().lines().filter(line -> line.contains("event=" + event + " ")).toList();
    }

    protected static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** En-tête {@code Set-Cookie} décomposé ; noms d'attributs insensibles à la casse. */
    public record SetCookie(String name, String value, Map<String, String> attributes) {

        public boolean has(String attribute) {
            return attributes.containsKey(attribute.toLowerCase(Locale.ROOT));
        }

        public String attr(String attribute) {
            return attributes.get(attribute.toLowerCase(Locale.ROOT));
        }

        static SetCookie parse(String header) {
            String[] parts = header.split(";");
            String first = parts[0].trim();
            int eq = first.indexOf('=');
            Map<String, String> attributes = new LinkedHashMap<>();
            for (int i = 1; i < parts.length; i++) {
                String part = parts[i].trim();
                if (part.isEmpty()) {
                    continue;
                }
                int sep = part.indexOf('=');
                String key = (sep < 0 ? part : part.substring(0, sep)).trim().toLowerCase(Locale.ROOT);
                String value = sep < 0 ? "" : part.substring(sep + 1).trim();
                attributes.put(key, value);
            }
            return new SetCookie(first.substring(0, eq), first.substring(eq + 1), attributes);
        }
    }
}
