package be.bstorm.formation.framework.security;

import be.bstorm.formation.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/** Socle de sécurité avec la configuration par défaut (non stricte, HTTP) : refus par défaut, en-têtes, CORS. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class SecurityBaselineIT {

    @Autowired
    MockMvcTester mvc;

    @Test
    void healthIsPublic() {
        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();
    }

    @Test
    void apiIsDeniedByDefaultWithProblemDetail() {
        MvcTestResult result = mvc.get().uri("/api/v1/anything").exchange();

        assertThat(result).hasStatus(401)
                .hasContentType("application/problem+json")
                .bodyJson().extractingPath("$.title").isEqualTo("Unauthorized");
        assertThat(result).doesNotContainHeader(HttpHeaders.WWW_AUTHENTICATE);
    }

    @Test
    void unexposedActuatorEndpointsAreNotPublic() {
        assertThat(mvc.get().uri("/actuator/env")).hasStatus(401);
    }

    @Test
    void swaggerIsDisabledByDefault() {
        assertThat(mvc.get().uri("/v3/api-docs")).hasStatus(401);
    }

    @Test
    void securityHeadersArePresent() {
        MvcTestResult result = mvc.get().uri("/actuator/health").exchange();

        assertThat(result).headers()
                .hasValue("X-Content-Type-Options", "nosniff")
                .hasValue("X-Frame-Options", "DENY")
                .hasValue("X-XSS-Protection", "0")
                .hasValue("Referrer-Policy", "no-referrer")
                .hasValue("Cross-Origin-Opener-Policy", "same-origin")
                .hasValue("Cross-Origin-Resource-Policy", "same-origin")
                .containsHeader("Permissions-Policy");
        assertThat(result.getResponse().getHeader("Content-Security-Policy"))
                .contains("default-src 'none'").contains("frame-ancestors 'none'");
        assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");
    }

    @Test
    void noHstsOverPlainHttpAndNoSessionCookie() {
        MvcTestResult result = mvc.get().uri("/api/v1/anything").exchange();

        assertThat(result).doesNotContainHeader("Strict-Transport-Security")
                .doesNotContainHeader(HttpHeaders.SET_COOKIE);
    }

    @Test
    void corsPreflightFromAllowedOriginIsAccepted() {
        MvcTestResult result = mvc.options().uri("/api/v1/anything")
                .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type")
                .exchange();

        assertThat(result).hasStatusOk().headers()
                .hasValue(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200");
        assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)).contains("POST");
    }

    @Test
    void corsPreflightFromUnknownOriginIsRejected() {
        MvcTestResult result = mvc.options().uri("/api/v1/anything")
                .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
                .exchange();

        assertThat(result).hasStatus(403).doesNotContainHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    @Test
    void corsRejectsHeaderNotInAllowList() {
        MvcTestResult result = mvc.options().uri("/api/v1/anything")
                .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Custom-Admin")
                .exchange();

        assertThat(result).hasStatus(403);
    }
}
