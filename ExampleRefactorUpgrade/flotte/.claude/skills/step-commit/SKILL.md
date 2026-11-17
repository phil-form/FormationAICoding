---
name: step-commit
description: Commit d'une marche de migration flotte (remplace le /commit Java du dépôt parent pour flotte/) - suite de tests complète, contrôle des diffs vulnérabilités / dépréciations / inventaire, fichiers interdits, git add ciblé, message conventionnel avec résumé des diffs. Ne pousse jamais ; tag d'étape seulement sur confirmation.
when_to_use: À la fin de chaque marche (appelée par les orchestrateurs), ou quand l'utilisateur demande de commiter dans flotte/.
argument-hint: "[step-NN] [intention du message]"
allowed-tools: Bash(.claude/bin/tests.sh:*), Bash(.claude/bin/junit-summary.py:*), Bash(.claude/bin/json-diff.py:*), Bash(.claude/bin/state.py:*), Bash(git status:*), Bash(git diff:*), Bash(git log:*), Bash(git add:*), Bash(git commit:*), Bash(git tag:*), Bash(git rev-parse:*)
---

# Commit d'étape (flotte)

Arguments : `$ARGUMENTS`. Étape par défaut : `STEP` de `docker/step.env`.

## Règles absolues

- **Aucune modification** de code ou de configuration pendant cette skill : on constate, on rapporte,
  l'utilisateur décide.
- Jamais `git push`, jamais `--no-verify`, jamais de réécriture d'historique (`--amend`, `rebase`, `reset`) sauf
  demande explicite.
- Fichiers **interdits** dans un commit : `.env` (et `.env.*` hors `.env.example` / `.env.dist`), `.ref/`,
  `.claude/state/`, `vendor/`, `tests/vendor/`, `app/cache/`, `var/`, `app/logs/`, tout `*.sqlite` **hors**
  `tests/fixtures/db/`, clés et certificats.
- Le dépôt git est le dépôt parent (`Formation/`) : n'indexer que des chemins sous `flotte/`.

## 1. Préparer

1. `git status --short -- .` et `git diff --stat -- .` (+ `--cached`).
2. Indexer avec `git add <chemins>` explicites (jamais `git add -A` ni `.`), uniquement ce qui relève de l'étape.
   Changements sans rapport → proposer des commits séparés.
3. Vérifier qu'aucun fichier interdit n'est indexé (`git diff --cached --name-only`) ; sinon arrêt et rapport.

## 2. Tester (toujours)

1. `.claude/bin/tests.sh --suite all`.
2. Rouge → **arrêt** : rapport des échecs (`junit-summary.py`) et proposition `/failure-triage`. Pas de commit.

## 3. Contrôler les diffs

Lire, pour l'étape :
- `docs/security/vulnerabilities.json` : vulnérabilités `introduced` (ou `accepted` échues) → arrêt sauf décision
  déjà consignée ;
- `docs/migration/deprecations.json` : `introduced` non arbitrées → arrêt ;
- inventaire : `.claude/bin/json-diff.py` étape précédente / étape courante par type → élément disparu ou modifié
  sans décision → arrêt ;
- `state.json` : alertes `G-*` non résolues → arrêt.

En cas d'arrêt : rapport et `AskUserQuestion` (corriger / accepter avec raison / commiter quand même avec la
mention dans le message / annuler).

## 4. Commiter

Message en anglais, format conventionnel :

```
chore(migration): step-NN symfony A.B -> C.D (php X.Y)

Tests: <n> passed (http/cli/db/edge/diff/unit)
Vulnerabilities: -<résolues> fixed, +<ajoutées> introduced, <ouvertes> open
Deprecations: -<résolues> fixed, +<ajoutées> introduced, <bloquantes> blocking for next step
Inventory: <inchangé | n changes, see docs/migration/steps/step-NN.md>
Accepted behavior changes: <n> (docs/migration/accepted-changes.md)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```

Type adapté hors marche (`test:`, `build:`, `docs:`…), toujours avec les lignes de résumé pertinentes et la
ligne `Co-Authored-By`. `git commit -F` avec un message préparé, puis `git log -1 --stat`.

## 5. Tag d'étape

Uniquement si la marche est terminée **et** que l'utilisateur confirme : `git tag flotte-migration/step-NN`.
Puis `state.py set last_green.step step-NN` et `state.py set last_green.commit <hash>`.

## Rapport final

Hash du commit, fichiers commités, résumé des tests et des diffs, tag créé ou non. Rappel : rien n'a été poussé.
