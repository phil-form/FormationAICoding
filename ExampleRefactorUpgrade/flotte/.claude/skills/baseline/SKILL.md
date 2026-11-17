---
name: baseline
description: Établit la référence step-00 (PHP 7.2 / Symfony 2.8) - environnement conteneurisé, composer.lock de référence, empreintes (schéma, inventaire, dépréciations, vulnérabilités), jeux de données, puis tag flotte-migration/step-00 après validation et génération de .ref/.
when_to_use: Une seule fois, avant /build-test-suite et toute montée de version ; ou pour reconstruire la référence si elle est perdue (avec confirmation).
argument-hint: "[--rebuild]"
allowed-tools: Bash(.claude/bin/env.sh:*), Bash(.claude/bin/composer.sh:*), Bash(.claude/bin/console.sh:*), Bash(.claude/bin/seed.sh:*), Bash(.claude/bin/state.py:*), Bash(git status:*), Bash(git log:*), Bash(git tag:*), Bash(git rev-parse:*), Bash(sha256sum:*)
---

# Baseline step-00

Arguments : `$ARGUMENTS`. Références : `conventions.md` (§ 1 à § 4), plan § 7 et § 9.

## Règles

- Session principale. Aucune modification du **code applicatif** (`src/`, `app/`, `web/`) : la référence est le code
  tel qu'il est.
- Si le tag `flotte-migration/step-00` existe déjà : s'arrêter, sauf `--rebuild` **et** confirmation explicite de
  l'utilisateur (le tag n'est jamais déplacé sans son accord).
- `docker/step.env` doit indiquer `STEP=step-00`, `PHP_VERSION=7.2`, `SYMFONY_VERSION=2.8`.

## Étapes

1. **Environnement**
   - Si `docker-compose.yml` ou les Dockerfiles de `conventions.md` § 2 manquent : skill `env` (`init`) pour les
     créer, puis faire relire l'utilisateur.
   - Skill `env` (`build`, puis `up`, puis `status`). Échec → arrêt (`G-ENV`).
2. **Lock de référence**
   - `.claude/bin/composer.sh config platform.php 7.2.24` (déjà présent dans `composer.json` : vérifier seulement).
   - `.claude/bin/composer.sh update --no-scripts --no-interaction` pour **générer** `composer.lock` (absent à
     l'origine), puis `.claude/bin/composer.sh install --no-scripts`.
   - Prévenir l'utilisateur : le lock est résolu aujourd'hui, pas en 2016 ; les versions transitives peuvent différer
     de la production. Lui demander s'il dispose du `vendor/` ou du lock de production pour comparaison.
   - Empreinte : `sha256sum composer.lock` → `state.py set baseline.lock_sha256 <hash>`.
3. **Application démarrée** — `.claude/bin/console.sh doctrine:schema:create` (base vide de l'étape), puis
   `.claude/bin/env.sh status` ; une requête de santé sur `/vehicules` doit répondre 200.
4. **Schéma** — capturer le schéma SQLite produit (sortie de `doctrine:schema:create --dump-sql`) dans
   `tests/fixtures/schema.sql` **seulement si le gel n'est pas actif** ; c'est la référence de non-dérive du schéma.
5. **Jeux de données** — skill `seed` (`all --target app --verify --rebuild`). Si les scénarios n'existent pas encore,
   le noter : ils seront créés par `/build-test-suite` (test-writer) et vérifiés ensuite.
6. **Empreintes initiales** — skills `inventory` (`all --snapshot`), `deprecation-check` (`--snapshot`),
   `vuln-audit` (`--snapshot`). Ces appels écrivent l'étape `step-00` dans `deprecations.json` et
   `vulnerabilities.json` (statut `open` pour tout constat initial).
7. **Validation** — présenter à l'utilisateur : versions installées (directes et transitives principales), hash du
   lock, nombre de routes/commandes/services, dépréciations, vulnérabilités par sévérité. Demander
   (`AskUserQuestion`) : valider la baseline / corriger / arrêter.
8. **Commit et tag** (après validation uniquement)
   - Skill `step-commit` (`step-00`) : commite `composer.lock`, l'environnement, les inventaires et rapports.
   - `git tag flotte-migration/step-00` sur ce commit, **après confirmation explicite**.
9. **Référence servie** — `.claude/bin/env.sh ref-sync flotte-migration/step-00` puis `.claude/bin/env.sh up --ref`
   et `status` : `app-ref` doit répondre sur le port 8072.
10. `state.py set last_green.step step-00` et `last_green.commit <hash>`.

## Rapport final

Environnement (images et digests), hash du `composer.lock`, résumé d'inventaire, dépréciations et vulnérabilités
initiales, tag créé, état de `app-ref`, points d'attention (lock reconstruit, scénarios manquants). Suite :
`/build-test-suite`.
