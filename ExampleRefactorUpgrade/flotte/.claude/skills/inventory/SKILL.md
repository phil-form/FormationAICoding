---
name: inventory
description: Cartographie l'application à l'étape courante (routes, commandes, services, formulaires, entités, templates, configuration) dans docs/migration/inventory/step-NN/, et compare avec l'étape précédente pour détecter tout élément disparu ou modifié.
when_to_use: Sur step-00 avant d'écrire les tests (/build-test-suite), puis avant et après chaque marche de migration (/upgrade-symfony, /upgrade-php) ; ou quand l'utilisateur demande « liste les routes / services / … ».
argument-hint: "[all|routes|commands|services|forms|entities|templates|config] [--snapshot|--diff]"
context: fork
agent: cartographer
---

# Inventaire de l'application

Arguments : `$ARGUMENTS` — type (`all` par défaut) et mode (`--snapshot` par défaut ; `--diff` = snapshot puis
comparaison).

Référence : `.claude/reference/conventions.md` (§ 2 arborescence, § 4 scripts, § 5.3 format, § 7 gate `G-INVENTORY`).

## Règles

- **Lecture seule** sur le code : tu n'écris que dans `docs/migration/inventory/`.
- Un sous-agent ne peut pas appeler d'autres skills : pour chaque type demandé, **lis**
  `.claude/skills/list-<type>/SKILL.md` et applique ses instructions toi-même, dans l'ordre :
  `config`, `routes`, `commands`, `services`, `forms`, `entities`, `templates`.
- Clés stables et tri stable (§ 5.3) : deux inventaires d'un même code doivent être **identiques à l'octet près**.
- Aucune valeur sensible dans l'inventaire (`secret`, mots de passe) : remplacer la valeur par `"<redacted>"` en
  gardant la clé.

## Étapes

1. Lis `docker/step.env` → `STEP`. Si absent, arrête-toi (`G-ENV`, proposer `/env up`).
2. `.claude/bin/env.sh status` : le conteneur `app` doit être sain (les `list-*` exécutent la console).
   Sinon, arrête-toi avec `G-ENV`.
3. Pour chaque type demandé : applique `list-<type>`, écris `docs/migration/inventory/<STEP>/<type>.json`.
4. Écris `docs/migration/inventory/<STEP>/summary.md` : nombre d'éléments par type, sources utilisées,
   éléments relevés par lecture seule (non confirmés par la console), points non résolus.
5. **Mode `--diff`** :
   1. Étape précédente = dernière entrée de `.claude/reference/steps.json` antérieure à `STEP` qui possède un dossier
      d'inventaire. Aucune → indique « pas de base de comparaison » et termine.
   2. Pour chaque type : `.claude/bin/json-diff.py <précédent>/<type>.json <courant>/<type>.json --key <clé § 5.3>`.
   3. Classe chaque écart :
      - **disparu** (clé absente) → alerte `G-INVENTORY` ;
      - **modifié** sur un champ observable (`path`, `methods`, `requirements`, `roles`, `options`, `fields`,
        `columns`, `blocks`, `variables`…) → alerte `G-INVENTORY` ;
      - **modifié** sur un champ attendu pour la marche (`controller`, `class`, `file`, `source`, `id` de service
        renommé avec alias conservé) → information ;
      - **ajouté** → information (ex. routes techniques du profiler, services d'autowiring).
   4. Écris `docs/migration/inventory/<STEP>/diff.md` (un tableau par type).
6. Rends ton rapport.

## Rapport final

- Fichiers écrits, nombre d'éléments par type.
- En `--diff` : alertes `G-INVENTORY` (élément, champ, avant → après), puis informations.
- Écarts avec le contrôle de complétude de chaque `list-*` (élément attendu absent).
