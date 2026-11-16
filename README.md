# Formation — API

API REST monolithique (Java 25 · Spring Boot 4.1 · PostgreSQL 18 · Hibernate/JPA · Flyway · Lombok · MapStruct),
conteneurisée avec Docker en dev comme en prod. Le front (Angular) vit dans un dépôt séparé.

> **Statut : préparation.** Le code n'est pas encore généré. Les spécifications sont dans `./tmp/`.

## Prérequis

- Docker + Docker Compose v2
- `fzf` (pour `restore.sh`)
- JDK 25 + Maven *(facultatif : uniquement pour travailler hors conteneur / lancer les tests)*

## Démarrage rapide (une fois le projet implémenté)

```bash
cp .env.example .env        # puis adapter les valeurs
./start.sh                  # dev : db + api (hot reload, debug sur 5005)
./start.sh --seed           # dev avec données de seed
./start.sh --prod           # prod
./start.sh --test           # tests dans un conteneur (docker/test)
./restore.sh                # restaurer un dump depuis ./init_db (picker fzf)
```

| Service | URL |
|---|---|
| API | http://localhost:8080/api/v1 |
| Swagger UI (dev) | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |
| PostgreSQL | localhost:5432 |

## Documentation

- [`CLAUDE.md`](CLAUDE.md) — contexte, stack, conventions (référence pour Claude Code et l'équipe)
- [`tmp/01-STRUCTURE.md`](tmp/01-STRUCTURE.md) — arborescence, couches, conventions API
- [`tmp/02-DOCKER.md`](tmp/02-DOCKER.md) — docker-compose, `docker/dev`, `docker/prod`, `docker/test`
- [`tmp/03-SCRIPTS.md`](tmp/03-SCRIPTS.md) — `start.sh`, `restore.sh`

## Ordre de mise en place suggéré

1. Générer le squelette Spring Boot (`tmp/01-STRUCTURE.md`) : Lombok, MapStruct, `lombok.config`, `application*.yml`
2. `.env.example`, `docker/dev/`, `docker-compose.yml` (db + api-dev) → vérifier le démarrage
3. Socle `framework/` : `BaseEntity`, `GlobalExceptionHandler`, `MapStructConfig`, première migration `V1__init.sql`
4. `start.sh` (dev, puis `--seed`)
5. `restore.sh`
6. `docker/test/` + `start.sh --test`
7. `docker/prod/` + profil `prod`
8. Première entité de bout en bout (entity → repository → service → mapper → controller) + tests
