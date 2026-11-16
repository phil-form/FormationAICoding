# Plan de travail — Gestion de flotte « Delvaux & Fils »

Source : [`tmp/analyse-gestion-de-flotte.md`](../analyse-gestion-de-flotte.md) (analyse client).
Cadre technique : stack et conventions de [`CLAUDE.md`](../../CLAUDE.md), décisions [ADR 0001](../../docs/adr/0001-gestion-des-secrets.md)
(secrets, OpenBao) et [ADR 0002](../../docs/adr/0002-authentification.md) (authentification).

> Ce dépôt est l'**API REST** ; l'interface web (Angular) vit dans un dépôt séparé et n'est pas couverte ici, sauf
> pour les contrats d'API qu'elle consomme.

## Vue d'ensemble

| Partie | Contenu | Règles | Dépend de | Taille |
|---|---|---|---|---|
| 0 | Cadrage : questions client, découpage en features, analyse client dans `docs/analyse/client/` | — | — | S |
| 1 | Socle transverse : suppression logique, cloisonnement dépôt/propriétaire, stockage de fichiers, temps | RM9, RM10 | — | M |
| 2 | Authentification et comptes (phase 4 de `Next.md`) | RM9 | 1 | L |
| 3 | Référentiels : dépôts, types de véhicule, types d'entretien | RM10 | 2 | S |
| 4 | Parc : véhicules et documents | RM3, RM7, RM10 | 3 | M |
| 5 | Réservations : cycle de vie, chevauchements, départ / retour, planning | RM1, RM2, RM3, RM4, RM7, RM8, RM9 | 4 | L |
| 6 | Entretien : plan, échéances, réalisation | RM4, RM5 | 4 | M |
| 7 | Carburant : saisie des pleins | RM4, RM9 | 4 | S |
| 8 | Sinistres : déclaration, photos, suivi | RM6, RM9 | 4, 1 (fichiers) | M |
| 9 | Pilotage : coûts, utilisation, consommation | — | 5, 6, 7, 8 | M |
| 10 | Données de démonstration et livrables | — | 2 → 9 | S |
| 11 | Qualité et sécurité transverses (continu) | RM1 → RM10 | toutes | — |

Tailles indicatives : S ≈ ½–1 jour, M ≈ 1–3 jours, L ≈ 3–5 jours (workflow complet compris).

### Méthode pour chaque feature (parties 2 à 9)

Workflow de `CLAUDE.md`, une analyse préparatoire par sous-partie :

1. `/analyse-feature docs/analyse/client/delvaux-gestion-de-flotte.md "<feature>"` (+ `/sensitive-data` systématique, `/owasp-check` automatique si sécurité)
2. `/review-analyse` → verdict **OK** obligatoire
3. `/write-tests` (agent isolé, depuis l'analyse seule) — tests nommés `acxx_…`, `@Tag("feature-NNN")`
4. `/implement-feature` (agent isolé, sans accès aux tests) — migration Flyway, entités, DTO, mapper, service, controller, **seeder**
5. `/run-tests` → en cas d'échec `/triage-failures` → `--fix` côté test ou code
6. `/trace-check` (chaque `AC` testé, chaque `RM` implémentée) → `/mutation-test` (services)
7. `/commit` (contrôles de sécurité) — un commit par feature terminée

**Définition de « terminé »** pour une feature : analyse validée, tests verts, traçabilité complète (`AC` et `RM`),
score de mutation ≥ 70 % sur les services, `/commit` sans résultat bloquant, seeder de démonstration à jour, Swagger
à jour.

---

## Partie 0 — Cadrage · ✅ terminée le 16/11/2026

- Analyse client + réponses : `docs/analyse/client/delvaux-gestion-de-flotte.md` (§ 8).
- Index des features (numéros et slugs réservés) : `docs/analyse/features/INDEX.md`.
- Chaque analyse passe par `/sensitive-data` (classement des données ; toute donnée sensible chiffrée).

### 0.1 Reprise de l'analyse client
- Copier l'analyse dans `docs/analyse/client/delvaux-gestion-de-flotte.md` (entrée du workflow ; `tmp/` reste historique).
- Y ajouter une section « Réponses du client » alimentée par 0.2.

### 0.2 Questions à poser au client (bloquantes pour certaines analyses)

> **Répondues** le 16/11/2026 — les réponses font foi dans l'analyse client (§ 8) : hypothèses par défaut retenues pour C1–C10 et C12 ; **C1b** ajoutée (un valideur ne valide jamais sa propre réservation) ; **C10** sans HEIC côté serveur ; **C11** : NISS sur l'utilisateur, chiffré (feature 002).

| # | Question | Hypothèse par défaut | Bloque |
|---|---|---|---|
| C1 | Qui valide une réservation ? Le gestionnaire du dépôt du **véhicule**, ou un admin ? Validation automatique possible ? | Gestionnaire du dépôt du véhicule (ou admin) | 5.2 |
| C2 | Un conducteur peut-il réserver un véhicule d'un **autre dépôt** ? | Oui (le planning est par dépôt) | 5.1 |
| C3 | Hiérarchie des permis : un titulaire **CE** peut-il conduire un véhicule **C** ou **B** ? | Oui : CE ⊇ C ⊇ B | 5.1 (RM2) |
| C4 | Un utilisateur peut-il cumuler plusieurs rôles (ex. gestionnaire qui conduit) ? | Oui (« un ou plusieurs rôles ») | 2 |
| C5 | Durée maximale d'une réservation ? Délai minimal de réservation ? | Pas de maximum ; pas de délai | 5.1 |
| C6 | Une **anomalie** signalée au retour change-t-elle le statut du véhicule ? | Non : notification au gestionnaire, qui décide | 5.3 |
| C7 | Un entretien **planifié** met-il le véhicule « en entretien » dès sa date prévue, ou seulement quand le gestionnaire le démarre ? | Action explicite du gestionnaire | 6.2 |
| C8 | Que deviennent les réservations futures d'un véhicule mis **hors service** (RM6) ou en entretien ? | Restent ; le gestionnaire est alerté et les traite (annulation / réaffectation) | 4, 8 |
| C9 | Définition du **taux d'utilisation** : heures réservées (confirmées + terminées) / heures ouvrées ? / 24 h ? | Heures effectives (départ → retour) / heures de la période, jours ouvrés 8 h–18 h | 9 |
| C10 | Photos : formats, nombre et taille maximale par sinistre ; durée de conservation ? | JPEG/PNG/HEIC, 10 photos, 10 Mo chacune ; conservation = celle du dossier | 8 |
| C11 | Le **NISS** demandé précédemment (`Next.md`, phase 5) concerne-t-il ce projet ? L'analyse Delvaux ne le mentionne pas. | À confirmer — sinon la phase 5 NISS sort du périmètre Delvaux | 2 |
| C12 | Fuseau horaire : uniquement Europe/Brussels ? | Oui (stockage UTC, affichage Europe/Brussels) | 1.4, 5 |

### 0.3 Découpage en features (numéros d'analyse)

| N° | Feature | Partie |
|---|---|---|
| 001 | Authentification (login, refresh, logout) | 2.1 |
| 002 | Comptes utilisateurs (admin) | 2.2 |
| 003 | Dépôts | 3.1 |
| 004 | Types de véhicule | 3.2 |
| 005 | Types d'entretien | 3.3 |
| 006 | Véhicules | 4.1 |
| 007 | Documents de véhicule | 4.2 |
| 008 | Réservation : demande et annulation | 5.1 |
| 009 | Réservation : validation / refus | 5.2 |
| 010 | Réservation : départ et retour | 5.3 |
| 011 | Planning hebdomadaire | 5.4 |
| 012 | Entretiens : échéances | 6.1 |
| 013 | Entretiens : planification et réalisation | 6.2 |
| 014 | Pleins | 7.1 |
| 015 | Sinistres : déclaration et suivi | 8.1 |
| 016 | Photos de sinistre | 8.2 |
| 017 | Pilotage : coûts | 9.1 |
| 018 | Pilotage : utilisation et consommation | 9.2 |

---

## Partie 1 — Socle transverse (infrastructure, hors workflow feature)

Implémenté une fois dans `framework/`, avec tests, avant les features qui l'utilisent.

### 1.1 Suppression logique (RM10)
- `BaseEntity` ou entité « supprimable » : colonne `deleted_at` (horodatage, qui) ; Hibernate 7 `@SoftDelete` ou filtre
  équivalent → les éléments supprimés n'apparaissent plus dans **aucune** requête JPA.
- Contraintes d'unicité **partielles** (`WHERE deleted_at IS NULL`) : une immatriculation supprimée peut être reprise.
- Endpoints `DELETE` → désactivation ; pas de suppression physique depuis l'API.
- Tests : un élément supprimé n'est plus listé, n'est plus accessible par id (404), reste en base.

### 1.2 Cloisonnement (RM9)
- Accès au **principal** courant (id, rôles, dépôt) depuis la sécurité (JWT, partie 2).
- Règles centralisées (`framework/security/AccessPolicy` ou `@PreAuthorize` avec beans dédiés) :
  - conducteur → uniquement **ses** réservations, pleins, sinistres ;
  - gestionnaire → uniquement les véhicules (et leurs données) **de son dépôt** ;
  - admin → tout.
- Ressource d'autrui → **404** (ne pas révéler l'existence, ADR / OWASP API1 BOLA).
- Filtrage dans les **requêtes** (repositories), pas seulement après chargement (listes, pagination, statistiques).
- Tests : matrice rôle × ressource × propriétaire (réutilisable par toutes les features).

### 1.3 Stockage de fichiers (photos de sinistre)
- Service `FileStorage` : volume dédié monté en dehors de l'application (le conteneur est en lecture seule),
  nom de fichier **UUID** non devinable, métadonnées en base (nom d'origine, type, taille, empreinte SHA-256).
- Contrôles : liste blanche de types vérifiés sur le **contenu** (signature binaire, pas l'extension), taille max,
  nombre max par sinistre, suppression des métadonnées EXIF (géolocalisation) à envisager.
- Téléchargement **uniquement** via un endpoint soumis au contrôle d'accès (RM9), `Content-Disposition: attachment`,
  `X-Content-Type-Options: nosniff`. Limites de taille alignées : Spring (`multipart`), WAF (`MODSEC_REQ_BODY_LIMIT`),
  HAProxy.
- Docker : volume `uploads` (dev, prod, test-prod), sauvegarde avec la base.

### 1.4 Temps et périodes
- Stockage `Instant` / `timestamptz` UTC ; conversion Europe/Brussels aux frontières (C12).
- Type « période » (début, fin) validé (fin > début, précision à l'heure) réutilisé par réservations et statistiques.

### 1.5 Extensions PostgreSQL
- `btree_gist` (contrainte d'exclusion RM1) : à créer par `docker/db/roles.sh` (superutilisateur), **pas** par une
  migration Flyway (le migrateur n'est pas propriétaire de la base — `CLAUDE.md`).

---

## Partie 2 — Authentification et comptes (phase 4 de `Next.md`)

Décisions : ADR 0002 (JWT ES256 via OpenBao 5 min, refresh `HttpOnly` 8 h glissant / 24 h, Argon2id + poivre).
**Infra préalable** (déjà listée dans `Next.md`) : clé `password-pepper`, Bouncy Castle, CORS avec credentials limité à
`/api/v1/auth/**`.

### 2.1 Authentification — feature 001
- `POST /api/v1/auth/login`, `/refresh`, `/logout` ; familles de refresh tokens, détection de réutilisation.
- Limitation des tentatives, réponses génériques, journal des événements de sécurité.
- Claims du JWT : `sub`, rôles, dépôt (pour 1.2).
- `/owasp-check` : ASVS V2/V3, cheat sheets JWT, Authentication, Session Management.

### 2.2 Comptes utilisateurs (ADMIN) — feature 002
- Entités **Utilisateur** (identifiant, e-mail, nom, prénom, catégorie de permis B / C / CE, dépôt, rôles, actif) et
  **Rôle**.
- CRUD admin, désactivation (RM10), changement de rôle / dépôt, réinitialisation de mot de passe.
- Changement de mot de passe par l'utilisateur ; révocation des sessions à chaque changement.
- Politique de mot de passe (longueur ≥ 12, liste de mots de passe compromis).
- `GET /api/v1/me` : profil courant (rôles, dépôt, permis) pour le front.
- Question C4 (cumul de rôles) et C11 (NISS).

---

## Partie 3 — Référentiels (ADMIN)

### 3.1 Dépôts — feature 003
- Nom, adresse, ville ; gestionnaire (un utilisateur ayant le rôle `GESTIONNAIRE`).
- Données de démo : Charleroi, Namur, Liège.

### 3.2 Types de véhicule — feature 004
- Nom, **permis requis** (B, C, CE), nombre de places. Base de RM2.

### 3.3 Types d'entretien — feature 005
- Nom, intervalle en **kilomètres**, intervalle en **mois** (au moins un des deux). Base de RM5.

Commun : unicité des noms parmi les éléments actifs, suppression logique interdite si l'élément est encore utilisé
(ex. type de véhicule avec des véhicules actifs) → 409.

---

## Partie 4 — Parc

### 4.1 Véhicules — feature 006
- Immatriculation (format belge, unique parmi les actifs), châssis (VIN, 17 caractères, unique), marque, modèle,
  type, dépôt, kilométrage, statut (`DISPONIBLE`, `EN_ENTRETIEN`, `HORS_SERVICE`), date d'achat.
- Gestionnaire : véhicules **de son dépôt** (RM9) ; admin : tous (transfert entre dépôts).
- Changement de statut manuel par le gestionnaire (C8 : alerte sur les réservations futures).
- **Réservabilité** calculée en un seul endroit (service) : statut `DISPONIBLE` (RM3) et aucun document expiré (RM7).
- Kilométrage : jamais diminué (RM4) — méthode unique `Vehicle.recordMileage(km)` utilisée par 5.3, 6.2 et 7.1.

### 4.2 Documents de véhicule — feature 007
- Type (assurance, contrôle technique, carte grise), numéro, date d'expiration (aucune pour la carte grise).
- Liste des documents expirant dans les **30 jours** (gestionnaire : son dépôt ; admin : tous).
- RM7 : un document expiré rend le véhicule non réservable — testé avec une horloge injectable (`Clock`).

---

## Partie 5 — Réservations (cœur métier)

### 5.1 Demande et annulation — feature 008
- Conducteur : demande (véhicule, début, fin à l'heure près, motif) → statut `DEMANDEE`.
- Contrôles à la demande : RM2 (permis, C3), RM3 et RM7 (réservabilité), fin > début, pas dans le passé.
- Annulation par le conducteur tant que la réservation n'est pas `EN_COURS` (`DEMANDEE` ou `CONFIRMEE`).
- RM9 : un conducteur ne voit que ses réservations.

### 5.2 Validation / refus — feature 009
- Gestionnaire du dépôt du véhicule (C1) ou admin : `DEMANDEE` → `CONFIRMEE` / `REFUSEE` (motif de refus) ; valideur
  enregistré.
- **RM1** à la confirmation : aucun chevauchement avec une réservation `CONFIRMEE` ou `EN_COURS` du même véhicule.
  Double garde : contrôle applicatif (message clair) **et** contrainte PostgreSQL
  `EXCLUDE USING gist (vehicle_id WITH =, tstzrange(start, end, '[)') WITH &&) WHERE (status IN ('CONFIRMEE','EN_COURS'))`
  (résiste aux validations **concurrentes** ; violation → 409). Test de concurrence dédié.
- Re-vérification de RM2/RM3/RM7 au moment de la validation (l'état a pu changer depuis la demande).

### 5.3 Départ et retour — feature 010
- Départ : `CONFIRMEE` → `EN_COURS`, kilométrage de départ (≥ kilométrage du véhicule, RM4).
- Retour : `EN_COURS` → `TERMINEE`, kilométrage d'arrivée (≥ départ et ≥ véhicule, RM4 → met à jour le véhicule),
  remarque / anomalie (C6 : notification au gestionnaire).
- **RM8** : machine à états unique (`ReservationStatus` + transitions autorisées) ; toute transition non prévue → 409.
  Tests exhaustifs : chaque paire (état, action).

### 5.4 Planning hebdomadaire — feature 011
- `GET /api/v1/depots/{id}/planning?week=2026-W47` : par véhicule du dépôt, créneaux occupés (`CONFIRMEE`,
  `EN_COURS`, éventuellement `DEMANDEE` distinguées) et véhicules non réservables (statut, document expiré).
- Lecture seule, accessible aux conducteurs ; ne révèle pas l'identité des autres conducteurs (minimisation).
- Performance : une requête par semaine et par dépôt (index sur véhicule + période).

---

## Partie 6 — Entretien

### 6.1 Échéances — feature 012
- **RM5** : pour chaque véhicule et type d'entretien, échéance atteinte quand
  `km_courant - km_dernier_entretien ≥ intervalle_km` **ou** `aujourd'hui ≥ date_dernier_entretien + intervalle_mois`
  (règle à préciser pour un véhicule sans historique : depuis la date d'achat et 0 km).
- Liste « à prévoir » (avec marge configurable) + documents expirant sous 30 jours (4.2) : tableau de bord du
  gestionnaire.
- Calcul à la demande (requête), horloge injectable ; job planifié de notification envisageable plus tard.

### 6.2 Planification et réalisation — feature 013
- Planifier (type, date prévue, garage) ; démarrer (véhicule → `EN_ENTRETIEN`, C7) ; clôturer (date de réalisation,
  kilométrage → RM4, coût) → véhicule `DISPONIBLE` sauf autre blocage.
- Gestionnaire de son dépôt / admin.

---

## Partie 7 — Carburant

### 7.1 Pleins — feature 014
- Conducteur : date, litres, montant, kilométrage ; véhicule qu'il a réservé (à confirmer) ou de son dépôt.
- RM4 : kilométrage inférieur au kilométrage courant → refusé ; supérieur → met à jour le véhicule.
- RM9 : conducteur → ses pleins ; gestionnaire → pleins des véhicules de son dépôt.
- Contrôles de cohérence : litres et montant > 0, prix au litre plausible (alerte, pas blocage).

---

## Partie 8 — Sinistres

### 8.1 Déclaration et suivi — feature 015
- Conducteur : date, description, gravité (`MINEUR`, `MOYEN`, `GRAVE`), réservation liée éventuelle.
- **RM6** : gravité `GRAVE` → véhicule `HORS_SERVICE` (même transaction) ; C8 pour les réservations futures.
- Gestionnaire : `DECLARE` → `EN_TRAITEMENT` → `CLOS`, coût ; transitions contrôlées (comme RM8).
- RM9 : conducteur → ses sinistres ; gestionnaire → son dépôt.

### 8.2 Photos — feature 016
- Téléversement / téléchargement / suppression logique via le service de la partie 1.3.
- `/owasp-check` (téléversement de fichiers : type réel, taille, nom, chemin, accès).

---

## Partie 9 — Pilotage (ADMIN ; gestionnaire limité à son dépôt)

### 9.1 Coûts — feature 017
- Coût total par véhicule et par dépôt sur une période : carburant (pleins) + entretiens (coût) + sinistres (coût).
- Montants en `BigDecimal` / `numeric(12,2)`, euros ; agrégats SQL (pas de chargement d'entités).

### 9.2 Utilisation et consommation — feature 018
- Taux d'utilisation (définition C9) par véhicule / dépôt.
- Consommation moyenne aux 100 km : litres / km parcourus sur la période (méthode « plein à plein » à préciser).
- Export CSV envisageable (hors analyse).

---

## Partie 10 — Données de démonstration et livrables

### 10.1 Seeders (`./start.sh --seed`, Datafaker déterministe)
- Un compte par rôle (`conducteur`, `gestionnaire`, `admin`) + conducteurs supplémentaires ; mots de passe de démo
  **uniquement en dev** (jamais en prod).
- 3 dépôts (Charleroi, Namur, Liège), types de véhicule (voiture B, camionnette B, poids lourd C/CE), une dizaine de
  véhicules, types d'entretien (vidange, pneus, contrôle technique…), documents (dont un expirant sous 30 jours, un
  expiré), réservations dans plusieurs états, pleins, un entretien à prévoir, un sinistre.
- Chaque feature ajoute son seeder (`/implement-feature`).

### 10.2 Livrables (§7 de l'analyse)
- `README` : installation, lancement, **comptes de démonstration**, parcours de démonstration orale.
- Branche principale toujours fonctionnelle : un commit par feature via `/commit`, tests verts.
- Collection d'exemples d'appels (Swagger dev / fichier `.http`) pour la démonstration.

---

## Partie 11 — Qualité et sécurité transverses (en continu)

### 11.1 Traçabilité des règles
| Règle | Feature(s) | Tests clés |
|---|---|---|
| RM1 | 009 | chevauchement refusé ; bornes jointives acceptées ; validations concurrentes (contrainte DB) |
| RM2 | 008, 009 | permis insuffisant refusé ; hiérarchie CE ⊇ C ⊇ B (C3) |
| RM3 | 006, 008, 009 | véhicule en entretien / hors service non réservable |
| RM4 | 006, 010, 013, 014 | km inférieur refusé ; km supérieur met à jour le véhicule ; égalité acceptée |
| RM5 | 012 | seuil km atteint ; seuil mois atteint ; aucun ; sans historique |
| RM6 | 015 | sinistre grave → hors service ; mineur/moyen → statut inchangé |
| RM7 | 007, 008, 009 | document expiré → non réservable ; expiration le jour même (horloge figée) |
| RM8 | 010 (+ 008, 009) | toutes les transitions interdites → 409 |
| RM9 | 1.2 + toutes | matrice rôle × propriétaire × dépôt ; ressource d'autrui → 404 |
| RM10 | 1.1 + toutes | supprimé : invisible, 404, présent en base |

`/trace-check` après chaque feature ; objectif final : **RM1 → RM10 toutes couvertes** (exigence §6 de l'analyse).

### 11.2 Sécurité
- `/owasp-check` sur 001, 002, 008, 009, 015, 016 (authentification, comptes, BOLA, fichiers).
- `/commit` à chaque feature ; `docker/test-prod/check.sh` avant chaque jalon (WAF : vérifier l'absence de faux
  positifs sur les payloads JSON et le téléversement multipart).
- Exceptions Trivy du 16/12/2026 à réexaminer (WAF, OpenBao).

### 11.3 Exploitation
- Sauvegarde des photos avec la base ; restauration testée (`restore.sh` à étendre au volume `uploads`).
- CI Jenkins (`/jenkins-pipeline`) une fois les premières features livrées.

---

## Jalons

| Jalon | Contenu | Démo possible |
|---|---|---|
| J1 | Parties 0, 1, 2 | connexion, gestion des comptes, rôles |
| J2 | Parties 3, 4 | référentiels, parc, documents qui expirent |
| J3 | Partie 5 | réservation complète : demande → validation → départ → retour, planning |
| J4 | Parties 6, 7, 8 | entretiens à prévoir, pleins, sinistres avec photos |
| J5 | Parties 9, 10 | pilotage, données de démonstration, README → **livraison** |

Ordre de bataille : J1 → J2 → J3, puis J4 (6, 7, 8 parallélisables), puis J5. Les questions client C1–C12 doivent
être tranchées **avant** l'analyse des features qu'elles bloquent (colonne « Bloque »).
