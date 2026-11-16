package be.bstorm.formation.framework.security;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Test exécuté avec un {@link CurrentUser} authentifié (principal réel de l'application, pas un {@code User} de
 * {@code @WithMockUser}) : {@code @WithCurrentUser(roles = Role.FLEET_MANAGER, depot = "…")}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithCurrentUserSecurityContextFactory.class)
public @interface WithCurrentUser {

    /** Identifiant de l'utilisateur (UUID). */
    String id() default "0193a5f2-0000-7000-8000-000000000001";

    Role[] roles() default Role.DRIVER;

    /** Dépôt de rattachement (UUID) ; vide = aucun. */
    String depot() default "0193a5f2-0000-7000-8000-00000000000a";
}
