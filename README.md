# Formation — API

API REST monolithique : Java 25 · Spring Boot 4.1 · Spring Security 7 · PostgreSQL 18 · Hibernate/JPA · Flyway ·
Lombok · MapStruct. Conteneurisée en dev comme en prod. Le front (Angular) vit dans un dépôt séparé.

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
- [`tmp/`](tmp/) — specs de conception initiales (implémentées ; le code fait foi)
