package be.bstorm.formation.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Feature 001 : {@code Clock} {@code @Primary} réglable, utilisé par tous les composants d'authentification. */
@TestConfiguration(proxyBeanMethods = false)
public class AuthTestConfig {

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(AuthItSupport.T0);
    }
}
