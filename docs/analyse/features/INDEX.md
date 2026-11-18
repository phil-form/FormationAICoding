# Index des features — Delvaux & Fils

Source : [`docs/analyse/client/delvaux-gestion-de-flotte.md`](../client/delvaux-gestion-de-flotte.md) ·
plan : [`tmp/plan/delvaux.md`](../../../tmp/plan/delvaux.md) (partie 0.3).

Numéros **réservés** : `/analyse-feature` utilise le numéro et le slug de cette table (fichier
`NNN-<slug>.md`) et met à jour la colonne « Statut ». Une feature nouvelle prend le prochain numéro libre (019…).

Statuts : `à faire` → `analysée` (brouillon) → `validée` (`/review-analyse` OK) → `terminée` (définition de
« terminé » du plan).

| N° | Slug | Feature | Partie | Règles | Réponses client | Dépend de | Données sensibles | Statut |
|---|---|---|---|---|---|---|---|---|
| 001 | `authentification` | Authentification (login, refresh, logout) | 2.1 | RM9 | C4 | socle 1 | aucune S2 ; SECRET : mot de passe (Argon2id + poivre), refresh token (SHA-256), JWT | validée |
| 002 | `comptes-utilisateurs` | Comptes utilisateurs (admin) | 2.2 | RM9, RM10 | C4, C11 | 001 | **NISS** (chiffré) | à faire |
| 003 | `depots` | Dépôts | 3.1 | RM10 | — | 001 | — | à faire |
| 004 | `types-vehicule` | Types de véhicule | 3.2 | RM10 | C3 | 001 | — | à faire |
| 005 | `types-entretien` | Types d'entretien | 3.3 | RM10 | — | 001 | — | à faire |
| 006 | `vehicules` | Véhicules | 4.1 | RM3, RM9, RM10 | C8 | 003, 004 | — | à faire |
| 007 | `documents-vehicule` | Documents de véhicule | 4.2 | RM7, RM10 | — | 006 | — | à faire |
| 008 | `reservation-demande` | Réservation : demande et annulation | 5.1 | RM1, RM2, RM3, RM7, RM8, RM9 | C2, C3, C5, C12 | 006, 007 | — | à faire |
| 009 | `reservation-validation` | Réservation : validation / refus | 5.2 | RM1, RM8, RM9 | C1, C1b | 008 | — | à faire |
| 010 | `reservation-depart-retour` | Réservation : départ et retour | 5.3 | RM4, RM8, RM9 | C6 | 009 | — | à faire |
| 011 | `planning` | Planning hebdomadaire | 5.4 | RM9 | C2, C12 | 008 | — | à faire |
| 012 | `entretiens-echeances` | Entretiens : échéances | 6.1 | RM5 | — | 005, 006 | — | à faire |
| 013 | `entretiens-realisation` | Entretiens : planification et réalisation | 6.2 | RM4, RM5 | C7, C8 | 012 | — | à faire |
| 014 | `pleins` | Pleins | 7.1 | RM4, RM9 | — | 006 | — | à faire |
| 015 | `sinistres` | Sinistres : déclaration et suivi | 8.1 | RM6, RM9 | C8 | 006 | à classer (`/sensitive-data`) | à faire |
| 016 | `photos-sinistre` | Photos de sinistre | 8.2 | RM9 | C10 | 015, socle 1.3 | à classer (`/sensitive-data`) | à faire |
| 017 | `pilotage-couts` | Pilotage : coûts | 9.1 | — | — | 010, 013, 014, 015 | — | à faire |
| 018 | `pilotage-utilisation` | Pilotage : utilisation et consommation | 9.2 | — | C9 | 010, 014 | — | à faire |

La colonne « Données sensibles » est une pré-classification ; la classification qui fait foi est celle produite par
`/sensitive-data` dans chaque analyse (§ 7.5).
