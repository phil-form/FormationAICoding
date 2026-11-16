package be.bstorm.formation.framework.security;

import be.bstorm.formation.framework.exception.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static be.bstorm.formation.framework.security.CurrentUserTest.DEPOT_A;
import static be.bstorm.formation.framework.security.CurrentUserTest.DEPOT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Matrice RM9 : rôle × propriétaire × dépôt. */
class AccessPolicyTest {

    private static final UUID ME = UUID.fromString("0193a5f2-0000-7000-8000-000000000001");
    private static final UUID OTHER = UUID.fromString("0193a5f2-0000-7000-8000-000000000002");

    private final AccessPolicy policy = new AccessPolicy(new CurrentUserProvider());

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static CurrentUser user(String roles) {
        Set<Role> set = EnumSet.noneOf(Role.class);
        for (String role : roles.split("\\+")) {
            set.add(Role.valueOf(role));
        }
        return new CurrentUser(ME, set, DEPOT_A);
    }

    @ParameterizedTest(name = "{0} — ressource de {1} au dépôt {2} → {3}")
    @CsvSource({
            // rôles,                 propriétaire, dépôt, accès
            "DRIVER,                  ME,    A, true",
            "DRIVER,                  OTHER, A, false",
            "DRIVER,                  OTHER, B, false",
            "FLEET_MANAGER,           OTHER, A, true",
            "FLEET_MANAGER,           OTHER, B, false",
            "FLEET_MANAGER+DRIVER,    ME,    B, true",
            "FLEET_MANAGER+DRIVER,    OTHER, B, false",
            "ADMIN,                   OTHER, B, true",
    })
    void ownedResources(String roles, String owner, String depot, boolean expected) {
        assertThat(policy.canAccessOwned(user(roles), "ME".equals(owner) ? ME : OTHER,
                "A".equals(depot) ? DEPOT_A : DEPOT_B)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} — dépôt {1} → {2}")
    @CsvSource({
            "DRIVER,        A, false",
            "FLEET_MANAGER, A, true",
            "FLEET_MANAGER, B, false",
            "ADMIN,         B, true",
    })
    void managedResources(String roles, String depot, boolean expected) {
        assertThat(policy.canManage(user(roles), "A".equals(depot) ? DEPOT_A : DEPOT_B)).isEqualTo(expected);
    }

    @Test
    void otherPeoplesResourceIsNotFoundNotForbidden() {
        SecurityContextHolder.getContext().setAuthentication(new CurrentUserAuthentication(user("DRIVER")));

        assertThatCode(() -> policy.checkOwned(ME, DEPOT_B, "Reservation")).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.checkOwned(OTHER, DEPOT_A, "Reservation"))
                .isInstanceOfSatisfying(NotFoundException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(404);
                    assertThat(e.getMessage()).isEqualTo("Reservation not found.");
                });
        assertThatThrownBy(() -> policy.checkManaged(DEPOT_A, "Vehicle")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void withoutAuthenticatedUserItIsUnauthorized() {
        assertThatThrownBy(() -> policy.checkOwned(ME, DEPOT_A, "Reservation"))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }
}
