package be.bstorm.formation.framework.seed;

/**
 * Jeu de données de développement, écrit en Java avec les entités, repositories et services de l'application
 * (validation, audit, UUID v7 et chiffrement — ex. NISS via {@code CryptoService} — sont donc toujours respectés).
 * <p>
 * Déclarer l'implémentation avec {@link Seeder} : elle n'existe alors qu'avec le profil {@code seed}
 * ({@code ./start.sh --seed}, dev uniquement). Chaque seeder s'exécute dans sa propre transaction.
 */
public interface DataSeeder {

    /** Ordre d'exécution croissant : les données référencées avant celles qui les référencent. */
    default int order() {
        return 0;
    }

    /** Nom affiché dans les logs. */
    default String name() {
        return getClass().getSimpleName();
    }

    /**
     * Idempotence : {@code false} si les données sont déjà présentes (le seed est rejoué à chaque démarrage
     * avec {@code --seed}, y compris lors des redémarrages devtools).
     */
    boolean shouldRun();

    /** Insère les données. Une exception annule la transaction de ce seeder et fait échouer le démarrage. */
    void seed();
}
