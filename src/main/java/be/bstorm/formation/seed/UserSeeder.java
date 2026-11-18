package be.bstorm.formation.seed;

import be.bstorm.formation.entity.User;
import be.bstorm.formation.framework.security.Role;
import be.bstorm.formation.framework.security.password.PasswordHasher;
import be.bstorm.formation.framework.seed.DataSeeder;
import be.bstorm.formation.framework.seed.Seeder;
import be.bstorm.formation.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Comptes de démonstration (RG-18, profil {@code seed}, dev uniquement) : {@code admin} (ADMIN), {@code gestionnaire}
 * (FLEET_MANAGER, DRIVER), {@code conducteur} (DRIVER), actifs, sans dépôt (feature 003).
 * <p>
 * Mot de passe commun lu dans {@code app.seed.demo-password} (variable {@code SEED_DEMO_PASSWORD}) — exception
 * documentée à ADR 0001 D9 : aucun autre composant ne lit cette propriété. Jamais journalisé.
 */
@Seeder
public class UserSeeder implements DataSeeder {

    static final int MIN_PASSWORD_LENGTH = 12;

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final String demoPassword;

    public UserSeeder(UserRepository users, PasswordHasher passwordHasher,
                      @Value("${app.seed.demo-password:}") String demoPassword) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.demoPassword = demoPassword;
    }

    @Override
    public int order() {
        return 10;
    }

    /** {@code admin} absent ; ne lit pas le mot de passe. */
    @Override
    public boolean shouldRun() {
        return !users.existsByUsername("admin");
    }

    @Override
    public void seed() {
        if (demoPassword == null || demoPassword.isBlank() || demoPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("app.seed.demo-password (SEED_DEMO_PASSWORD) must be set, non-blank and "
                    + "at least " + MIN_PASSWORD_LENGTH + " characters long");
        }
        List<User> demo = List.of(
                demoUser("admin", Set.of(Role.ADMIN)),
                demoUser("gestionnaire", Set.of(Role.FLEET_MANAGER, Role.DRIVER)),
                demoUser("conducteur", Set.of(Role.DRIVER)));
        demo.forEach(users::save);
    }

    private User demoUser(String username, Set<Role> roles) {
        return User.builder()
                .username(username)
                .passwordHash(passwordHasher.hash(demoPassword))
                .roles(new HashSet<>(roles))
                .build();
    }
}
