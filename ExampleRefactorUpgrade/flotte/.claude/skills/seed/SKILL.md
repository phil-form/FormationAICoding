---
name: seed
description: Charge un jeu de données déterministe (scénario de tests/fixtures) dans la base de app et/ou app-ref, vérifie son empreinte, et reconstruit au besoin les bases SQLite de référence - sans jamais passer par le code de l'application.
when_to_use: Avant une session de caractérisation ou de tests ; pour vérifier que les bases de référence n'ont pas dérivé ; après l'écriture ou la correction d'un scénario par /write-db-tests.
argument-hint: "<scenario|all> [--target app|ref|both] [--verify] [--rebuild]"
allowed-tools: Bash(.claude/bin/seed.sh:*), Bash(.claude/bin/state.py:*), Bash(.claude/bin/env.sh:*)
---

# Jeu de données de référence

Arguments : `$ARGUMENTS`. Conventions : `.claude/reference/conventions.md` (§ 2, § 6). Plan : § 6.

## Qui crée quoi

| Élément | Auteur | Skill |
|---|---|---|
| Liste des scénarios et leur rôle (`empty`, `demo`, `nominal`, `edge`, `report-months`, `volume`) | `characterizer` | `/test-plan` |
| `tests/fixtures/<scenario>.yaml`, `tests/fixtures/README.md` (scénario ↔ `BH-xxx`) | `test-writer` | `/write-db-tests` |
| `tests/bin/seed.php` (chargeur PDO direct, neutre vis-à-vis de la version) | `test-writer` | `/write-db-tests` |
| `tests/fixtures/db/<scenario>.sqlite` + `.sha256` (bases figées) | **cette skill** (`--rebuild`) | `/seed` |
| Chargement dans les volumes `db-app` / `db-ref` | **cette skill** | `/seed`, `/env reset` |

Si `tests/fixtures/` ou `tests/bin/seed.php` n'existe pas : **ne rien inventer**. Indiquer que les scénarios sont à
écrire par `/write-db-tests` (après `/test-plan`) et s'arrêter.

## Règles

- Le seed **n'utilise jamais** le code de l'application (ni `fleet:demo:load`, ni entités, ni Doctrine) : il écrit
  directement dans le schéma SQLite capturé en step-00. `fleet:demo:load` est un comportement testé, pas un seed.
- Déterminisme : identifiants explicites, dates absolues, graine fixe pour `volume`. Deux chargements du même
  scénario produisent **la même empreinte**.
- **Gel** : si `state.py get frozen` vaut `true`, `--rebuild` est **refusé** (gate `G-FROZEN`) : les bases de
  référence font partie des zones gelées. Seul le chargement (`seed.sh <s>`) et la vérification (`--verify`) restent
  possibles. Le dégel relève de l'utilisateur (`accepted-changes.md` puis `state.py freeze off`).
- Ne jamais modifier un `.yaml` de scénario : signaler l'anomalie et proposer `/write-db-tests --fix`.

## 1. Préparer

1. `.claude/bin/env.sh status` : les conteneurs `tests` et la cible (`app` / `app-ref`) doivent être sains, sinon
   proposer `/env up --ref` (gate `G-ENV` si l'échec persiste).
2. Lister les scénarios disponibles (`tests/fixtures/*.yaml`) ; `all` = tous sauf `volume` (à demander explicitement).

## 2. Reconstruire les bases figées (`--rebuild`)

1. Vérifier le gel (ci-dessus).
2. `.claude/bin/seed.sh <scenario> --rebuild` : crée une base vide à partir du schéma de référence
   (`tests/fixtures/schema.sql`, capturé en step-00 par `/baseline`), charge le YAML, écrit
   `fixtures/db/<scenario>.sqlite` et son `.sha256` (empreinte du **contenu logique** : dump SQL trié, pas du fichier
   binaire, pour rester stable entre versions de SQLite).
3. Comparer avec l'empreinte précédente si elle existait : toute différence est signalée (changement de scénario non
   annoncé = anomalie).

## 3. Charger

`.claude/bin/seed.sh <scenario|all> --target <app|ref|both>` (défaut `both`) : copie la base figée dans le volume,
vide le cache applicatif, puis enregistre le scénario chargé (`state.py set scenario <s>`).

## 4. Vérifier (`--verify`, aussi exécuté après chaque chargement)

1. Recalculer l'empreinte logique de la base chargée dans chaque cible et la comparer au `.sha256`.
2. Vérifier que le schéma chargé est identique à `tests/fixtures/schema.sql` (tables, colonnes, types, index).
3. Écart → échec explicite : base dérivée, schéma modifié par la migration (à arbitrer par `/failure-triage`) ou
   scénario modifié.

## Rapport final

Tableau : scénario, cible, nombre de lignes par table, empreinte attendue / obtenue, statut. Puis anomalies et
action proposée (`/write-db-tests --fix`, `/failure-triage`, `/env reset`).
