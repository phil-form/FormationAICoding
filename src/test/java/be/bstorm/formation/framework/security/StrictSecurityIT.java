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

/**
 * Comportement du mode strict (celui du profil prod), sans démarrer de serveur TLS : HTTPS forcé, HSTS preload,
 * CSP stricte, CORS limité à une origine HTTPS.
 */
@SpringBootTest(properties = {
        "app.security.strict=true",
        "app.security.require-https=true",
        "app.security.https-port=443",
        "app.security.hsts.enabled=true",
        "app.security.hsts.preload=true",
        "app.security.content-security-policy=default-src 'none'; frame-ancestors 'none'; upgrade-insecure-requests",
        "app.security.cors.allowed-origins=https://app.example.be",
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class StrictSecurityIT {

    @Autowired
    MockMvcTester mvc;

    @Test
    void plainHttpGetIsRedirectedToHttps() {
        MvcTestResult result = mvc.get().uri("/actuator/health?x=1").exchange();

        assertThat(result).hasStatus(301)
                .hasHeader(HttpHeaders.LOCATION, "https://localhost/actuator/health?x=1");
    }

    @Test
    void plainHttpPostIsRejected() {
        assertThat(mvc.post().uri("/api/v1/anything")).hasStatus(403);
    }

    @Test
    void httpsResponseCarriesHstsPreloadAndUpgradeCsp() {
        MvcTestResult result = mvc.get().uri("/actuator/health").secure(true).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getHeader("Strict-Transport-Security"))
                .contains("max-age=63072000").contains("includeSubDomains").contains("preload");
        assertThat(result.getResponse().getHeader("Content-Security-Policy")).contains("upgrade-insecure-requests");
    }

    @Test
    void httpsApiIsStillDeniedByDefault() {
        assertThat(mvc.get().uri("/api/v1/anything").secure(true)).hasStatus(401);
    }

    @Test
    void corsAcceptsOnlyTheConfiguredHttpsOrigin() {
        MvcTestResult allowed = mvc.options().uri("/api/v1/anything").secure(true)
                .header(HttpHeaders.ORIGIN, "https://app.example.be")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
                .exchange();
        MvcTestResult plainOrigin = mvc.options().uri("/api/v1/anything").secure(true)
                .header(HttpHeaders.ORIGIN, "http://app.example.be")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
                .exchange();

        assertThat(allowed).hasStatusOk()
                .hasHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.example.be");
        assertThat(allowed).doesNotContainHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS);
        assertThat(plainOrigin).hasStatus(403);
    }

    @Test
    void corsAllowsCredentialsOnlyOnAuthenticationRoutes() {
        MvcTestResult auth = mvc.options().uri("/api/v1/auth/refresh").secure(true)
                .header(HttpHeaders.ORIGIN, "https://app.example.be")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Requested-With")
                .exchange();
        MvcTestResult authFromPlainOrigin = mvc.options().uri("/api/v1/auth/refresh").secure(true)
                .header(HttpHeaders.ORIGIN, "http://app.example.be")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .exchange();

        assertThat(auth).hasStatusOk()
                .hasHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.example.be")
                .hasHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        assertThat(auth.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
                .containsIgnoringCase("X-Requested-With");
        assertThat(authFromPlainOrigin).hasStatus(403);
    }
}
