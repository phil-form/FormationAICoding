# CLAUDE.md

Contexte projet pour Claude Code. À lire avant toute intervention.

## Statut

Phase de **préparation** : aucune ligne de code applicatif n'existe encore.
Les spécifications détaillées à implémenter sont dans `./tmp/` :

| Fichier | Contenu |
|---|---|
| `tmp/01-STRUCTURE.md` | Arborescence, packages, couches (controller, service, repository…), conventions |
| `tmp/02-DOCKER.md` | Spécification `docker-compose.yml` et des dossiers `docker/dev/`, `docker/prod/`, `docker/test/` |
| `tmp/03-SCRIPTS.md` | Spécification `start.sh` (avec seed optionnel) et `restore.sh` (picker fzf) |

Lorsqu'une spec de `tmp/` a été implémentée, la mettre à jour (ou la supprimer) et refléter
l'état réel dans ce fichier.

## Stack technique

| Élément | Choix | Remarques |
|---|---|---|
| Langage | **Java 25** (LTS, sept. 2025) | Prochain LTS : Java 29 (sept. 2027) |
| Framework | **Spring Boot 4.1.x** | Spring n'a pas de « LTS » officiel : on prend la dernière GA stable. Passer en 4.2.x quand elle sera GA |
| Build | **Maven** (wrapper `./mvnw`) | |
| Base de données | **PostgreSQL 18** | Image `postgres:18-alpine` |
| ORM | **Hibernate** (ORM 7.x, via Spring Data JPA) | Version gérée par le BOM Spring Boot |
| Boilerplate | **Lombok** | Getters/setters, constructeurs, builders, logs |
| Mapping | **MapStruct** (+ `lombok-mapstruct-binding`) | Mappers générés à la compilation |
| Migrations | **Flyway** | Seul responsable du schéma |
| Doc API | springdoc-openapi (Swagger UI) | Activé en dev uniquement |
| Tests | JUnit 5, Spring Boot Test, **Testcontainers** (postgres:18) | Pas de H2 |
| Conteneurs | Docker + Docker Compose | Dev et prod conteneurisés |

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
- **Entités** : héritent de `framework.entity.BaseEntity` (id, audit, `@Version`, `equals/hashCode`
  sûrs), associations en `LAZY` par défaut.
- **Erreurs** : `@RestControllerAdvice` global renvoyant des `ProblemDetail` (RFC 9457).
- **URLs** : préfixe `/api/v1`, ressources au pluriel, kebab-case (`/api/v1/training-sessions`).
- **Pagination** : `Pageable` + réponse paginée pour les listes.

## Base de données & JPA

- `spring.jpa.hibernate.ddl-auto=validate` partout : **Hibernate ne modifie jamais le schéma**,
  Flyway le fait.
- `spring.jpa.open-in-view=false`.
- Migrations : `src/main/resources/db/migration/V<n>__<description>.sql` — ne jamais modifier une
  migration déjà appliquée, en créer une nouvelle.
- Données de seed (dev uniquement) : `src/main/resources/db/seed/R__seed_*.sql` (migrations
  répétables Flyway), activées via la variable `SEED=true` (voir `start.sh`).
- Backups / dumps de restauration : dossier `./init_db/` (utilisé par `restore.sh`).

## Profils Spring

| Profil | Usage |
|---|---|
| `dev` | Conteneur dev, devtools, Swagger UI, logs SQL, port debug 5005 |
| `prod` | Conteneur prod, pas de Swagger, logs JSON, actuator restreint (`health`, `info`) |
| `test` | Tests d'intégration (Testcontainers) |

Docker : un dossier par environnement (`docker/dev/`, `docker/prod/`, `docker/test/`), chacun avec son
`Dockerfile`, son `Dockerfile.dockerignore` et ses scripts propres ; un seul `docker-compose.yml` à la
racine avec les profils Compose `dev`, `prod`, `test`.

Toute la configuration sensible passe par variables d'environnement (fichier `.env`, jamais commité ;
`.env.example` sert de modèle).

## Commandes (cibles, une fois implémentées)

```bash
./start.sh                 # démarre la stack en dev
./start.sh --seed          # démarre en dev et applique les données de seed
./start.sh --prod          # démarre la stack en prod
./start.sh --test          # lance la suite de tests dans docker/test
./restore.sh               # choisit un dump dans ./init_db via fzf et le restaure
./mvnw verify              # tests hors conteneur (Docker requis pour Testcontainers)
./mvnw spring-boot:run     # lancement hors Docker (nécessite une DB accessible)
```

## Workflow IA : skills et agents (`.claude/`)

Développement **piloté par l'analyse** : l'analyse préparatoire d'une feature est le seul contrat commun entre
un agent qui écrit les tests et un agent qui écrit le code, et ces deux agents ne se voient pas. Un auditeur,
seul à voir les deux côtés, arbitre et contrôle.

```
docs/analyse/client/* ─/analyse-feature─▶ features/NNN-slug.md (+ /owasp-check) ─/review-analyse─▶ verdict OK ?
                                                                                                     │ oui
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

- Répondre et documenter **en français** ; code, identifiants et commits en anglais.
- Ne pas ajouter de dépendance non listée ci-dessus sans le signaler.
- Toute modification de schéma = nouvelle migration Flyway.
- Ajouter/mettre à jour les tests avec chaque fonctionnalité.

## Points ouverts (à trancher)

- Authentification / autorisation (probablement JWT pour le front Angular) — non défini.
- Configuration CORS pour l'origine du front Angular.
- Domaine métier / premières entités — non défini.
- Hébergement prod et stratégie de backup automatisée.
