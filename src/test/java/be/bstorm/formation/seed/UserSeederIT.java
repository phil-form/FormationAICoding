package be.bstorm.formation.seed;

import be.bstorm.formation.framework.security.Role;
import be.bstorm.formation.support.SpiedAuthItSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Feature 001 — {@link UserSeeder} instancié par son constructeur public (RG-18) : AC-43, AC-44. */
@Tag("feature-001")
class UserSeederIT extends SpiedAuthItSupport {

    private static final String DEMO_PASSWORD = "Demo-Password-2026";

    @Test
    @DisplayName("[AC-43] Seed des comptes admin, gestionnaire, conducteur ; login admin possible ; exécution unique")
    void ac43_seedsDemoAccountsOnce() throws Exception {
        // Given
        UserSeeder seeder = new UserSeeder(users, passwordHasher, DEMO_PASSWORD);
        assertThat(seeder.shouldRun()).isTrue();

        // When
        seeder.seed();

        // Then
        assertThat(rolesOf("admin")).containsExactly("ADMIN");
        assertThat(rolesOf("gestionnaire")).containsExactlyInAnyOrder("DRIVER", "FLEET_MANAGER");
        assertThat(rolesOf("conducteur")).containsExactly("DRIVER");
        for (String username : List.of("admin", "gestionnaire", "conducteur")) {
            assertThat(jdbc.queryForObject("SELECT enabled FROM app_user WHERE username = ?", Boolean.class, username))
                    .isTrue();
            assertThat(jdbc.queryForObject("SELECT depot_id IS NULL FROM app_user WHERE username = ?", Boolean.class,
                    username)).isTrue();
        }
        assertThat(login("admin", DEMO_PASSWORD)).hasStatusOk();
        assertThat(seeder.shouldRun()).isFalse();
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "   ", "elevenchars"})
    @DisplayName("[AC-44] Mot de passe de démonstration vide, blanc ou < 12 caractères : IllegalStateException, aucun compte")
    void ac44_rejectsMissingOrShortDemoPassword(String demoPassword) {
        // Given
        UserSeeder seeder = new UserSeeder(users, passwordHasher, demoPassword);

        // When / Then
        var thrown = assertThatThrownBy(seeder::seed).isInstanceOf(IllegalStateException.class);
        if (!demoPassword.isEmpty()) {
            thrown.hasMessageNotContaining(demoPassword);
        }
        assertThat(userCount()).isZero();
    }

    @Test
    @DisplayName("[AC-44] Mot de passe de 12 caractères (borne) : seed sans exception, trois comptes")
    void ac44_acceptsTwelveCharacterDemoPassword() {
        // Given
        UserSeeder seeder = new UserSeeder(users, passwordHasher, "twelve-chars");

        // When / Then
        assertThatCode(seeder::seed).doesNotThrowAnyException();
        assertThat(userCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("[AC-44] admin existant : shouldRun() = false sans contrôler le mot de passe")
    void ac44_shouldRunIsFalseWhenAdminExistsEvenWithEmptyPassword() {
        // Given
        createUser("admin", PASSWORD, Set.of(Role.ADMIN), null);
        UserSeeder seeder = new UserSeeder(users, passwordHasher, "");

        // When / Then
        assertThatCode(() -> assertThat(seeder.shouldRun()).isFalse()).doesNotThrowAnyException();
    }

    private List<String> rolesOf(String username) {
        return jdbc.queryForList("SELECT r.role FROM app_user_role r JOIN app_user u ON u.id = r.user_id "
                + "WHERE u.username = ?", String.class, username);
    }

    private int userCount() {
        return jdbc.queryForObject("SELECT count(*) FROM app_user", Integer.class);
    }
}
