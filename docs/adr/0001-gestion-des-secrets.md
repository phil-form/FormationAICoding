# ADR 0001 — Gestion des secrets : OpenBao

| | |
|---|---|
| Statut | **Accepté** — 16/11/2026 |
| Décideurs | Philippe Stons (+ Claude Code) |
| Concerne | clé de signature JWT, identifiants PostgreSQL, clés NISS (chiffrement + index), secrets futurs |

## Contexte

Application **haute sécurité** : authentification JWT critique, NISS (numéro national, donnée personnelle sensible)
stocké chiffré. Les secrets ne doivent se trouver ni dans git, ni dans l'image, ni dans des variables
d'environnement de production (visibles via `docker inspect`, `/proc/*/environ`, actuator `env`, dumps).

## Décisions

| # | Sujet | Décision | Alternative écartée (raison) |
|---|---|---|---|
| D1 | Stockage | **OpenBao 2.7** (fork libre de Vault) | Fichiers / SOPS : la clé NISS serait en mémoire de l'API |
| D2 | Hébergement | Conteneur sur le serveur de l'API, stockage **Raft**, réseau Docker interne + **TLS**, jamais publié | Serveur dédié / cluster 3 nœuds : coût opérationnel non justifié tant que l'API est mono-instance |
| D3 | Descellement | **Shamir manuel** 5 parts / seuil 3, chaque part dans un item **1Password** distinct | Auto-unseal par clé locale (vol du disque = vol des secrets) ; KMS/HSM non disponible |
| D4 | Signature JWT | **ES256 via Transit `sign`** (`marshaling_algorithm=jws`) : la clé privée ne quitte jamais OpenBao ; `kid` = version de clé Transit ; vérification locale avec les clés publiques mises en cache | Clé lue depuis le KV (clé privée en mémoire) ; HS256 (symétrique) |
| D5 | Base de données | **Identifiants dynamiques** (moteur `database`) : utilisateur temporaire par instance, TTL 1 h renouvelé, max 7 j ; rôle **migrateur** distinct pour Flyway | Rôles statiques avec rotation |
| D6 | NISS | **Transit `encrypt`/`decrypt`** avec clé `niss` **dérivée** (AES-256-GCM96, `derived=true`) : chaque chiffré est lié à un **contexte** = type + identifiant immuable de la ligne (un chiffré copié vers une autre ligne est indéchiffrable) ; `rewrap` et `min_decryption_version` conservés. **Index aveugle** Transit `hmac` (clé `niss-index`, SHA-256). Clés non exportables | Chiffrement dans l'API avec une clé chargée ; `associated_data` (pas de `rewrap` possible → la valeur en clair transiterait par l'API à chaque rotation) |
| D7 | Tokens | Accès **5 min** ; refresh **à usage unique**, rotation, révocation de la famille si réutilisé ; session glissante **8 h** d'inactivité, **24 h** maximum | 15 min / 7 j (exposition plus longue) |
| D8 | Sauvegardes | Parts Shamir + jeton de récupération dans **1Password** ; **snapshots Raft** quotidiens chiffrés, stockés hors site | — |
| D9 | Dev | OpenBao en **mode dev** (mémoire) + service compose `openbao-init` qui lit **`.env.dev`** (gitignoré, modèle `.env.dev.example` versionné) et configure KV, Transit, moteur DB, AppRole **à l'identique de la prod** | Secrets dev en variables d'environnement directes (code différent entre dev et prod) |
| D11 | Accès admin | **Aucun token root permanent** (révoqué après amorçage). Accès admin *break-glass* : redémarrage temporaire avec `disable_unauthed_generate_root_endpoints = false` (désactivé par défaut depuis OpenBao 2.5, CVE-2025-52894), token généré avec 3 parts, retour immédiat à la config normale, token révoqué en fin d'opération (`admin-token.sh`) | Token root conservé dans 1Password (privilège permanent) ; méthode d'auth admin dédiée (surface d'attaque supplémentaire) |
| D12 | Audit | **Déclaratif** dans la configuration serveur (OpenBao 2.x refuse l'activation par l'API) ; OpenBao refuse toute requête s'il ne peut pas auditer | — |
| D13 | Token de l'API | AppRole avec **token périodique** (`token_period=1h`, renouvelé tant que l'API tourne) : les baux DB sont rattachés au token qui les crée, un `token_max_ttl` les révoquerait avant leur `max_ttl` | Token à durée maximale (24 h) : coupure des connexions DB chaque jour |
| D10 | Client Java | **Spring Vault 4.1** (`spring-vault-core`) + configuration maison | Spring Cloud Vault : le train compatible Boot 4.1 (2026.0) n'est pas encore GA → à réévaluer à sa sortie |

## Architecture cible

```
                        ┌──────────────────────── réseau Docker interne (172.30.0.0/24) ───────────────────────┐
HAProxy ──TLS 1.3──▶ API ──TLS──▶ OpenBao (Raft, scellé au démarrage)                                          │
                      │   AppRole : role_id (config) + secret_id (fichier 0440, renouvelé à chaque déploiement)  │
                      │   ├─ transit/sign/jwt          ES256, kid = version                                     │
                      │   ├─ transit/encrypt|decrypt/niss                                                        │
                      │   ├─ transit/hmac/niss-index                                                             │
                      │   └─ database/creds/app        utilisateur PG temporaire (DML uniquement)               │
                      └──▶ PostgreSQL ◀── utilisateurs créés / révoqués par OpenBao (connexion admin dédiée)    │
                        └───────────────────────────────────────────────────────────────────────────────────────┘
```

### Modèle de droits PostgreSQL

| Rôle | Type | Droits |
|---|---|---|
| `formation_owner` | groupe NOLOGIN | propriétaire du schéma et des objets |
| `formation_rw` | groupe NOLOGIN | `SELECT, INSERT, UPDATE, DELETE` (via `ALTER DEFAULT PRIVILEGES FOR ROLE formation_owner`) |
| `v-approle-migrator-…` | dynamique (Flyway, au démarrage) | membre de `formation_owner` ; Flyway exécute `SET ROLE formation_owner` pour que les objets n'appartiennent pas à l'utilisateur temporaire |
| `v-approle-app-…` | dynamique (runtime) | membre de `formation_rw` uniquement : aucun DDL possible |
| `openbao_admin` | statique (mot de passe connu d'OpenBao seul, tourné par `rotate-root`) | `CREATEROLE`, rien d'autre |

### Politiques OpenBao (moindre privilège)

- `api` : `transit/sign/jwt`, `transit/keys/jwt` (lecture des clés publiques), `transit/encrypt/niss`,
  `transit/decrypt/niss`, `transit/hmac/niss-index`, `transit/{encrypt,decrypt,rewrap}/file` (clés de données des
  fichiers téléversés, ajout du 16/11/2026), `database/creds/app`, `database/creds/migrator`.
  **Aucun** accès en lecture aux clés privées, à `sys/`, ni à l'administration.
- `deployer` (Jenkins) : création d'un `secret_id` pour l'AppRole `api` uniquement.
- `admin` : humain, via token éphémère ; le token root est **révoqué** après l'amorçage.

## Plan de mise en œuvre

Les phases 1 à 3 relèvent de l'**infrastructure** : implémentées directement, avec leurs tests. Les phases 4 et 5
sont des **features** et suivent le workflow `/analyse-feature` → `/owasp-check` → `/review-analyse` →
`/write-tests` → `/implement-feature` → `/run-tests` → `/triage-failures` → `/trace-check` → `/mutation-test`.

### Phase 1 — OpenBao (infra) — ✅ terminée le 16/11/2026 (voir `docs/ops/OPENBAO.md`)
- [x] 1.1 Service `openbao` (dev : mode dev ; prod : `openbao.hcl`, Raft, listener TLS interne, audit fichier,
      conteneur durci comme l'API). CA interne pour le TLS API → OpenBao (`docker/prod/openbao/`).
- [x] 1.2 `openbao-init` (dev) : lit `.env.dev`, configure engines, clés, rôles DB, AppRole, politiques —
      **même script de configuration que la prod** (seule la source des valeurs change).
- [x] 1.3 Amorçage prod `bootstrap.sh` (idempotent) : `operator init` (parts affichées une seule fois → 1Password,
      jamais écrites sur disque), configuration, révocation du token root.
- [x] 1.4 PostgreSQL : rôles `formation_owner` / `formation_rw` / `openbao_admin` (`docker/db/roles.sh`, idempotent ; hors Flyway car il faut un superutilisateur).
- [x] 1.5 `start.sh --prod` : détecte un OpenBao scellé, affiche la procédure de descellement et attend.
- [x] 1.6 Snapshots Raft quotidiens (timer systemd) + procédure de restauration **testée**.

### Phase 2 — Intégration Spring (infra) — ✅ 16/11/2026
- [x] 2.1 `spring-vault-core` 4.1 ; authentification AppRole (`secret_id` lu depuis un fichier), TLS avec la CA interne.
- [x] 2.2 DataSource dynamique : `SecretLeaseContainer` sur `database/creds/app`, renouvellement du bail, nouvel
      identifiant avant `max_ttl` → mise à jour Hikari + `softEvictConnections()` (sans coupure).
- [x] 2.3 Flyway avec `database/creds/migrator` + `initSql: SET ROLE formation_owner`.
- [x] 2.4 Démarrage en échec explicite si OpenBao est injoignable ou scellé ; `HealthIndicator` OpenBao.
- [x] 2.5 Tests d'intégration : Testcontainers (`testcontainers-vault`, image OpenBao) + PostgreSQL.

### Phase 3 — `CryptoService` (infra) — ✅ 16/11/2026
- [x] 3.1 Interface `CryptoService` (`encrypt`, `decrypt`, `blindIndex`) + implémentation Transit ;
      chiffré stocké avec sa version (`vault:vN:…`) ; contexte (AAD) = type de donnée.
- [x] 3.2 Normalisation et validation du NISS (11 chiffres, clé de contrôle modulo 97) avant HMAC.
- [x] 3.3 Rotation : `transit/keys/niss/rotate` + job de `rewrap` ; `min_decryption_version` relevé ensuite.
- [x] 3.4 Tests : unitaires (implémentation factice) + intégration avec OpenBao réel.

### Phase 4 — Authentification (feature)
- [ ] `JwtEncoder` délégant la signature à Transit (ES256, `kid`), `JwtDecoder` sur les clés publiques Transit (cache).
- [ ] Refresh token opaque 256 bits, stocké **haché**, familles de tokens, détection de réutilisation, 8 h glissant / 24 h max.
- [ ] Login, refresh, logout ; limitation des tentatives ; analyse OWASP (ASVS V2/V3, cheat sheets JWT et Session).

### Phase 5 — Utilisateur et NISS (feature)
- [ ] Entité utilisateur, NISS chiffré via `CryptoService` + colonne d'index aveugle unique.

### Phase 6 — Exploitation
- [ ] Jenkins : renouvellement du `secret_id` à chaque déploiement (politique `deployer`).
- [ ] Runbooks : base posée dans `docs/ops/OPENBAO.md` (descellement, break-glass, rotation, restauration testée) — à compléter (supervision, Jenkins).
- [ ] Supervision : alerte si OpenBao est scellé, si un bail DB n'est pas renouvelé, ou si un snapshot échoue.

## Questions ouvertes

| # | Question | Hypothèse par défaut | Phase |
|---|---|---|---|
| Q1 | ~~Transport du refresh token vers Angular : cookie `HttpOnly; Secure; SameSite=Strict` (implique CSRF + `allow-credentials`) ou corps de réponse + stockage en mémoire~~ | **Résolue → ADR 0002** | — |
| Q2 | ~~Hachage des mots de passe : Argon2id (Bouncy Castle) ou bcrypt~~ | **Résolue → ADR 0002** | — |
| Q3 | Fréquence de rotation des clés `jwt` et `niss` | `jwt` : 90 j ; `niss` : 1 an + rewrap | 3-4 |
| Q4 | Détenteurs des 5 parts Shamir (personnes distinctes ?) | À désigner avant la mise en production | 1 |
| Q5 | Destination hors site des snapshots Raft | À définir avec l'hébergeur | 1 |

## Risques acceptés

- **OpenBao mono-nœud = point de défaillance unique** : s'il est indisponible, plus de login, de NISS ni de nouvelle
  connexion DB (les connexions déjà ouvertes survivent jusqu'à la fin du bail).
- **Descellement manuel** : après un redémarrage, l'API reste indisponible jusqu'à l'intervention de 3 détenteurs de
  parts → alerte et procédure obligatoires.
- ~~Spring Vault 4.1.1 / Spring Framework~~ : **levé** — Boot 4.1.2 embarque Spring Framework 7.0.10, la version de Spring Vault 4.1.1.
