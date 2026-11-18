# Formation — API

API REST monolithique : Java 25 · Spring Boot 4.1 · Spring Security 7 · PostgreSQL 18 · Hibernate/JPA · Flyway ·
Lombok · MapStruct. Conteneurisée en dev comme en prod. Le front (Angular) vit dans un dépôt séparé.

## Les exemples de la formation

Ce dépôt sert de support à la formation « développer avec l'IA » (Claude Code). Il contient **trois exemples
indépendants**. Chacun illustre une façon de travailler différente, choisie selon la situation de départ :

| Exemple | Situation de départ | Méthodologie illustrée | README |
|---|---|---|---|
| **API Java** (racine) | Projet neuf, rien n'existe | **Développement piloté par l'analyse** : la spécification d'abord, puis des agents isolés écrivent tests et code sans se voir | ce fichier, § suivant |
| **flotte** (`ExampleRefactorUpgrade/flotte/`) | Application legacy de 2016, **sans aucun test** | **Migration « bug pour bug »** : figer le comportement par des tests de caractérisation, puis monter les versions une marche à la fois | [`ExampleRefactorUpgrade/flotte/README.md`](ExampleRefactorUpgrade/flotte/README.md) |
| **DemoMCP** (`DemoMCP/`) | Besoin de brancher l'IA sur un outil métier | **Apprentissage progressif du protocole MCP** : serveur, client, tests, agent, puis sécurité et authentification | [`DemoMCP/README.md`](DemoMCP/README.md) |

Points communs aux trois exemples, à retenir :

1. **Préparer avant de coder.** On écrit d'abord un document (spécification, plan de migration, plan de démo) et un
   `CLAUDE.md` qui fixe le contexte. L'IA travaille mieux avec un cadre explicite qu'avec une demande vague.
2. **Le test est le juge, pas l'IA.** Une réponse de l'IA n'est validée que par un test qui passe, écrit
   indépendamment du code qu'il vérifie.
3. **Des garde-fous automatiques plutôt que des consignes.** Les règles importantes (pas d'accès aux secrets, tests
   gelés, agents isolés) sont imposées par des *hooks* qui refusent l'action, pas seulement écrites dans un prompt.
4. **L'humain décide.** L'IA s'arrête et demande dès qu'un choix engage le projet : changement de comportement,
   faille de sécurité, dérogation, commit.
5. **Tout est tracé.** Décisions (ADR, changements acceptés), rapports d'audit et prompts exportés (`tmp/prompt.md`,
   `ExampleRefactorUpgrade/flotte/tmp/prompts.md`, `DemoMCP/prompt.md`) permettent de rejouer et d'expliquer la
   démarche.

## Méthodologie de l'API Java : le développement piloté par l'analyse

### Le problème

Quand une même IA écrit le code **et** ses tests, les tests ont tendance à confirmer le code tel qu'il est écrit,
erreurs comprises : ils ne prouvent rien. Et sans spécification écrite, personne ne peut dire si le résultat est
juste.

### Le principe

Une **analyse préparatoire** de la feature (règles métier `RG-xx`, critères d'acceptation `AC-xx`, contrat technique,
modèle de données) devient **le seul contrat commun** entre deux agents qui ne se voient pas :

- l'agent **`test-writer`** écrit les tests à partir de l'analyse, sans aucun accès au code de production ;
- l'agent **`code-writer`** écrit le code à partir de l'analyse, sans aucun accès aux tests.

Si les tests passent, c'est que deux lectures indépendantes de la même analyse concordent. Si un test échoue, un
troisième agent, l'**`auditor`** (le seul à voir les deux côtés), décide qui est en cause : le code, le test,
l'analyse ou l'infrastructure. Il renvoie un retour formulé **uniquement en termes d'analyse** (« AC-04 n'est pas
respecté »), jamais en recopiant du code, pour ne pas briser l'isolement.

### Le déroulé, étape par étape

```
1. Analyse client (docs/analyse/client/)             ← ce que veut le client, en langage métier
2. /analyse-feature → docs/analyse/features/NNN.md   ← règles, critères testables, contrat technique
      ├─ /sensitive-data (toujours)                  ← classe chaque donnée ; les données sensibles sont chiffrées
      └─ /owasp-check (si la feature touche la sécurité) ← exigences SEC-xx, critères AC-SEC-xx
3. /review-analyse → verdict OK / À corriger         ← relecture critique AVANT d'écrire une ligne de code
4. /write-tests  ║  /implement-feature               ← chacun de son côté, sans se voir
5. /run-tests                                        ← progression en direct dans la status line
6. /triage-failures (si rouge)                       ← verdict CODE / TEST / ANALYSE / INFRA → bon destinataire
7. /trace-check                                      ← chaque critère a-t-il un test ET du code ?
8. /mutation-test                                    ← les tests détectent-ils vraiment les défauts ? (PIT)
9. /commit                                           ← suite complète + secrets, SAST, dépendances, images
```

### Pourquoi ça marche

- **Les erreurs coûtent moins cher en amont** : une ambiguïté corrigée à l'étape 3 évite des allers-retours entre
  tests et code.
- **L'isolement est imposé, pas espéré** : un hook (`.claude/hooks/guard.sh`) refuse à `test-writer` la lecture de
  `src/main/` et à `code-writer` celle de `src/test/`.
- **Deux mesures de qualité objectives** : la traçabilité (tous les critères couverts) et le score de mutation
  (les tests échouent vraiment quand le code est faux).
- **La sécurité fait partie du processus** : données sensibles et exigences OWASP sont ajoutées à l'analyse, donc
  testées comme le reste ; le socle (OpenBao, TLS 1.3, refus par défaut) est décidé dans des ADR (`docs/adr/`).

Détail des skills, agents et règles d'isolement : section « Workflow IA » de [`CLAUDE.md`](CLAUDE.md).

## Prérequis

- Docker + Docker Compose v2 (aucun JDK nécessaire : build et tests tournent en conteneur)
- `fzf` (sélection interactive de `restore.sh`), `openssl` (certificat de test)
- Optionnel : JDK 25 pour travailler dans l'IDE hors conteneur

## Démarrage

```bash
cp .env.example .env        # puis changer DB_PASSWORD (et CORS_ALLOWED_ORIGINS pour la prod)
./start.sh                  # dev : db + api (hot reload, debug JDWP 5005)
./start.sh --seed           # dev + données de seed (seeders Java : package seed/, @Seeder)
./start.sh --test           # suite de tests complète dans docker/test (rapports : target/test-reports)
./start.sh --prod           # prod (docker-compose.prod.yml) : HTTPS, image durcie, OpenBao scellé
./start.sh --test-prod      # chaîne de prod en local : HAProxy → WAF ModSecurity/CRS → API (https://localhost)
./start.sh --fresh          # supprime la base avant de démarrer (confirmation)
./restore.sh                # restaure un dump de ./init_db (fzf) dans la base dev ; --prod pour la prod
```

### Dev

| Service | URL |
|---|---|
| API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |
| Debug JDWP | localhost:5005 |
| OpenBao (mode dev) | http://127.0.0.1:8200 — token : `BAO_DEV_ROOT_TOKEN_ID` de `.env.dev` |
| PostgreSQL | 127.0.0.1:5432 |

Secrets dev : `.env.dev` (créé automatiquement depuis `.env.dev.example` avec des valeurs aléatoires) est injecté dans
OpenBao par le service `openbao-init`, avec **le même script de configuration que la prod**. Recette :
`docker/openbao/check-phase1.sh`.

Comptes de démonstration (`./start.sh --seed`, dev uniquement) : `admin` (ADMIN), `gestionnaire` (FLEET_MANAGER +
DRIVER), `conducteur` (DRIVER), sans dépôt. Mot de passe commun : `SEED_DEMO_PASSWORD` de `.env.dev` (au moins
12 caractères ; à ajouter à un `.env.dev` créé avant la feature 001). Connexion : `POST /api/v1/auth/login`.

Hot reload : devtools redémarre l'application quand `target/classes` change → configurer l'IDE pour déléguer
le build à Maven, ou lancer `./mvnw compile`.

### Test local de la prod

`./start.sh --test-prod` démarre `docker-compose.prod.yml` + `docker-compose.test-prod.yml` : la même définition prod,
avec devant elle **HAProxy** (même `haproxy.cfg` qu'en prod) et un **WAF ModSecurity + OWASP CRS**, chaque saut en
TLS 1.3 vérifié par une PKI de test (`docker/test-prod/gen-certs.sh`). L'API n'est joignable que par cette chaîne.

```bash
curl --cacert secrets/test-prod/ca.crt https://localhost/api/v1/ping     # 401 (API, refus par défaut)
docker/test-prod/check.sh                                                 # recette : 22 contrôles
```

Premier lancement : OpenBao prod doit être amorcé (`docker/prod/openbao/bootstrap.sh`), comme en prod.
⚠ HSTS `preload` est envoyé pour `localhost` : préférer `curl` ou une fenêtre privée du navigateur.

### Prod

Internet → **HAProxy** (HTTPS public, Let's Encrypt via certbot) → TLS 1.3 re-chiffré → API sur `127.0.0.1:8443`.
Mise en place du serveur (groupe, dossiers, certbot, HAProxy) : [`docs/ops/DEPLOIEMENT-PROD.md`](docs/ops/DEPLOIEMENT-PROD.md).

| Port | Rôle |
|---|---|
| 8443 | HTTPS de l'API (TLS 1.3, HTTP/2), publié sur `127.0.0.1` pour HAProxy uniquement |
| 8081 | Actuator, lié à `127.0.0.1` **dans** le conteneur (healthcheck Docker), non publié |

Certificat : `tls.crt` + `tls.key` (PEM) dans `TLS_CERT_DIR`, monté en lecture seule, **rechargé à chaud** quand le
deploy hook certbot le remplace. En local : `docker/prod/gen-selfsigned-cert.sh` (proposé par `./start.sh --prod`).

Secrets : **OpenBao** (Raft, TLS interne, descellement Shamir 3/5, aucun token root permanent). Amorçage, descellement,
accès admin break-glass, rotations, sauvegardes : [`docs/ops/OPENBAO.md`](docs/ops/OPENBAO.md).

La prod **refuse de démarrer** si la sécurité est mal configurée (origine CORS non HTTPS ou avec joker, HSTS
désactivé, CSP avec `unsafe-*`…) : le message d'erreur liste les points à corriger.

## Tests

```bash
./start.sh --test                                   # tout, en conteneur
.claude/skills/run-tests/scripts/run-tests.sh --docker   # idem avec barre de progression
```

`*Test` : tests unitaires (Surefire) · `*IT` : intégration avec PostgreSQL 18 via Testcontainers (Failsafe).

## Documentation

- [`CLAUDE.md`](CLAUDE.md) — stack, conventions, sécurité, workflow IA (référence pour Claude Code et l'équipe)
- [`Next.md`](Next.md) — feuille de route (phases 1 à 6)
- [`docs/adr/`](docs/adr/) — décisions d'architecture (0001 : secrets / OpenBao)
- [`docs/ops/`](docs/ops/) — exploitation : déploiement prod (HAProxy, certbot), OpenBao
- [`tmp/`](tmp/) — specs de conception initiales (implémentées ; le code fait foi), kit de démarrage et prompts de la formation
- [`ExampleRefactorUpgrade/flotte/`](ExampleRefactorUpgrade/flotte/README.md) — exemple de migration legacy « bug pour bug »
- [`DemoMCP/`](DemoMCP/README.md) — exemple de serveur MCP, du premier outil à l'authentification
