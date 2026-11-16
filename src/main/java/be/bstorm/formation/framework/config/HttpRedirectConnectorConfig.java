package be.bstorm.formation.framework.config;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Connecteur HTTP en clair, ouvert à côté du connecteur HTTPS principal, dont le seul rôle est de rediriger vers
 * HTTPS (voir {@code HttpsEnforcementFilter}). Actif uniquement si TLS est terminé dans l'application
 * ({@code server.ssl.enabled=true}) et que {@code app.server.http-redirect-port} est défini (profil prod).
 */
@Configuration
@ConditionalOnExpression("${server.ssl.enabled:false} and '${app.server.http-redirect-port:}' != ''")
public class HttpRedirectConnectorConfig {

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> httpRedirectConnector(
            @Value("${app.server.http-redirect-port}") int httpPort,
            @Value("${server.port}") int httpsPort) {
        return factory -> {
            Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            connector.setScheme("http");
            connector.setSecure(false);
            connector.setPort(httpPort);
            connector.setRedirectPort(httpsPort);
            factory.addAdditionalConnectors(connector);
        };
    }
}
