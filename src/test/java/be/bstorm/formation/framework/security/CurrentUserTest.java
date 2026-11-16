package be.bstorm.formation.framework.security;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserTest {

    static final UUID DEPOT_A = UUID.fromString("0193a5f2-0000-7000-8000-00000000000a");
    static final UUID DEPOT_B = UUID.fromString("0193a5f2-0000-7000-8000-00000000000b");

    @Test
    void requiresIdAndAtLeastOneRole() {
        assertThatThrownBy(() -> new CurrentUser(null, Set.of(Role.DRIVER), DEPOT_A)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CurrentUser(UUID.randomUUID(), Set.of(), DEPOT_A))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void managerManagesOnlyTheirDepot() {
        CurrentUser manager = new CurrentUser(UUID.randomUUID(), Set.of(Role.FLEET_MANAGER), DEPOT_A);

        assertThat(manager.managesDepot(DEPOT_A)).isTrue();
        assertThat(manager.managesDepot(DEPOT_B)).isFalse();
        assertThat(manager.managesDepot(null)).isFalse();
    }

    @Test
    void driverManagesNothingEvenInTheirDepot() {
        assertThat(new CurrentUser(UUID.randomUUID(), Set.of(Role.DRIVER), DEPOT_A).managesDepot(DEPOT_A)).isFalse();
    }

    @Test
    void managerWithoutDepotManagesNothing() {
        assertThat(new CurrentUser(UUID.randomUUID(), Set.of(Role.FLEET_MANAGER), null).managesDepot(DEPOT_A)).isFalse();
    }

    @Test
    void adminManagesEveryDepot() {
        CurrentUser admin = new CurrentUser(UUID.randomUUID(), Set.of(Role.ADMIN), null);

        assertThat(admin.managesDepot(DEPOT_A)).isTrue();
        assertThat(admin.managesDepot(DEPOT_B)).isTrue();
    }

    @Test
    void authenticationCarriesRoleAuthorities() {
        CurrentUser user = new CurrentUser(UUID.randomUUID(), Set.of(Role.DRIVER, Role.FLEET_MANAGER), DEPOT_A);
        CurrentUserAuthentication authentication = new CurrentUserAuthentication(user);

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isSameAs(user);
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.getAuthorities()).extracting(Object::toString)
                .containsExactlyInAnyOrder("ROLE_DRIVER", "ROLE_FLEET_MANAGER");
    }
}
