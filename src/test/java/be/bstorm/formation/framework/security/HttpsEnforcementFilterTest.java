package be.bstorm.formation.framework.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HttpsEnforcementFilterTest {

    private static final int MANAGEMENT_PORT = 8081;

    @Test
    void plainGetIsPermanentlyRedirectedToHttpsKeepingPathAndQuery() throws Exception {
        MockHttpServletResponse response = run(filter(true, 8443, null), request("GET", "/api/v1/items", "page=2&size=5"));

        assertThat(response.getStatus()).isEqualTo(301);
        assertThat(response.getHeader("Location")).isEqualTo("https://api.local:8443/api/v1/items?page=2&size=5");
    }

    @Test
    void defaultHttpsPortIsOmittedAndConfiguredHostWins() throws Exception {
        MockHttpServletResponse response = run(filter(true, 443, "api.example.be"), request("HEAD", "/x", null));

        assertThat(response.getStatus()).isEqualTo(301);
        assertThat(response.getHeader("Location")).isEqualTo("https://api.example.be/x");
    }

    @Test
    void plainNonIdempotentRequestIsRejectedNotRedirected() throws Exception {
        MockHttpServletResponse response = run(filter(true, 443, null), request("POST", "/api/v1/items", null));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader("Location")).isNull();
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getContentAsString()).contains("HTTPS Required");
    }

    @Test
    void secureRequestPassesThrough() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/v1/items", null);
        request.setSecure(true);
        MockFilterChain chain = new MockFilterChain();

        filter(true, 443, null).doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void managementPortIsNotRedirected() throws Exception {
        MockHttpServletRequest request = request("GET", "/actuator/health", null);
        request.setLocalPort(MANAGEMENT_PORT);
        MockFilterChain chain = new MockFilterChain();

        filter(true, 443, null).doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void disabledFilterLetsPlainHttpThrough() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/items", null);
        MockFilterChain chain = new MockFilterChain();

        filter(false, 443, null).doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    private static MockHttpServletResponse run(HttpsEnforcementFilter filter, MockHttpServletRequest request)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return response;
    }

    private static MockHttpServletRequest request(String method, String uri, String query) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setServerName("api.local");
        request.setServerPort(8080);
        request.setLocalPort(8080);
        request.setQueryString(query);
        return request;
    }

    private static HttpsEnforcementFilter filter(boolean requireHttps, int httpsPort, String host) {
        SecurityProperties props = new SecurityProperties(true, requireHttps, httpsPort, host,
                new SecurityProperties.Hsts(true, Duration.ofDays(730), true, true), "default-src 'none'",
                new SecurityProperties.Cors(List.of("https://app.example.be"), List.of(), List.of("GET"),
                        List.of("Authorization"), List.of(), false, Duration.ofHours(1)));
        return new HttpsEnforcementFilter(props, MANAGEMENT_PORT);
    }
}
