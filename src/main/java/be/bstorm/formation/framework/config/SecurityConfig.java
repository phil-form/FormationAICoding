package be.bstorm.formation.framework.config;

import be.bstorm.formation.framework.security.AuthCsrfFilter;
import be.bstorm.formation.framework.security.HttpsEnforcementFilter;
import be.bstorm.formation.framework.security.ProblemDetailSecurityHandler;
import be.bstorm.formation.framework.security.SecurityProperties;
import be.bstorm.formation.framework.security.jwt.AuthAwareBearerTokenResolver;
import be.bstorm.formation.framework.security.jwt.CurrentUserJwtConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy;
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter.HeaderValue;
import org.springframework.security.web.session.DisableEncodeUrlFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Socle de sécurité HTTP de l'API.
 * <p>
 * Refus par défaut : seuls les endpoints explicitement listés sont publics, tout le reste exige une
 * authentification. Authentification par jeton d'accès JWT ES256 ({@code Authorization: Bearer}, feature 001,
 * ADR 0002) vérifié localement (resource server OAuth2 / Nimbus) ; les routes {@code POST /api/v1/auth/login},
 * {@code /refresh} et {@code /logout} sont publiques et ignorent l'en-tête {@code Authorization} (RG-20).
 * <p>
 * API sans état : pas de session, pas de formulaire ni de Basic. Le {@code CsrfFilter} de Spring reste désactivé :
 * le seul cookie (jeton de rafraîchissement, {@code SameSite=Strict}, {@code Path=/api/v1/auth}) est protégé par
 * {@link AuthCsrfFilter} (ADR 0002 D3 : {@code Origin} autorisée + en-tête {@code X-Requested-With}), placé avant le
 * filtre CORS.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String PERMISSIONS_POLICY =
            "accelerometer=(), camera=(), geolocation=(), gyroscope=(), magnetometer=(), microphone=(), "
                    + "payment=(), usb=(), browsing-topics=()";

    private final SecurityProperties properties;
    private final ProblemDetailSecurityHandler problemDetailHandler;

    @Value("${springdoc.api-docs.enabled:false}")
    private boolean apiDocsEnabled;

    @Value("${management.server.port:#{null}}")
    private Integer managementPort;

    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
                                               CorsConfigurationSource corsConfigurationSource,
                                               @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver,
                                               JwtDecoder jwtDecoder) throws Exception {
        properties.assertStrict();

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .anonymous(Customizer.withDefaults())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(problemDetailHandler)
                        .accessDeniedHandler(problemDetailHandler))
                .oauth2ResourceServer(rs -> rs
                        .bearerTokenResolver(new AuthAwareBearerTokenResolver())
                        .authenticationEntryPoint(problemDetailHandler)
                        .accessDeniedHandler(problemDetailHandler)
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(new CurrentUserJwtConverter())))
                .addFilterBefore(new HttpsEnforcementFilter(properties, managementPort), DisableEncodeUrlFilter.class)
                .addFilterBefore(new AuthCsrfFilter(corsConfigurationSource, resolver), CorsFilter.class)
                .headers(this::securityHeaders)
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(EndpointRequest.to("health", "info")).permitAll();
                    if (apiDocsEnabled) {
                        auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                    }
                    auth.requestMatchers("/error").permitAll();
                    auth.requestMatchers(HttpMethod.POST,
                            "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll();
                    auth.anyRequest().authenticated();
                });
        return http.build();
    }

    private void securityHeaders(HeadersConfigurer<HttpSecurity> h) {
        h.contentTypeOptions(Customizer.withDefaults())
                .frameOptions(f -> f.deny())
                .xssProtection(x -> x.headerValue(HeaderValue.DISABLED))
                .cacheControl(Customizer.withDefaults())
                .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER))
                .contentSecurityPolicy(csp -> csp.policyDirectives(properties.contentSecurityPolicy()))
                .permissionsPolicyHeader(p -> p.policy(PERMISSIONS_POLICY))
                .crossOriginOpenerPolicy(c -> c.policy(CrossOriginOpenerPolicy.SAME_ORIGIN))
                .crossOriginResourcePolicy(c -> c.policy(CrossOriginResourcePolicy.SAME_ORIGIN));
        SecurityProperties.Hsts hsts = properties.hsts();
        if (hsts.enabled()) {
            h.httpStrictTransportSecurity(s -> s
                    .maxAgeInSeconds(hsts.maxAge().toSeconds())
                    .includeSubDomains(hsts.includeSubDomains())
                    .preload(hsts.preload()));
        } else {
            h.httpStrictTransportSecurity(HeadersConfigurer.HstsConfig::disable);
        }
    }

    /**
     * Aucun utilisateur Spring Security « UserDetails » : l'authentification passe uniquement par le jeton d'accès
     * (feature 001). Empêche Spring Boot de générer un utilisateur par défaut avec un mot de passe affiché dans les logs.
     */
    @Bean
    UserDetailsService noUsers() {
        return new InMemoryUserDetailsManager();
    }
}
