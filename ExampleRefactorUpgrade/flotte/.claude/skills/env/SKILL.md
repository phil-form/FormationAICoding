---
name: env
description: Pilote l'environnement conteneurisé de la migration (app, app-ref, tests, tools, trivy) - création initiale de l'infrastructure Docker, construction, démarrage, arrêt, état de santé, remise à zéro de la base et resynchronisation du code de référence step-00.
when_to_use: Avant toute skill qui exécute l'application ou les tests ; à la première utilisation (init) ; après un changement d'étape (build) ; quand un conteneur n'est pas sain ; pour repartir d'une base propre (reset).
argument-hint: "init | up | down | build | status | reset [--scenario <s>] | ref-sync <tag> [--ref]"
allowed-tools: Bash(.claude/bin/env.sh:*), Bash(.claude/bin/console.sh:*), Bash(.claude/bin/state.py:*), Bash(docker image inspect:*), Bash(docker buildx imagetools inspect:*), Bash(git rev-parse:*), Bash(git tag:*)
---

# Environnement conteneurisé

Arguments : `$ARGUMENTS`. Conventions : `.claude/reference/conventions.md` (§ 2 arborescence, § 3 services, § 7 gates).
Plan : `tmp/plan-migration-php84-sf64.md` § 7.

## Règles

- Skill de **session principale** (pas d'agent) : elle est appelée par les orchestrateurs et par l'utilisateur.
- C'est la **seule** skill, avec l'agent `migrator`, autorisée à écrire `docker-compose.yml` et `docker/`. Elle ne
  touche jamais `src/`, `app/`, `tests/` (hors création de dossiers vides au `init`) ni `docs/`.
- Le service `app-ref` sert **toujours** le code step-00 (`.ref/`, monté en lecture seule) : on ne le modifie jamais à
  la main, seulement via `ref-sync`.
- Aucune valeur secrète en dur : le `secret` Symfony et le compte `gestionnaire` sont des valeurs de **test**,
  passées par variables d'environnement (`docker/step.env` ou `docker/test.env`), jamais dans une image.
- Toute indisponibilité (image non construite, conteneur non sain après le délai, port occupé) lève la gate
  **`G-ENV`** : `state.py alert G-ENV "<texte>"`, arrêt et compte rendu à l'utilisateur ; ne jamais « contourner » en
  désactivant un healthcheck.

## 1. `init` — créer l'infrastructure (une seule fois)

Si `docker-compose.yml` **et** `docker/` existent déjà : ne rien écraser, passer à `status` et signaler les écarts
avec les conventions. Sinon, créer :

1. **`docker/step.env`** (versionné) : `STEP=step-00`, `PHP_VERSION=7.2`, `SYMFONY_VERSION=2.8`,
   `FAKETIME="2026-01-15 10:00:00"`, `TZ=Europe/Brussels`, `LC_ALL=C.UTF-8`.
2. **`docker/app/php.ini`** commun : `date.timezone=Europe/Brussels`, `precision=14`, `serialize_precision=-1`
   (à confirmer contre la valeur effective de step-00, cf. § 4), `memory_limit=512M`, `error_reporting=E_ALL`,
   `display_errors=0`, `log_errors=1`, `error_log=/var/log/php/error.log`. Les `E_DEPRECATED`/`E_USER_DEPRECATED`
   sont redirigés vers `/var/log/php/deprecations.log` (handler d'erreurs chargé par `auto_prepend_file`, script
   versionné `docker/app/deprecation-logger.php`, qui **journalise sans modifier le comportement** : il renvoie
   `false` pour laisser le traitement standard).
3. **`docker/app/php72|php74|php81|php84/Dockerfile`** — un par famille de PHP :
   - image de base officielle Apache (`php:<x.y>-apache`) ;
   - extensions : `pdo_sqlite`, `intl`, `pcov` (désactivé par défaut, activé par `PCOV_ENABLED=1`) ;
   - **libfaketime** (paquet distribution, `LD_PRELOAD` + `FAKETIME` lus depuis l'environnement) ;
   - Composer : version **2.2 LTS** pour PHP 7.2 (compatible), 2.x récente au-delà ;
   - `DocumentRoot` paramétré (`web/` jusqu'à step-01, `public/` à partir de step-02) via `APACHE_DOCUMENT_ROOT`,
     front controller (`app.php` / `index.php`) en environnement `prod` sans debug, sauf si `APP_DEBUG=1` ;
   - utilisateur non root pour Apache, `HEALTHCHECK` sur `/vehicules` (code 200 attendu).
4. **`docker/tests/Dockerfile`** : PHP 8.4 CLI, `pdo_sqlite`, extensions nécessaires à PHPUnit 11, Composer récent.
5. **`docker/tools/Dockerfile`** : PHP 8.4 CLI + Composer 2.8+ (pour `composer audit --locked`), Rector, PHPStan
   (+ `phpstan-deprecation-rules`, `phpstan-symfony`, `phpstan-doctrine`), PHP_CodeSniffer + PHPCompatibility,
   Infection, `osv-scanner` (binaire). Outils installés **dans l'image** (`/opt/tools`), jamais dans le
   `composer.json` de l'application.
6. **`docker-compose.yml`** (projet `flotte-mig`) conforme au § 3 des conventions :
   - `app` : build `docker/app/php${PHP_VERSION/./}` (choisi par `env.sh build` depuis `docker/step.env`), port
     `8080`, monte `./` en rw, volume `db-app` sur `/var/app-data`, `database_path` pointé vers
     `/var/app-data/flotte.sqlite` ;
   - `app-ref` : image php72, port `8072`, monte `./.ref` en **ro**, volume `db-ref`, mêmes variables ;
   - `tests` : `docker/tests`, monte `./tests`, volumes `db-app:/data/app`, `db-ref:/data/ref`,
     `APP_URL=http://app`, `REF_URL=http://app-ref`, `depends_on` avec `condition: service_healthy` ;
   - `tools` : `docker/tools`, monte `./` ;
   - `trivy` : `aquasec/trivy`, socket Docker en lecture seule, cache dans un volume ;
   - logs PHP de `app` et `app-ref` dans des volumes nommés (`logs-app`, `logs-ref`) lisibles par `tools` ;
   - ports publiés sur `127.0.0.1` uniquement.
7. **Épinglage par digest** : construire une fois, résoudre chaque image de base
   (`docker buildx imagetools inspect <image>:<tag>`) et remplacer `FROM image:tag` par `FROM image:tag@sha256:…`.
   Consigner les digests dans `docker/IMAGES.md` (image, tag, digest, date de résolution).
8. **`.gitignore`** de `flotte/` : ajouter `/.ref/` et `/.claude/state/` s'ils n'y sont pas.
9. Créer les dossiers vides attendus s'ils manquent (`tests/`, `docs/migration/`, `docs/security/`) avec un
   `.gitkeep` — **sans** y écrire de contenu (c'est le rôle des autres skills).
10. Enchaîner sur `build` puis `up`, puis `ref-sync` si le tag `flotte-migration/step-00` existe ; sinon indiquer que
    `.ref/` sera créé par `/baseline`.

## 2. `build`

1. Lire `docker/step.env` ; vérifier que le Dockerfile de la famille PHP de l'étape existe (sinon **G-ENV** et
   proposer `/php-step`).
2. `.claude/bin/env.sh build` ; vérifier que chaque image référencée par digest est bien celle construite
   (`docker image inspect`).

## 3. `up` / `down`

1. `.claude/bin/env.sh up [--ref]` (`--ref` : démarre aussi `app-ref`). Attendre les healthchecks (délai 120 s).
2. Contrôles après démarrage :
   - `.claude/bin/console.sh --version` et `.claude/bin/console.sh --ref --version` : versions de Symfony attendues
     (`SYMFONY_VERSION` pour `app`, 2.8 pour `app-ref`) ;
   - la version de PHP de chaque conteneur correspond à `docker/step.env` ;
   - l'horloge est figée : la date vue par PHP correspond à `FAKETIME`.
3. Tout écart → **G-ENV**.
4. `down` : `.claude/bin/env.sh down` (les volumes sont conservés ; `reset` les restaure).

## 4. `status`

`.claude/bin/env.sh status` puis tableau : service, image (+ digest), version PHP, version Symfony, santé, port,
étape (`STEP`), date figée, scénario de base chargé (lu dans `.claude/state/migration/state.json` si présent).
Signaler les écarts avec les conventions (port, montage, absence de `FAKETIME`, `serialize_precision`).

## 5. `reset [--scenario <s>] [--ref]`

`.claude/bin/env.sh reset --scenario <s>` (défaut : `demo`) : restaure `fixtures/db/<s>.sqlite` dans le volume de
`app` (et `app-ref` avec `--ref`), vide les caches Symfony, vérifie l'empreinte (`seed.sh <s> --verify`).
Si `tests/fixtures/db/<s>.sqlite` n'existe pas : renvoyer vers `/seed`.

## 6. `ref-sync <tag>`

1. Vérifier que le tag existe (`git tag --list <tag>`) ; par défaut `flotte-migration/step-00`.
2. `.claude/bin/env.sh ref-sync <tag>` : régénère `.ref/` par `git archive` du sous-dossier `flotte/` au tag, puis
   `composer install` depuis le `composer.lock` du tag dans `app-ref`.
3. Redémarrer `app-ref` et vérifier sa santé. Refuser tout autre tag que step-00 sans confirmation explicite de
   l'utilisateur (la référence ne doit pas dériver).

## Rapport final

- Action exécutée, services et santé, versions PHP/Symfony par service, digests, date figée.
- Fichiers créés ou modifiés (`init` uniquement).
- Gate `G-ENV` éventuelle avec cause et action proposée.
