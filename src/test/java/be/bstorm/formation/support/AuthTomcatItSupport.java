package be.bstorm.formation.support;

import be.bstorm.formation.TestcontainersConfig;
import be.bstorm.formation.entity.User;
import be.bstorm.formation.framework.security.LoginRateLimiter;
import be.bstorm.formation.framework.security.Role;
import be.bstorm.formation.framework.security.password.PasswordHasher;
import be.bstorm.formation.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Feature 001 — AC-66 / AC-67 : Tomcat réel ({@code RemoteIpValve}), client HTTP vers {@code http://127.0.0.1:<port>}
 * (MockMvc ne passe pas par la valve). Les sous-classes fixent {@code server.tomcat.remoteip.internal-proxies}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.forward-headers-strategy=native",
        "server.tomcat.remoteip.remote-ip-header=x-forwarded-for"})
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, AuthTestConfig.class})
@ExtendWith(OutputCaptureExtension.class)
public abstract class AuthTomcatItSupport {

    protected static final String PASSWORD = AuthItSupport.PASSWORD;
    protected static final String WRONG_PASSWORD = AuthItSupport.WRONG_PASSWORD;

    @Value("${local.server.port}")
    protected int port;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected UserRepository users;
    @Autowired
    protected PasswordHasher passwordHasher;
    @Autowired
    protected LoginRateLimiter rateLimiter;
    @Autowired
    protected MutableClock clock;

    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @BeforeEach
    void resetAuthenticationState() {
        jdbc.update("DELETE FROM refresh_token");
        jdbc.update("DELETE FROM refresh_token_family");
        jdbc.update("DELETE FROM app_user_role");
        jdbc.update("DELETE FROM app_user");
        rateLimiter.reset();
        clock.set(AuthItSupport.T0);
    }

    protected void createAlice() {
        users.save(User.builder()
                .username("alice")
                .passwordHash(passwordHasher.hash(PASSWORD))
                .roles(new HashSet<>(Set.of(Role.DRIVER)))
                .build());
    }

    /** POST /api/v1/auth/login avec l'en-tête {@code X-Forwarded-For} donné ; renvoie le statut HTTP. */
    protected int login(String username, String password, String forwardedFor) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + AuthItSupport.LOGIN))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", forwardedFor)
                .POST(HttpRequest.BodyPublishers.ofString(AuthItSupport.loginJson(username, password)))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    protected static List<String> loginFailureLines(CapturedOutput output) {
        return output.getAll().lines().filter(line -> line.contains("event=LOGIN_FAILURE ")).toList();
    }
}
