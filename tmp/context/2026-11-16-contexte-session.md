# Contexte de session — 16/11/2026

Point de reprise pour une nouvelle session Claude Code (à lire après `CLAUDE.md`, qui reste la référence des
conventions). Dernier commit : `5e6c818`.

## Où en est le projet

API REST Spring Boot 4.1.2 / Java 25 / PostgreSQL 18 pour la gestion de flotte **Delvaux & Fils** (front Angular
séparé). Plan de travail : `tmp/plan/delvaux.md` (parties 0 → 11, jalons J1 → J5).

| Étape | État | Où |
|---|---|---|
| Socle technique (sécurité HTTP, Docker dev/prod/test-prod, WAF, HAProxy) | ✅ | `CLAUDE.md` |
| Secrets : OpenBao, Spring Vault, `CryptoService`, type `Niss` (phases 1–3 de `Next.md`) | ✅ | ADR 0001, `docs/ops/OPENBAO.md` |
| Décisions d'authentification (cookie refresh, Argon2id + pepper Transit) | ✅ décidé, **non implémenté** | ADR 0002 |
| Partie 0 Delvaux : réponses client C1–C12 (+ C1b), index des features 001–018 | ✅ `63d2361` | `docs/analyse/client/delvaux-gestion-de-flotte.md` § 8, `docs/analyse/features/INDEX.md` |
| Skill `/sensitive-data` (toute donnée `S2` chiffrée), branchée sur `/analyse-feature` et `/review-analyse` | ✅ `563ed30` | `.claude/skills/sensitive-data/` |
| Partie 1 Delvaux : socle transverse | ✅ `5e6c818` | `framework/`, plan partie 1 |
| **Partie 2 : authentification et comptes (features 001, 002)** | **à faire — prochaine étape** | plan partie 2, `Next.md` phases 4–5 |

Tests : **184 verts** (unitaires + intégration, `run-tests.sh --docker`). Recette OpenBao dev : 36/36 + 11/11 SQL.

## Réponses client à garder en tête (détail : analyse client § 8)

- C1/C1b : validation par le gestionnaire du dépôt **du véhicule** ou un admin ; jamais sa **propre** réservation.
- C2 : réservation inter-dépôts permise. C3 : permis CE ⊇ C ⊇ B. C4 : rôles cumulables.
- C5 : aucune limite de durée, pas de réservation dans le passé. C6 : anomalie au retour → notification, le
  gestionnaire décide. C7 : « en entretien » sur action explicite. C8 : réservations futures conservées + alerte.
- C9 : taux d'utilisation = heures effectives / heures ouvrées (lun–ven 8 h–18 h, Bruxelles).
- C10 : JPEG/PNG, 10 photos × 10 Mo, EXIF retiré. C11 : **NISS sur l'utilisateur, chiffré** (feature 002).
- C12 : Europe/Brussels, stockage UTC.

## Décisions de la partie 1 (utiles pour toutes les features)

- Suppression logique : `SoftDeletableEntity` + `@SQLRestriction(NOT_DELETED)` (vérifié au démarrage),
  `SoftDeleter.delete()`, index uniques **partiels**.
- Cloisonnement : `CurrentUser` / `Role` (`DRIVER`, `FLEET_MANAGER`, `ADMIN`) ; `AccessPolicy` (autrui → 404) ;
  `AccessSpecifications` (filtrage dans la requête) ; tests avec `@WithCurrentUser`.
- Fichiers : **tous chiffrés au repos** (enveloppe, clé Transit `file`), ré-encodage ImageIO, volume `uploads`.
- Temps : `Clock` injecté, `Period` sur heures pleines (`[start, end[`), `@ValidPeriod`, `btree_gist` (RM1).
- Id avant persistance : `entity.assignId()` (UUID v7) — obligatoire avant tout chiffrement lié à la ligne.

## Pièges connus

- Une association vers un élément **supprimé** n'est plus chargeable (`EntityNotFoundException`) : empêcher la
  suppression d'un élément utilisé ou recopier les données d'historique (à trancher par feature, ex. véhicule
  supprimé ↔ réservations passées).
- Hibernate fige l'état à insérer au `persist` : chiffrer **avant** `save`, avec l'id attribué par `assignId()`.
- Requêtes SQL natives : non filtrées par la suppression logique (`deleted_at IS NULL` à ajouter).
- Migrations : jamais d'instruction de niveau base (extensions → `docker/db/roles.sh`) ; hook `flyway-guard` actif.
- Changement de `docker/dev/Dockerfile` → relancer `./start.sh --build`.

## Prochaine étape : partie 2

1. Infrastructure d'authentification (hors workflow feature) : clé Transit `password-pepper`, encodeur Argon2id
   (Bouncy Castle — dépendance à signaler), `JwtEncoder` / `JwtDecoder` sur Transit (ES256), CORS avec credentials
   sur `/api/v1/auth/**`, filtre qui place un `CurrentUserAuthentication` dans le `SecurityContext`.
2. Features 001 `authentification` puis 002 `comptes-utilisateurs` (NISS chiffré) avec le workflow complet :
   `/analyse-feature` (→ `/sensitive-data`, `/owasp-check`) → `/review-analyse` → `/write-tests` →
   `/implement-feature` → `/run-tests` → `/trace-check` → `/mutation-test` → `/commit`.

## Reste ouvert / en veille

- Sauvegarde et restauration du volume `uploads` avec la base (plan 11.3).
- Test d'un téléversement réel à travers HAProxy → WAF (`docker/test-prod/check.sh`) quand la feature 016 existera.
- Exceptions Trivy (WAF, OpenBao) expirant le 16/12/2026 ; Lombok et `sun.misc.Unsafe` (bloquant pour Java 29).
- ADR 0001 : Q3 rotation des clés, Q4 détenteurs des parts Shamir, Q5 sauvegarde hors site.

## Règles de travail

- Français pour la doc et les échanges ; anglais pour le code, les identifiants et les commits.
- Commits uniquement via `/commit` (tests complets puis contrôles de sécurité), jamais de push.
- Les fichiers personnels `"Next.md "` (avec espace final) et `tmp/TP8-demarrer-un-projet-avec-claude.md` restent hors
  des commits.
