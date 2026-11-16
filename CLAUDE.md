# CLAUDE.md

Contexte projet pour Claude Code. À lire avant toute intervention.

## Statut

**Socle en place** (compilé, 32 tests verts, stack dev/prod/test vérifiée de bout en bout) :
structure par couche, `framework/` (sécurité HTTP, CORS, erreurs `ProblemDetail`, `BaseEntity`, pagination),
Docker dev/prod/test, `start.sh`, `restore.sh`. **Aucune feature métier ni authentification** pour l'instant :
toutes les routes `/api/**` répondent 401 (refus par défaut).

Feuille de route : **`Next.md`** (6 phases, décisions dans `docs/adr/0001-gestion-des-secrets.md`).
**Phase 1 (OpenBao) terminée** : coffre dev/prod, Transit (JWT, NISS, index), identifiants PostgreSQL dynamiques,
AppRole `api`, break-glass, snapshots. Runbook : `docs/ops/OPENBAO.md`.
**Phase 2 (intégration Spring) terminée** : l'API n'a plus aucun mot de passe DB ; identifiants dynamiques en rotation,
Flyway via un migrateur temporaire, refus de démarrer si OpenBao est scellé.
**Phase 3 (`CryptoService`) terminée** : chiffrement Transit lié à la ligne, index aveugle, rotation (rewrap), type `Niss`.
Prochaine : **phase 4** (authentification, workflow feature complet).

Les specs initiales de `tmp/` sont implémentées ; elles restent comme référence de conception — en cas d'écart,
**le code fait foi**.

## Stack technique

| Élément | Choix | Remarques |
|---|---|---|
| Langage | **Java 25** (LTS, sept. 2025) | Prochain LTS : Java 29 (sept. 2027) |
| Framework | **Spring Boot 4.1.2** (Spring Security 7, Jackson 3, Tomcat 11) | Spring n'a pas de « LTS » officiel : on prend la dernière GA stable. Passer en 4.2.x quand elle sera GA |
| Build | **Maven** (wrapper `./mvnw`) | |
| Base de données | **PostgreSQL 18** | Image `postgres:18-alpine` |
| ORM | **Hibernate** (ORM 7.x, via Spring Data JPA) | Version gérée par le BOM Spring Boot |
| Boilerplate | **Lombok** | Getters/setters, constructeurs, builders, logs |
| Mapping | **MapStruct 1.6.3** (+ `lombok-mapstruct-binding`) | Mappers générés à la compilation |
| Migrations | **Flyway** | Seul responsable du schéma |
| Doc API | springdoc-openapi 3.2 (Swagger UI) | Activé en dev uniquement |
| Sécurité | Spring Security 7 | Voir section « Sécurité » |
| Mutation testing | PIT 1.30 | `/mutation-test` |
| Tests | JUnit 5, `MockMvcTester` (AssertJ), **Testcontainers 2** (postgres:18-alpine) | Pas de H2 ; `*Test` = Surefire, `*IT` = Failsafe |
| Conteneurs | Docker + Docker Compose | Dev et prod conteneurisés |
| Secrets | **OpenBao 2.7** (Transit, database, KV, AppRole) via **Spring Vault 4.1.1** | ADR 0001 ; `framework/openbao/` |
| Données de seed | **Datafaker 2.7** (profil `seed`) | Exclu du jar de production |

## Architecture

- **Monolithe** (pas de microservices), **API REST uniquement**. Le front est une application
  Angular dans un dépôt séparé : aucun template, aucune ressource statique ici.
- Organisation **par couche**, directement sous le package racine (pas de package par feature) :
  `controller/`, `service/` (+ `service/impl/`), `repository/`, `entity/`, `dto/` (`request/`, `response/`),
  `mapper/`. Le socle transverse (config, exceptions, classes de base) va dans **`framework/`**.
  Détails : `tmp/01-STRUCTURE.md`.
- Package racine : `be.bstorm.formation` (à ajuster si le nom du projet change).

## Conventions de code

- **Controllers** : minces, pas de logique métier, n'exposent **jamais** d'entités JPA → uniquement des DTO.
- **DTO** : `record` Java, validés avec Jakarta Validation (`@Valid`, `@NotBlank`…), `@Builder` si utile.
- **Mappers** : interfaces **MapStruct** (`XxxMapper`, `@Mapper(config = MapStructConfig.class)`),
  `unmappedTargetPolicy = ERROR`. Pas de mapping écrit à la main ni dans les controllers.
- **Services** : interface + implémentation (`service/impl/XxxServiceImpl`), portent la logique métier
  et les transactions (`@Transactional` au niveau service, `readOnly = true` pour les lectures).
- **Repositories** : interfaces Spring Data JPA.
- **Lombok** utilisé systématiquement pour le boilerplate :
  - entités : `@Getter @Setter @NoArgsConstructor(access = PROTECTED) @AllArgsConstructor @Builder` ;
    **jamais** `@Data`, `@EqualsAndHashCode` ni `@ToString` non maîtrisé sur une entité ;
  - services/controllers : `@RequiredArgsConstructor` (injection par constructeur, champs `private final`),
    `@Slf4j` pour les logs. Pas de `@Autowired` sur les champs.
- **Seeders** : `seed/XxxSeeder` annoté `@Seeder`, implémente `DataSeeder` (voir « Base de données & JPA »).
- **Accès DB** : ne jamais configurer d'identifiant DB statique pour l'API ; avec `app.openbao.enabled=true` (dev, prod), le
  `DataSource` vient de `framework/openbao/OpenBaoDataSourceConfig` (utilisateur dynamique DML, rotation sans coupure).
  Les tests (profil `test`) n'utilisent pas OpenBao, sauf `OpenBaoIntegrationIT`.
- **Migrations** : exécutées en `SET ROLE formation_owner` (propriétaire du schéma, **pas** de la base) : aucune
  instruction de niveau base (`ALTER DATABASE`, `CREATE EXTENSION` hors schéma…) dans une migration → `docker/db/roles.sh`.
- **Données sensibles** (NISS…) : jamais en clair en base, dans un log ou une erreur. Toute donnée classée `S2`
  (grille `.claude/skills/sensitive-data/classification.md`) est **chiffrée systématiquement** ; détection par
  `/sensitive-data` à chaque analyse.
  - Type `framework.type.Niss` (`Niss.of(raw)` normalise et valide, `toString()` masqué) ; DTO : `@ValidNiss`.
  - Stockage : `CryptoService.encrypt(SensitiveDataType.NISS, niss.digits(), context)` + colonne d'index aveugle
    **unique** `CryptoService.blindIndex(NISS, niss.digits())` ; recherche = calcul de l'index puis égalité.
  - `context` = identifiant **immuable** de la ligne, connu **avant** le chiffrement (ex. UUID de l'entité) : un
    chiffré est indéchiffrable sur une autre ligne.
  - Nouvelle colonne chiffrée → une implémentation de `framework.crypto.RewrapTarget` (rotation de clé).
  - Tests : `TestcontainersConfig` fournit un `InMemoryCryptoService` (même contrat que Transit).
- **Entités** : héritent de `framework.entity.BaseEntity` (id **UUID v7**, audit `createdAt`/`updatedAt`,
  `@Version`, `equals/hashCode` sûrs), associations en `LAZY` par défaut. Colonnes attendues dans chaque table :
  `id uuid primary key`, `created_at timestamptz`, `updated_at timestamptz`, `version bigint`.
- **Erreurs** : `framework.exception.GlobalExceptionHandler` renvoie des `ProblemDetail` (RFC 9457) pour tout,
  y compris 401/403. Lever `NotFoundException` (404), `ConflictException` (409) ou `BusinessException`
  (statut libre, 422 par défaut). Validation → 400 avec `errors[{field,message}]`. Erreur inattendue → 500
  générique + `errorId` (aussi dans le log). Jamais de détail technique au client.
- **URLs** : préfixe `/api/v1`, ressources au pluriel, kebab-case (`/api/v1/training-sessions`).
- **Pagination** : `Pageable` (taille max 100) + `framework.dto.PageResponse` pour les listes.
- **JSON** : les champs inconnus sont refusés (400) — pas d'affectation silencieuse.

## Base de données & JPA

- `spring.jpa.hibernate.ddl-auto=validate` partout : **Hibernate ne modifie jamais le schéma**,
  Flyway le fait.
- `spring.jpa.open-in-view=false`.
- Migrations : `src/main/resources/db/migration/V<n>__<description>.sql` — ne jamais modifier une
  migration déjà appliquée, en créer une nouvelle.
- **Seed (dev uniquement)** : en **Java**, jamais en SQL — un `@Seeder` par agrégat dans `be.bstorm.formation.seed`,
  implémentant `framework.seed.DataSeeder` (`order()`, `shouldRun()` idempotent, `seed()`), qui utilise entités,
  repositories et services (validation, audit, UUID v7, chiffrement NISS respectés). Exécuté par `SeedRunner` après
  Flyway, une transaction par seeder, échec = démarrage en erreur ; interdit avec le profil `prod`.
  Activé par le profil `seed` (`./start.sh --seed`). Toute nouvelle entité devrait avoir son seeder.
- Backups / dumps de restauration : dossier `./init_db/` (utilisé par `restore.sh`).

## Profils Spring

| Profil | Usage |
|---|---|
| `dev` | HTTP 8080, devtools, Swagger UI, logs SQL, debug 5005, actuator étendu, CORS `localhost:*` |
| `prod` | HTTPS 8443 (TLS 1.3) derrière HAProxy, mode **strict**, logs JSON ECS, actuator interne |
| `seed` | Exécute les `@Seeder` Java au démarrage (dev uniquement, refusé avec `prod`) |
| `test` | Tests d'intégration (Testcontainers) |

Docker — trois fichiers compose (regroupés par ordre alphabétique) et un dossier par environnement :

| Fichier | Projet | Contenu |
|---|---|---|
| `docker-compose.yml` | `formation` (réseau 172.30.0.0/24) | **dev** : db (127.0.0.1:5432), OpenBao dev + `db-roles` + `openbao-init`, `api-dev` ; profil `test` : `api-test` |
| `docker-compose.prod.yml` | `formation-prod` (172.31.0.0/24) | **prod** : db (non publiée), `openbao-prod`, `api-prod` (127.0.0.1:8443 pour le reverse proxy) |
| `docker-compose.test-prod.yml` | surcharge de prod | **test local de la prod** : `haproxy` (80/443, `docker/prod/haproxy/haproxy.cfg`) → `waf` (ModSecurity + OWASP CRS 4.25 LTS, paranoïa 2) → `api-prod` (non publiée) ; PKI de test `docker/test-prod/gen-certs.sh` |

Dossiers `docker/dev/`, `docker/prod/`, `docker/test/` (Dockerfile + fichiers propres), `docker/test-prod/`
(certificats de test, recette `check.sh`), `docker/openbao/`, `docker/db/`. Dev et test-prod peuvent tourner en même temps.

Toute la configuration sensible passe par variables d'environnement (fichier `.env`, jamais commité ;
`.env.example` sert de modèle).

## Sécurité

Code : `framework/config/SecurityConfig`, `CorsConfig`, `HttpRedirectConnectorConfig`, `framework/security/*`.
Configuration : `app.security.*` (`SecurityProperties`).

- **Refus par défaut** : seuls `health`/`info`, `/error` et Swagger (si activé) sont publics.
- **Sans état** : pas de session, de cookie, de formulaire ni de Basic. CSRF désactivé tant qu'aucune
  authentification ne repose sur un cookie — **à réactiver** si le refresh token est stocké en cookie.
- **Mode strict** (`app.security.strict=true`, prod) : le démarrage **échoue** si HTTPS n'est pas exigé, si HSTS
  < 1 an, si une origine CORS n'est pas HTTPS explicite (pas de joker, pas de pattern), si la CSP contient
  `unsafe-*`. Ne jamais contourner : corriger la configuration.
- **Architecture prod** : Internet → **HAProxy** (443, Let's Encrypt, redirection 80→443, challenge ACME,
  contrôle du Host, limitation de débit) → **TLS 1.3 re-chiffré et vérifié** → API publiée sur `127.0.0.1:8443`
  uniquement. Procédure serveur : `docs/ops/DEPLOIEMENT-PROD.md`.
- **Certificats** : renouvelés par certbot ; le deploy hook (`docker/prod/certbot/deploy-hook.sh`) les copie de
  façon atomique dans le dossier monté **en lecture seule** dans le conteneur → rechargement à chaud (~10 s, sans
  redémarrage, vérifié) ; il recharge aussi HAProxy.
- **TLS (API et HAProxy)** : TLS 1.3 uniquement, suites `TLS_AES_256_GCM_SHA384`, `TLS_CHACHA20_POLY1305_SHA256`,
  `TLS_AES_128_GCM_SHA256`, groupes `x25519`, `secp256r1`, `secp384r1`, HTTP/2, pas de tickets de session (HAProxy).
- **IP client** : `X-Forwarded-For`/`-Proto` pris en compte **uniquement** depuis `TRUSTED_PROXIES` (passerelle du
  réseau Docker fixe `DOCKER_SUBNET`) ; HAProxy supprime ceux envoyés par le client.
- **HTTPS forcé dans l'API** (défense en profondeur) : `GET`/`HEAD` en clair → 301 ; autres méthodes → 403.
- **En-têtes** : HSTS 2 ans `includeSubDomains; preload` (HTTPS uniquement), CSP `default-src 'none'` +
  `upgrade-insecure-requests` (prod), `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy: no-referrer`,
  `Permissions-Policy` restrictive, COOP/CORP `same-origin`, `Cache-Control: no-store`.
- **Actuator (prod)** : port 8081 lié à `127.0.0.1` dans le conteneur (healthcheck Docker uniquement),
  `health`/`info` en lecture seule, tout le reste désactivé.
- **Conteneur prod** : publié sur `127.0.0.1` uniquement, utilisateur 10001, clé TLS lue via le groupe `TLS_CERT_GID`, système de fichiers en lecture seule (`/tmp` en tmpfs),
  `cap_drop: ALL`, `no-new-privileges`, limites mémoire/PIDs, DB publiée sur `127.0.0.1` uniquement.

## Commandes

```bash
./start.sh                 # démarre la stack en dev
./start.sh --seed          # démarre en dev et applique les données de seed
./start.sh --prod          # démarre la stack en prod (docker-compose.prod.yml)
./start.sh --test-prod     # chaîne de prod complète en local : HAProxy → WAF → API (https://localhost)
docker/test-prod/check.sh  # recette test-prod : TLS, redirection, attaques bloquées par le WAF, isolement
./start.sh --test          # lance la suite de tests dans docker/test
./restore.sh [--prod]      # choisit un dump dans ./init_db via fzf et le restaure (dev par défaut)
./start.sh --fresh         # repart d'une base vide (confirmation)
docker/openbao/check-phase1.sh       # recette OpenBao + droits PostgreSQL (dev)
docker/prod/openbao/bootstrap.sh     # amorçage OpenBao prod (puis unseal.sh / admin-token.sh, voir docs/ops/OPENBAO.md)
docker/prod/gen-selfsigned-cert.sh   # certificat auto-signé pour tester la prod en local
./mvnw verify              # tests hors conteneur (JDK 25 + Docker requis)
```

## Workflow IA : skills et agents (`.claude/`)

Développement **piloté par l'analyse** : l'analyse préparatoire d'une feature est le seul contrat commun entre
un agent qui écrit les tests et un agent qui écrit le code, et ces deux agents ne se voient pas. Un auditeur,
seul à voir les deux côtés, arbitre et contrôle.

```
docs/analyse/client/* ─/analyse-feature─▶ features/NNN-slug.md (+ /owasp-check) ─/review-analyse─▶ verdict OK ?
                                                               (+ /sensitive-data toujours)          │ oui
                             ┌──────────────/write-tests──────────────┴──────/implement-feature──────────┐
                             ▼ test-writer (analyse → src/test)              code-writer (analyse → src/main) ▼
                             └────────────────────────────/run-tests──────────────────────────────────────┘
                                                              │ échecs
                                              /triage-failures (auditor : voit tout)
                     ┌──────────────────────┬─────────────────┴──────────┬───────────────────────┐
              TEST: feedback-tests.md  CODE: feedback-code.md   ANALYSE: reviews/NNN.triage.md   INFRA
              /write-tests --fix       /implement-feature --fix  /analyse-feature → /review-analyse  /analyse-logs
Feature verte ─▶ /trace-check (couverture AC ↔ tests ↔ code) ─▶ /mutation-test (solidité des tests)
```

| Skill | Agent | Rôle |
|---|---|---|
| `/analyse-feature <analyse client> <feature>` | `analyst` | Analyse préparatoire : `RG-xx`, `AC-xx`, contrat technique, modèle de données ; traite les revues existantes |
| `/sensitive-data <analyse>` | `analyst` | Classe chaque donnée (`S0`/`S1`/`S2`/`SECRET`), signale les données sensibles et impose leur **chiffrement `CryptoService`** (`SD-xx`, `AC-SD-xx`, § 7.5) ; appelé **systématiquement** par `/analyse-feature` |
| `/owasp-check <analyse>` | `analyst` | Exigences `SEC-xx` + critères testables `AC-SEC-xx` (OWASP Top 10, API Top 10, ASVS L2) ; appelé automatiquement si la feature touche à la sécurité |
| `/review-analyse <analyse>` | `analysis-reviewer` | Relecture critique (contrat, testabilité, cohérence, sécurité) → `docs/analyse/reviews/NNN-slug.review.md`, verdict **OK / À corriger** ; obligatoire avant tests et code |
| `/write-tests <analyse> [--fix]` | `test-writer` | Tests depuis l'analyse seule ; `@Tag("feature-NNN")`, méthodes `acxx_…` / `acsecxx_…` ; `--fix` lit `feedback-tests.md` |
| `/implement-feature <analyse> [--fix]` | `code-writer` | Code depuis l'analyse seule ; `--fix` lit `feedback-code.md` |
| `/run-tests [--unit\|--it] [--docker]` | — | Tests avec progression dans la status line (%, ETA, ✔/✘/↷) |
| `/triage-failures <analyse>` | `auditor` | Verdict par échec : CODE / TEST / ANALYSE / INFRA, retour au bon destinataire |
| `/trace-check <analyse>` | `auditor` | Matrice AC ↔ tests ↔ code (script + contrôle sémantique) |
| `/mutation-test [<analyse>\|<package>]` | `auditor` | PIT : score par classe, mutants survivants traduits en lacunes de test → `docs/audit/` |
| `/analyse-logs [service\|tests\|fichier]` | `log-analyst` | Erreurs groupées, chronologie, cause probable reliée au code, signaux de sécurité (lecture seule) |
| `/audit-deps [--images] [--fix]` | `general-purpose` | CVE des dépendances Maven et images Docker → `docs/audit/` |
| `/deprecations [--log f] [--no-compile]` | — | Après les tests : dépréciations (compilation `-Xlint`, JVM, Spring, Hibernate, Mockito, bibliothèques) → plan de correction dans `docs/audit/`, sans modifier le code |
| `/commit [intention] [--no-images]` | — | Commit après **suite de tests complète** puis contrôles : secrets, fichiers interdits, SAST Semgrep, config Docker, dépendances, images Docker (Trivy). Détection → rapport + décision de l'utilisateur, **aucune correction automatique** ; jamais de push |
| `/jenkins-pipeline [create\|update]` | — (manuel uniquement) | `Jenkinsfile` : build, tests, SAST, SCA, scan image, DAST ZAP, deploy scp + ssh |

**Isolation** — chaque agent sensible a un hook `PreToolUse` (`.claude/hooks/guard.sh <mode>`) :

| Agent | Lit | Écrit | Bash |
|---|---|---|---|
| `test-writer` | `docs/analyse/`, `src/test/`, `CLAUDE.md`, `pom.xml`, `tmp/`, `feedback-tests.md` | `src/test/` | non |
| `code-writer` | tout sauf tests, rapports de tests/PIT, retours non triés, `.claude/state/audit/` | tout sauf zones de tests, `docs/`, `.claude/` | `./mvnw` sans exécuter les tests |
| `analysis-reviewer` | `docs/`, `CLAUDE.md`, `tmp/`, `src/main/`, `pom.xml` | `docs/analyse/reviews/` | non |
| `auditor` | tout | `.claude/state/audit/`, `feedback-*.md`, `docs/analyse/reviews/`, `docs/audit/` | `./mvnw`, `git` lecture, scripts des skills |
| `log-analyst` | tout | rien | pipelines de lecture (`docker compose logs`, `grep`, `jq`…) |

`.env` est illisible pour tous ces agents. **Règle d'étanchéité** : les fichiers `feedback-*.md` qui franchissent
la frontière tests/code ne contiennent que des références à l'analyse, jamais de code. C'est un garde-fou, pas un
sandbox : ne jamais copier de code de test dans le prompt de `code-writer` (ni l'inverse).

**Hook projet `flyway-guard`** (`.claude/settings.json`, pour toute la session) : refuse la modification ou la
suppression d'une migration `V*.sql` déjà commitée, la réutilisation d'une version et toute migration hors ordre.

Dossiers : `docs/analyse/client/` (entrées client), `docs/analyse/features/` (analyses préparatoires),
`docs/analyse/reviews/` (revues et arbitrages), `docs/audit/` (rapports d'audit versionnés),
`.claude/state/` (état local : progression et log des tests, retours, rapports d'audit — non versionné).

## Règles pour Claude

- Décisions d'architecture : `docs/adr/` (0001 secrets/OpenBao, 0002 authentification) — les respecter ; toute remise
  en cause passe par un nouvel ADR.
- **Secrets** : jamais dans git, l'image, les logs ni les variables d'environnement de prod ; ils viennent d'OpenBao.

- Répondre et documenter **en français** ; code, identifiants et commits en anglais.
- **Commits : toujours via `/commit`** (tests complets puis contrôles de sécurité) — jamais de `git commit` direct ni de push.
- Ne pas ajouter de dépendance non listée ci-dessus sans le signaler.
- Toute modification de schéma = nouvelle migration Flyway.
- Ajouter/mettre à jour les tests avec chaque fonctionnalité.

## Points ouverts (à trancher)

- Secrets, JWT, NISS, authentification : **décidés** (ADR 0001, ADR 0002). Restent ouvertes (ADR 0001) : Q3
  fréquence de rotation des clés, Q4 détenteurs des parts Shamir, Q5 sauvegarde hors site.
- Domaine métier / premières entités — non défini.
- Hébergement prod, rate limiting (bucket4j ou proxy), stratégie de backup automatisée.
