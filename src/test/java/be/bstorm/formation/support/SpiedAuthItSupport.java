package be.bstorm.formation.support;

import be.bstorm.formation.framework.crypto.PasswordPepper;
import be.bstorm.formation.framework.security.LoginRateLimiter;
import be.bstorm.formation.framework.security.jwt.JwtKeyProvider;
import be.bstorm.formation.framework.security.password.PasswordHasher;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Contexte partagé par la plupart des tests d'intégration de 001 : les composants observés par les AC
 * ({@code PasswordHasher}, {@code LoginRateLimiter}, {@code JwtKeyProvider}, {@code PasswordPepper}) sont des espions
 * qui délèguent aux vraies implémentations (stubs remis à zéro après chaque test).
 */
@MockitoSpyBean(types = {PasswordHasher.class, LoginRateLimiter.class, JwtKeyProvider.class, PasswordPepper.class})
public abstract class SpiedAuthItSupport extends AuthItSupport {
}
