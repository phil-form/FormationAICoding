package be.bstorm.formation.framework.security;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.util.Set;
import java.util.UUID;

public class WithCurrentUserSecurityContextFactory implements WithSecurityContextFactory<WithCurrentUser> {

    @Override
    public SecurityContext createSecurityContext(WithCurrentUser annotation) {
        CurrentUser user = new CurrentUser(UUID.fromString(annotation.id()), Set.of(annotation.roles()),
                annotation.depot().isBlank() ? null : UUID.fromString(annotation.depot()));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new CurrentUserAuthentication(user));
        return context;
    }
}
