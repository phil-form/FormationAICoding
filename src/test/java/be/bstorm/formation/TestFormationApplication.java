package be.bstorm.formation;

import org.springframework.boot.SpringApplication;

/**
 * Lance l'application en local avec une base Testcontainers (sans docker compose), profil dev, SANS OpenBao :
 * identifiants DB statiques du conteneur et {@code CryptoService} en mémoire (voir {@link TestcontainersConfig}).
 */
public class TestFormationApplication {

    public static void main(String[] args) {
        SpringApplication.from(FormationApplication::main)
                .with(TestcontainersConfig.class)
                .withAdditionalProfiles("dev")
                .run(withoutOpenBao(args));
    }

    private static String[] withoutOpenBao(String[] args) {
        String[] all = java.util.Arrays.copyOf(args, args.length + 1);
        all[args.length] = "--app.openbao.enabled=false";
        return all;
    }
}
