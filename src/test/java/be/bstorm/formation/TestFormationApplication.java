package be.bstorm.formation;

import org.springframework.boot.SpringApplication;

/** Lance l'application en local avec une base Testcontainers (sans docker compose), profil dev. */
public class TestFormationApplication {

    public static void main(String[] args) {
        SpringApplication.from(FormationApplication::main)
                .with(TestcontainersConfig.class)
                .withAdditionalProfiles("dev")
                .run(args);
    }
}
