package be.bstorm.formation.framework.security;

/**
 * Rôles applicatifs (analyse client § 2.1, cumulables — réponse C4). Autorité Spring : {@code ROLE_<nom>}.
 * <ul>
 *   <li>{@link #DRIVER} — conducteur : ses réservations, pleins, sinistres ;</li>
 *   <li>{@link #FLEET_MANAGER} — gestionnaire : les véhicules (et leurs données) de <b>son</b> dépôt ;</li>
 *   <li>{@link #ADMIN} — tout, sur tous les dépôts.</li>
 * </ul>
 */
public enum Role {

    DRIVER,
    FLEET_MANAGER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
