# 02 — Docker (spec à implémenter)

> ✅ **Implémenté** (socle du 16/11/2026). Spec conservée comme référence de conception : en cas d'écart, le code fait foi.

Chaque environnement a **son propre dossier** sous `docker/`, contenant son Dockerfile et
tous ses fichiers spécifiques. Le contexte de build reste la racine du projet.

```
docker/
├── dev/
│   ├── Dockerfile
│   ├── Dockerfile.dockerignore   # .dockerignore propre à ce Dockerfile (BuildKit)
│   └── entrypoint.sh             # lance spring-boot:run avec le debug JDWP
├── prod/
│   ├── Dockerfile
│   ├── Dockerfile.dockerignore
│   ├── entrypoint.sh             # exec java $JAVA_OPTS ... JarLauncher
│   └── healthcheck.sh            # appel /actuator/health
└── test/
    ├── Dockerfile
    ├── Dockerfile.dockerignore
    └── run-tests.sh              # ./mvnw verify + export des rapports
```

> BuildKit (activé par défaut avec Docker Compose v2) utilise automatiquement
> `<chemin-du-Dockerfile>.dockerignore` s'il existe : chaque environnement filtre son contexte
> indépendamment, pas besoin d'un `.dockerignore` global.

## Variables d'environnement (`.env.example`)

```dotenv
COMPOSE_PROJECT_NAME=formation

# Base de données
DB_NAME=formation
DB_USER=formation
DB_PASSWORD=change-me
DB_PORT=5432            # port exposé sur l'hôte

# API
API_PORT=8080
DEBUG_PORT=5005         # dev uniquement
SEED=false              # true => applique db/seed au démarrage (dev uniquement)

# CORS
CORS_ALLOWED_ORIGINS=http://localhost:4200
```

## docker-compose.yml

Un seul fichier, trois **profils Compose** :

| Service | Profil | Build | Rôle |
|---|---|---|---|
| `db` | toujours actif | image `postgres:18-alpine` | PostgreSQL 18 |
| `api-dev` | `dev` | `docker/dev/Dockerfile` | API, sources montées, devtools, debug |
| `api-prod` | `prod` | `docker/prod/Dockerfile` | API packagée (jar), JRE seule |
| `api-test` | `test` | `docker/test/Dockerfile` | Exécution de la suite de tests puis sortie |

### Service `db`

- `image: postgres:18-alpine`
- `environment` : `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` depuis `.env`.
- **Volume : `pgdata:/var/lib/postgresql`** ⚠️ Changement PostgreSQL 18 : l'image officielle stocke
  désormais les données dans `/var/lib/postgresql/18/docker` ; on monte donc le **dossier parent**
  `/var/lib/postgresql` (et non plus `/var/lib/postgresql/data` comme avant la v18).
- Bind mount `./init_db:/init_db:ro` → accès aux dumps pour `restore.sh`.
- `ports: "${DB_PORT}:5432"` (dev ; en prod, ne pas exposer ou limiter à `127.0.0.1`).
- `healthcheck` : `pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}` (interval 5s, retries 10).

### Service `api-dev` (profil `dev`)

- `build: { context: ., dockerfile: docker/dev/Dockerfile }`
- `depends_on: db: condition: service_healthy`
- `environment` : `SPRING_PROFILES_ACTIVE=dev`, `DB_HOST=db`, `DB_PORT=5432`, `DB_NAME`, `DB_USER`,
  `DB_PASSWORD`, `SEED`, `CORS_ALLOWED_ORIGINS`.
- Volumes :
  - `./:/app` (sources → hot reload devtools)
  - `m2cache:/root/.m2` (cache Maven persistant)
- Ports : `${API_PORT}:8080`, `${DEBUG_PORT}:5005`.

### Service `api-prod` (profil `prod`)

- `build: { context: ., dockerfile: docker/prod/Dockerfile }`
- `depends_on: db: condition: service_healthy`
- `environment` : `SPRING_PROFILES_ACTIVE=prod`, `DB_*`, `CORS_ALLOWED_ORIGINS`. **Pas de `SEED`.**
- Ports : `${API_PORT}:8080`.
- `restart: unless-stopped`
- `healthcheck` : `/app/healthcheck.sh`.

### Service `api-test` (profil `test`)

- `build: { context: ., dockerfile: docker/test/Dockerfile }`
- **Pas de dépendance à `db`** : les tests utilisent Testcontainers (base jetable `postgres:18-alpine`).
- Volumes :
  - `/var/run/docker.sock:/var/run/docker.sock` (Testcontainers pilote le Docker de l'hôte)
  - `m2cache:/root/.m2`
  - `./target/test-reports:/reports` (récupération des rapports Surefire/Failsafe)
- `environment` :
  - `SPRING_PROFILES_ACTIVE=test`
  - `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`
- `extra_hosts: ["host.docker.internal:host-gateway"]` (Linux)
- Le conteneur s'arrête à la fin des tests ; son code de sortie = résultat des tests.

### Volumes nommés

`pgdata`, `m2cache`.

### Utilisation

```bash
docker compose --profile dev  up -d --build
docker compose --profile prod up -d --build
docker compose --profile test run --rm --build api-test
docker compose --profile dev down
```

## docker/dev/

**Dockerfile** — itération rapide, pas d'optimisation de taille.

- Base : `maven:3.9-eclipse-temurin-25` (JDK complet + Maven).
- `WORKDIR /app`
- Copier `pom.xml` + `.mvn/` + `mvnw` puis `./mvnw dependency:go-offline` (cache de couche).
- Les sources sont montées par volume (pas de `COPY src`).
- Copier `docker/dev/entrypoint.sh` → `/entrypoint.sh`.
- `EXPOSE 8080 5005`, `ENTRYPOINT ["/entrypoint.sh"]`.

**entrypoint.sh**

```
exec ./mvnw spring-boot:run \
  -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
```

DevTools redémarre l'application quand les classes compilées changent (compiler depuis l'IDE
ou activer `spring.devtools.restart.poll-interval` si les événements fichiers ne traversent pas le volume).

**Dockerfile.dockerignore** : `target/`, `.git/`, `.idea/`, `init_db/`, `tmp/`, `.env`.

## docker/prod/

**Dockerfile** — multi-stage, image finale minimale et non-root.

1. **Stage `build`** — `maven:3.9-eclipse-temurin-25`
   - Copier `pom.xml`, `.mvn/`, `mvnw` → `./mvnw dependency:go-offline`
   - Copier `src/` (+ `lombok.config`) → `./mvnw -B clean package -DskipTests`
   - Extraire les couches : `java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination extracted`
2. **Stage `runtime`** — `eclipse-temurin:25-jre-alpine`
   - Créer un utilisateur `spring` non-root, `USER spring`
   - Copier les couches dans l'ordre : `dependencies`, `spring-boot-loader`,
     `snapshot-dependencies`, `application` (meilleur cache Docker)
   - Copier `docker/prod/entrypoint.sh` et `docker/prod/healthcheck.sh`
   - `ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"`
   - `EXPOSE 8080`, `ENTRYPOINT ["/app/entrypoint.sh"]`
   - `HEALTHCHECK CMD /app/healthcheck.sh`

**entrypoint.sh** : `exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher "$@"`

**healthcheck.sh** : `wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"'`

**Dockerfile.dockerignore** : `target/`, `.git/`, `.idea/`, `init_db/`, `tmp/`, `docker/dev/`, `docker/test/`,
`.env`, `*.md`, `src/test/`.

> Les tests ne tournent pas dans le build prod : ils passent via `docker/test/` (ou en CI) avant.

## docker/test/

**Dockerfile**

- Base : `maven:3.9-eclipse-temurin-25`
- `WORKDIR /app`
- Copier `pom.xml`, `.mvn/`, `mvnw` → `./mvnw dependency:go-offline`
- Copier `src/` + `lombok.config` (image autonome, pas de bind mount des sources : résultat reproductible)
- Copier `docker/test/run-tests.sh` → `/run-tests.sh`, `ENTRYPOINT ["/run-tests.sh"]`

**run-tests.sh**

1. `./mvnw -B verify` (tests unitaires Surefire + intégration Failsafe `*IT`)
2. Copier `target/surefire-reports` et `target/failsafe-reports` vers `/reports` (même en cas d'échec)
3. Sortir avec le code de retour de Maven

**Dockerfile.dockerignore** : `target/`, `.git/`, `.idea/`, `init_db/`, `tmp/`, `docker/dev/`, `docker/prod/`, `.env`.
