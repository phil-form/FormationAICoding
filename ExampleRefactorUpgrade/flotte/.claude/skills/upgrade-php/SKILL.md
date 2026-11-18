---
name: upgrade-php
description: Orchestrateur - monte la version de PHP de l'application marche par marche (étapes kind php de steps.json) jusqu'à la cible (8.4 par défaut) - analyse de compatibilité, dépréciations, correction, changement d'image, tests, contrôles de sécurité, rapport et commit.
when_to_use: Seul pour une montée de PHP, ou appelé par /upgrade-symfony quand une version de Symfony exige un PHP minimal plus récent.
argument-hint: "[<cible, ex. 8.4>] [--until step-NN] [--auto|--interactive] [--dry-run] [--resume]"
allowed-tools: Bash(.claude/bin/state.py:*), Bash(.claude/bin/track-merge.py:*), Bash(.claude/bin/env.sh:*), Bash(.claude/bin/tests.sh:*), Bash(.claude/bin/junit-summary.py:*), Bash(git status:*), Bash(git log:*), Bash(git diff:*)
---

# Orchestrateur : montée de PHP

Arguments : `$ARGUMENTS`. Cible par défaut : `target.php` de `.claude/reference/steps.json`. Mode par défaut :
`--interactive` (ou celui de l'orchestrateur appelant).

Références : `.claude/reference/conventions.md` (§ 6 état, § 7 gates), `steps.json`, plan § 1.1.

## Règles

- Session principale ; chaque étape = invocation de la skill correspondante via l'outil **Skill**.
- Une marche = une étape `kind: php` de `steps.json`. Jamais de saut (7.2 → 7.4 → 8.1 → 8.4).
- Gel actif pendant la marche (`state.py freeze on`).
- Les gates `G-*` arrêtent la marche même en `--auto`.
- Si appelé par `/upgrade-symfony` : ne pas écraser `orchestrator` dans l'état ; écrire `sub_orchestrator:
  upgrade-php` et rendre la main à la fin sans lever le gel de l'appelant.

## 0. Initialisation

1. Lire `steps.json`, `docker/step.env`, `state.json`.
2. `--resume` : reprendre à `current_step` / `phase`. Sinon initialiser l'état (orchestrateur, cible, mode).
3. Marches restantes : étapes `kind: php` après l'étape courante, jusqu'à la cible ou `--until`. Une marche PHP ne
   doit pas dépasser ce que supporte la version Symfony courante (ex. pas de PHP 8.1 avec Symfony 4.4 si la
   compatibilité n'est pas assurée) : sinon le signaler et s'arrêter.
4. `--dry-run` : afficher les marches, les points de vigilance (`notes` de `steps.json`, pièges du plan § 0) et
   s'arrêter. En `--interactive`, faire valider.

## Pour chaque marche PHP `step-NN` (version `X.Y`)

1. **Pré-requis** (`prereq`) — skill `env` (`up`, `status`) → `G-ENV` si KO ; skill `test-run` (`--suite all`) vert
   obligatoire → sinon `G-TESTS`. `state.py set current_step step-NN`, `state.py freeze on`.
2. **Analyse** (`analyse`) — skills `compat-scan` (`X.Y`) et `deprecation-check` (`--php X.Y --snapshot`),
   `vuln-audit` (`--snapshot`).
3. **Nettoyage** (`fix-deprecations`) — tant que des incompatibilités ou dépréciations bloquantes pour `X.Y`
   subsistent : skill `fix-deprecations` (`--php X.Y`), puis `test-run`. Rouge → `failure-triage` → `--fix`,
   `G-BEHAVIOR`, `G-ENV` ; borne `max_fix_iterations` → `G-TESTS`. Incorrigible → `G-DEPREC`.
4. **Montée** (`upgrade`) — skill `upgrade-step` (`php X.Y`) : image, `config.platform.php`, `docker/step.env`.
   Puis skill `env` (`build`, `up`, `status`).
5. **Vérification** (`verify`) — skill `test-run` (`--suite all`, différentiel compris). Rouge → `failure-triage`
   puis `upgrade-step php X.Y --fix` / `fix-deprecations --fix`, borne `max_fix_iterations`.
   Points d'attention : comparaisons chaîne/nombre, tri stable, `serialize_precision`, `null` vers fonctions
   internes (cf. plan § 0) — un écart observé sur ces points est presque toujours un `BEHAVIOR-CHANGE` à arbitrer.
6. **Contrôles** (`controls`) — `deprecation-check --diff`, `vuln-audit --diff` (runtime PHP et image compris),
   `inventory all --diff` → gates `G-DEPREC`, `G-VULN`, `G-INVENTORY`.
7. **Bilan** (`report`) — skill `step-report` (`step-NN`).
8. **Passage** (`gate`) — validation utilisateur en `--interactive` ou si alerte.
9. **Commit** (`commit`) — skill `step-commit` (`step-NN`). `state.py freeze off` (sauf appel par
   `/upgrade-symfony`), `last_green` mis à jour.

## Gate levée

Même procédure que `/upgrade-symfony` (section « Gate levée ») : alerte dans l'état, résumé, `AskUserQuestion`
(Corriger / Accepter avec raison / Reporter / Arrêter), consignation de l'acceptation (`accepted-changes.md` via
l'auditeur, ou `track-merge.py --accept`), puis `state.py alerts-clear`.

## Rapport final

Marches PHP réalisées, images utilisées, gates et décisions, diffs vulnérabilités (dont CVE PHP runtime résolues),
dépréciations, commits, commande de reprise si interrompu.
