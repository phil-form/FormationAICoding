# Next — feuille de route

> Mémoire de travail du projet : ce qui est fait, ce qui vient, dans quel ordre, et comment savoir que c'est terminé.
> Décisions détaillées et justifications : [`docs/adr/0001-gestion-des-secrets.md`](docs/adr/0001-gestion-des-secrets.md).
> Contexte technique et conventions : [`CLAUDE.md`](CLAUDE.md).

## Demandes d'origine

1. ~~Mettre en place la structure du projet~~ → fait (voir « Déjà en place »).
2. Mettre en place l'authentification, tests + code : **JWT + refresh**, token court, application **haute sécurité,
   critique** (vérifier les OWASP Cheat Sheets) → phase 4.
3. L'utilisateur a un **NISS**, qui doit être **chiffré en base**, via un service dédié (**`CryptoService`**) → phases 3 et 5.
4. Proposer un stockage sécurisé des secrets (clé de token, mot de passe DB…) → **décidé : OpenBao** (ADR 0001),
   phases 1 et 2.

## Déjà en place (16/11/2026)

- Squelette Spring Boot 4.1.2 / Java 25, architecture par couche, socle `framework/` (sécurité, CORS, erreurs
  `ProblemDetail`, `BaseEntity` UUID v7, pagination).
- Sécurité HTTP : refus par défaut, API sans état, mode strict en prod (échec au démarrage si mal configuré),
  en-têtes (HSTS preload, CSP, COOP/CORP…), CORS strict en prod / permissif en dev.
- Prod : HAProxy en frontal (TLS 1.3, Let's Encrypt via certbot, limitation de débit, contrôle du Host) →
  TLS 1.3 re-chiffré → API ; certificat rechargé à chaud ; conteneur durci. Procédure : `docs/ops/DEPLOIEMENT-PROD.md`.
- Docker dev / prod / test, `start.sh`, `restore.sh` ; 32 tests verts.
- Workflow IA : 12 skills, 6 agents, hooks d'isolation tests/code et de protection des migrations Flyway.
- **Phase 1 (OpenBao) terminée** — voir ci-dessous.

## Décisions prises (résumé de l'ADR 0001)

| Sujet | Décision |
|---|---|
| Coffre à secrets | **OpenBao 2.7**, conteneur sur le serveur de l'API, Raft, réseau Docker interne + TLS, jamais publié |
| Descellement | **Shamir manuel** 5 parts / seuil 3, une part par item **1Password** distinct |
| Sauvegardes | Parts + jeton de récupération dans 1Password ; snapshots Raft quotidiens chiffrés hors site |
| Signature JWT | **ES256 via Transit `sign`** (clé privée jamais exportée), `kid` = version de clé |
| Base de données | **Identifiants dynamiques** (TTL 1 h renouvelé, max 7 j), rôle migrateur distinct pour Flyway |
| NISS | **Transit encrypt/decrypt** (AES-256-GCM96) + **index aveugle** Transit `hmac` (SHA-256) |
| Tokens | Accès **5 min** ; refresh à usage unique, rotation, révocation de famille si réutilisé ; **8 h** glissant, **24 h** max |
| Dev | OpenBao mode dev + service `openbao-init` alimenté par **`.env.dev`** (même configuration que la prod) |
| Client Java | **Spring Vault 4.1** (Spring Cloud Vault pas encore GA pour Boot 4.1 → à réévaluer) |

---

## Phase 1 — OpenBao (infrastructure) · ✅ terminée le 16/11/2026

Runbook : [`docs/ops/OPENBAO.md`](docs/ops/OPENBAO.md).

| # | Tâche | Livrables | État |
|---|---|---|---|
| 1.1 | Services `openbao-dev` (mode dev) et `openbao-prod` (Raft, TLS 1.3, jamais publié, conteneur durci, audit déclaratif) | `docker-compose.yml`, `docker/openbao/dev.hcl`, `docker/prod/openbao/openbao.hcl` | ✅ |
| 1.2 | PKI interne (CA 5 ans + certificat serveur 1 an) | `docker/prod/openbao/gen-internal-tls.sh` | ✅ |
| 1.3 | Configuration **unique** dev/prod, idempotente : KV v2, Transit (`jwt`, `niss`, `niss-index`), database (`app`, `migrator`), AppRole `api`, politiques | `docker/openbao/configure.sh`, `docker/openbao/policies/*.hcl` | ✅ |
| 1.4 | Dev : `db-roles` + `openbao-init` alimenté par `.env.dev` (créé automatiquement par `start.sh`) | `.env.dev.example`, services compose | ✅ |
| 1.5 | Prod : amorçage idempotent (init Shamir 5/3 affiché une fois, rotate-root, identifiants API, token de snapshot, révocation du root) | `docker/prod/openbao/bootstrap.sh` | ✅ |
| 1.6 | Rôles PostgreSQL du modèle de droits | `docker/db/roles.sh` | ✅ |
| 1.7 | `start.sh` : `.env.dev` auto en dev ; en prod détection non initialisé / scellé + descellement guidé | `start.sh`, `docker/prod/openbao/unseal.sh` | ✅ |
| 1.8 | Snapshots Raft chiffrés (age) + rétention ; restauration **testée** | `docker/prod/openbao/snapshot.sh` | ✅ |
| 1.9 | *(ajouté)* Accès admin break-glass sans token root permanent | `docker/prod/openbao/admin-token.sh` | ✅ |
| 1.10 | *(ajouté)* Recette automatisée | `docker/openbao/smoke-test.sh`, `docker/openbao/check-phase1.sh` | ✅ |

**Vérifié** (dev **et** prod) :
- AppRole `api` : 26 contrôles — signature JWT au format JWS (64 octets `r||s`), chiffrement / déchiffrement NISS
  non déterministe, index aveugle déterministe, identifiants DB ; **refus** de : export de clés, rotation,
  reconfiguration, vérification Transit (faite localement), lecture de la config DB, écriture KV, politiques,
  `sys/mounts`, création de `secret_id` ou de tokens d'autres politiques.
- PostgreSQL : 11 contrôles — `migrator` crée des tables possédées par `formation_owner` ; `app` fait du DML
  mais **aucun** `CREATE`/`DROP`/`ALTER`/`TRUNCATE`/`SET ROLE`/`CREATE ROLE` ; la révocation d'un bail coupe les
  sessions et supprime l'utilisateur.
- Prod : token root révoqué, mot de passe `openbao_admin` inconnu de tous, TLS interne 1.3 seul, aucun port publié,
  scellé après redémarrage, descellement par parts (part invalide / refusée gérée), API prod saine.
- Sauvegarde : snapshot chiffré age → déchiffré → **restauré** via break-glass ; une interruption en mode
  break-glass ramène toujours la configuration normale.

**Appris en route** (intégré à l'ADR : D11, D12) :
- OpenBao 2.x interdit l'activation d'un audit par l'API → audit déclaré dans la configuration.
- OpenBao ≥ 2.5 désactive les endpoints `generate-root` non authentifiés (CVE-2025-52894) → procédure break-glass.
- La CLI refuse une part Shamir passée par pipe → descellement via l'API (corps HTTPS, boucle locale, CA vérifiée).
- `docker compose exec -T` transmet le stdin → toujours le couper quand il n'est pas voulu (sinon une part en
  attente peut être « avalée »).

## Phase 2 — Intégration Spring (infrastructure)

**Objectif** : l'API ne lit plus aucun secret dans l'environnement ; tout vient d'OpenBao.

Points d'attention issus de la phase 1 : les objets existants (table `flyway_schema_history`) appartiennent déjà
à `formation_owner` (réassignés par `roles.sh`) ; le superutilisateur `DB_USER` reste nécessaire au conteneur `db`
et à `restore.sh` (à terme : `POSTGRES_PASSWORD_FILE`).

| # | Tâche |
|---|---|
| 2.1 | Dépendance `spring-vault-core` 4.1 ; **valider la compatibilité avec Spring Framework 7.1** (risque identifié) |
| 2.2 | Authentification AppRole : `role-id` et `secret-id` lus depuis `/run/secrets/openbao/` (dev : volume `openbao-dev-creds`, déjà monté ; prod : monter `OPENBAO_CREDS_DIR` + `ca.crt` de `OPENBAO_TLS_DIR`) ; `api-prod` dépend de `openbao-prod` sain |
| 2.3 | DataSource dynamique : `SecretLeaseContainer` sur `database/creds/app`, renouvellement du bail, nouvel identifiant avant `max_ttl` → mise à jour Hikari + `softEvictConnections()` sans coupure |
| 2.4 | Flyway avec `database/creds/migrator` (au démarrage uniquement) + `initSql: SET ROLE formation_owner` |
| 2.5 | Échec de démarrage explicite si OpenBao est injoignable ou scellé ; `HealthIndicator` OpenBao |
| 2.6 | Suppression de `DB_PASSWORD` côté API (compose, `.env`, `application.yml`) |
| 2.7 | Tests d'intégration : Testcontainers `testcontainers-vault` (image OpenBao) + PostgreSQL ; test de renouvellement de bail |

**Terminé quand** : l'API dev et prod démarrent sans `DB_PASSWORD`, survivent à l'expiration d'un bail DB, refusent de
démarrer si OpenBao est scellé (message clair), et tous les tests passent.

## Phase 3 — `CryptoService` (infrastructure)

**Objectif** : un service unique pour chiffrer, déchiffrer et indexer les données sensibles, sans que l'API ne
détienne jamais de clé.

| # | Tâche |
|---|---|
| 3.1 | Interface `CryptoService` (`encrypt`, `decrypt`, `blindIndex`) dans `framework/crypto/` + implémentation Transit ; chiffré stocké avec sa version (`vault:vN:…`) ; contexte (AAD) = type de donnée |
| 3.2 | Type `Niss` (value object) : normalisation (chiffres seuls), validation (11 chiffres, date de naissance plausible, clé de contrôle modulo 97, règle post-2000), jamais loggé (`toString` masqué) |
| 3.3 | Convertisseur JPA (`AttributeConverter`) ou service explicite — à décider en analyse de la phase 5 |
| 3.4 | Rotation : `transit/keys/niss/rotate` + job de `rewrap` des chiffrés + relèvement de `min_decryption_version` |
| 3.5 | Tests : unitaires (implémentation factice), intégration avec OpenBao réel, propriétés (chiffrer puis déchiffrer = identité ; même NISS → même index) |

**Terminé quand** : chiffrement, déchiffrement et index fonctionnent contre OpenBao, une rotation + rewrap est
testée, et aucune clé ne transite par l'API.

## Phase 4 — Authentification (feature, workflow complet)

`/analyse-feature` → `/owasp-check` → `/review-analyse` → `/write-tests` → `/implement-feature` → `/run-tests`
→ `/triage-failures` → `/trace-check` → `/mutation-test`

| Sujet | Contenu attendu dans l'analyse |
|---|---|
| Endpoints | `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout` |
| Token d'accès | JWT ES256, 5 min, `iss`/`aud`/`exp`/`iat`/`jti`/`sub`, `kid` = version Transit ; signature par Transit, vérification locale (clés publiques en cache, rafraîchies à la rotation) |
| Refresh token | Opaque 256 bits aléatoires, stocké **haché** (SHA-256), famille de tokens, usage unique, réutilisation → révocation de toute la famille, 8 h glissant / 24 h absolu |
| Mots de passe | Argon2id (ou bcrypt selon Q2), politique ASVS (longueur ≥ 12, pas de règles de composition, liste de mots de passe compromis) |
| Anti-abus | Limitation par compte et par IP, réponses génériques (pas d'énumération), journalisation des événements de sécurité |
| Révocation | Logout = révocation de la famille ; changement de mot de passe = révocation de toutes les familles |
| Références | OWASP ASVS V2 (authentification) / V3 (sessions), cheat sheets JWT, Authentication, Session Management, Password Storage |

**Terminé quand** : tous les critères d'acceptation (y compris `AC-SEC`) sont couverts et verts, la traçabilité
est complète et le score de mutation dépasse le seuil.

## Phase 5 — Utilisateur et NISS (feature, workflow complet)

- Entité utilisateur, NISS chiffré via `CryptoService` + colonne `niss_index` **unique** (index aveugle).
- Recherche par NISS = calcul de l'index puis recherche exacte ; jamais de `LIKE` sur un NISS.
- Le NISS n'apparaît ni dans les logs, ni dans les messages d'erreur, ni dans les réponses (masqué sauf besoin explicite et autorisé).
- Contrôle d'accès : un utilisateur ne voit que ses données (BOLA) ; consultation du NISS journalisée.
- RGPD : finalité, durée de conservation, droit à l'effacement (suppression du chiffré **et** de l'index).

## Phase 6 — Exploitation

| # | Tâche |
|---|---|
| 6.1 | Jenkins : génération d'un nouveau `secret_id` à chaque déploiement (politique `deployer`), réponse wrappée, ancien `secret_id` révoqué |
| 6.2 | Runbooks : base posée dans `docs/ops/OPENBAO.md` (descellement, break-glass, rotation, restauration testée, incidents) — à compléter avec la supervision et Jenkins |
| 6.3 | Supervision : alerte si OpenBao est scellé, si un bail DB n'est pas renouvelé, si un snapshot échoue, si l'audit OpenBao ne peut plus écrire |
| 6.4 | Exercice de restauration complet (snapshot + parts depuis 1Password) avant la mise en production |

## Questions ouvertes

| # | Question | Hypothèse par défaut | Bloque |
|---|---|---|---|
| Q1 | Transport du refresh token : cookie `HttpOnly; Secure; SameSite=Strict` (→ CSRF + `allow-credentials`) ou corps de réponse | Cookie `HttpOnly` limité à `/api/v1/auth` | Phase 4 |
| Q2 | Hachage des mots de passe : Argon2id (Bouncy Castle) ou bcrypt | Argon2id | Phase 4 |
| Q3 | Fréquence de rotation des clés | `jwt` 90 j, `niss` 1 an + rewrap | Phases 3–4 |
| Q4 | Détenteurs des 5 parts Shamir | À désigner | Mise en prod |
| Q5 | Destination hors site des snapshots Raft | À définir avec l'hébergeur | Mise en prod |

## Risques acceptés

- OpenBao mono-nœud : point unique de défaillance (plus de login, de NISS ni de nouvelle connexion DB s'il tombe).
- Descellement manuel : API indisponible après un redémarrage jusqu'à l'intervention de 3 détenteurs de parts.
- Spring Vault 4.1.1 compilé contre Spring Framework 7.0 : compatibilité à confirmer en 2.1.
