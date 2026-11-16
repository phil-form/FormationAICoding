# 03 — Scripts (spec à implémenter)

> ✅ **Implémenté** (socle du 16/11/2026). Spec conservée comme référence de conception : en cas d'écart, le code fait foi.

Deux scripts Bash à la racine, exécutables (`chmod +x`), avec `set -euo pipefail`,
un `--help`, des messages colorés et des codes de sortie explicites.
Tous deux chargent `.env` (et échouent proprement s'il est absent, en suggérant `cp .env.example .env`).

---

## start.sh

Démarre le projet via Docker Compose.

### Usage

```
./start.sh [options]

  --dev           (défaut) démarre db + api-dev
  --prod          démarre db + api-prod
  --test          lance api-test (suite de tests), rapports dans target/test-reports, puis sort
  --seed          applique les données de seed au démarrage (dev uniquement)
  --build         force la reconstruction des images
  --fresh         supprime le volume pgdata avant de démarrer (demande confirmation)
  --logs          suit les logs de l'API après démarrage
  -h, --help      affiche l'aide
```

### Comportement

1. Vérifier les prérequis : `docker`, `docker compose` (v2), fichier `.env`.
2. Parser les options ; refuser `--seed` combiné à `--prod` ou `--test` (erreur explicite).
   `--test` : `docker compose --profile test run --rm [--build] api-test`, afficher le résultat et
   sortir avec le code de retour des tests (les étapes suivantes ne s'appliquent pas).
3. `--fresh` : confirmation interactive (`[y/N]`), puis `docker compose down -v` sur le volume `pgdata`.
4. Exporter `SEED=true|false` et lancer :
   `docker compose --profile <dev|prod> up -d [--build]`
5. Attendre que `db` soit `healthy` puis que l'API réponde sur `/actuator/health`
   (timeout configurable, ex. 120 s), avec un indicateur de progression.
6. Afficher un récapitulatif : URL de l'API, Swagger UI (dev), port debug (dev), état du seed.
7. `--logs` : `docker compose logs -f api-<profil>`.

### Mécanisme de seed

> ⚠ **Remplacé** : le seed est désormais écrit en Java (`@Seeder` / `DataSeeder`, voir CLAUDE.md). Ce qui suit est historique.

- Les scripts de seed sont des migrations **répétables** Flyway : `src/main/resources/db/seed/R__seed_*.sql`.
- Quand `SEED=true`, le profil `dev` ajoute `classpath:db/seed` à `spring.flyway.locations`
  → Flyway applique le seed **après** les migrations versionnées, au démarrage de l'API.
- Les scripts de seed doivent être **idempotents** (`INSERT ... ON CONFLICT DO NOTHING`, ou
  `TRUNCATE` préalable des tables concernées).
- Un script répétable est réappliqué automatiquement lorsque son contenu change.

---

## restore.sh

Restaure un backup présent dans `./init_db/` dans la base PostgreSQL du conteneur `db`,
avec sélection interactive via **fzf**.

### Usage

```
./restore.sh [fichier]     # sans argument : picker fzf
  -y, --yes                # pas de confirmation (usage scripté)
  -h, --help
```

### Formats supportés

| Extension | Outil de restauration |
|---|---|
| `.sql` | `psql` |
| `.sql.gz` | `gunzip -c` → `psql` |
| `.dump` / `.backup` (format custom `pg_dump -Fc`) | `pg_restore --no-owner --no-acl` |

### Comportement

1. Vérifier les prérequis : `docker`, `fzf` (message d'installation si absent :
   `sudo apt install fzf`), `.env`, présence d'au moins un fichier dans `./init_db/`.
2. S'assurer que le service `db` tourne et est `healthy` (sinon le démarrer seul :
   `docker compose up -d db` et attendre).
3. Sélection du fichier :
   - liste triée par date (plus récent en premier) avec taille et date ;
   - `fzf --prompt="Backup à restaurer > " --height=40% --reverse`
   - `--preview` : métadonnées du fichier + `head -n 50` pour `.sql`, `zcat | head` pour `.sql.gz`,
     `pg_restore -l` (via le conteneur) pour `.dump` ;
   - annulation (Échap) → sortie propre, code 130.
4. Confirmation explicite : *« La base `<DB_NAME>` va être écrasée. Continuer ? [y/N] »*.
5. Arrêter l'API (`api-dev` / `api-prod` si en cours) pour libérer les connexions.
6. Recréer la base :
   `DROP DATABASE IF EXISTS <db> WITH (FORCE); CREATE DATABASE <db> OWNER <user>;`
   (exécuté sur la base `postgres` via `docker compose exec -T db psql`).
7. Restaurer selon le format (le fichier est lu depuis le bind mount `/init_db` du conteneur).
   Pour `psql` : `-v ON_ERROR_STOP=1` ; arrêt et message clair en cas d'erreur.
8. Relancer l'API si elle tournait avant. Rappeler que Flyway validera l'historique
   (`flyway_schema_history`) au démarrage : le dump doit correspondre aux migrations du code.
9. Afficher un récapitulatif (fichier restauré, durée).

### Bonus (optionnel)

`backup.sh` symétrique : `pg_dump -Fc` vers `./init_db/<db>_<YYYYMMDD-HHMMSS>.dump`.
