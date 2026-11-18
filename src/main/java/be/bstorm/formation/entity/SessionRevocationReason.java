package be.bstorm.formation.entity;

/** Raison de la révocation d'une famille de jetons de rafraîchissement (feature 001, RG-10, RG-12, RG-15, RG-16). */
public enum SessionRevocationReason {
    LOGOUT,
    REUSE_DETECTED,
    USER_DISABLED,
    PASSWORD_CHANGED,
    ADMIN_REVOKED
}
