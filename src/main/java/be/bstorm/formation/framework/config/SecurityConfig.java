package be.bstorm.formation.framework.config;

import be.bstorm.formation.framework.security.HttpsEnforcementFilter;
import be.bstorm.formation.framework.security.ProblemDetailSecurityHandler;
import be.bstorm.formation.framework.security.SecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy;
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter.XFrameOptionsMode;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter.HeaderValue;
import org.springframework.security.web.session.DisableEncodeUrlFilter;

/**
 * Socle de sécurité HTTP de l'API.
 * <p>
 * Refus par défaut : seuls les endpoints explicitement listés sont publics, tout le reste exige une
 * authentification. Aucun mécanisme d'authentification n'est encore branché (JWT à venir) : les routes
 * {@code /api/**} répondent donc 401 tant qu'il ne l'est pas.
 * <p>
 * API sans état : pas de session, pas de cookie, pas de formulaire ni de Basic. CSRF est désactivé car aucune
 * authentification ne repose sur un cookie ; <b>à réactiver</b> si un refresh token est un jour stocké en cookie.
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
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
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
                .addFilterBefore(new HttpsEnforcementFilter(properties, managementPort), DisableEncodeUrlFilter.class)
                .headers(this::securityHeaders)
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(EndpointRequest.to("health", "info")).permitAll();
                    if (apiDocsEnabled) {
                        auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                    }
                    auth.requestMatchers("/error").permitAll();
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
     * Aucun utilisateur tant que l'authentification n'est pas implémentée : empêche Spring Boot de générer un
     * utilisateur par défaut avec un mot de passe affiché dans les logs.
     */
    @Bean
    UserDetailsService noUsers() {
        return new InMemoryUserDetailsManager();
    }
}
