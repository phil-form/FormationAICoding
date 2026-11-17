---
name: test-run
description: Lance la suite de tests de la migration (boîte noire http/cli/db/edge, différentielle contre app-ref, unitaire) dans les conteneurs, avec progression, puis produit un résumé par critère de comportement BH-xxx et un retour exploitable par /failure-triage.
when_to_use: Après chaque modification du code ou de l'environnement, à chaque phase des orchestrateurs, avant /step-commit, ou pour vérifier une suite fraîchement écrite contre app-ref.
argument-hint: "[--suite http|cli|db|edge|diff|unit|all] [--filter <motif>] [--group BH-xxx] [--coverage] [--against ref|app]"
allowed-tools: Bash(.claude/bin/tests.sh:*), Bash(.claude/bin/junit-summary.py:*), Bash(.claude/bin/env.sh:*), Bash(.claude/bin/state.py:*)
---

# Exécution des tests

Arguments : `$ARGUMENTS`. Conventions : `.claude/reference/conventions.md` (§ 3, § 4, § 5.2). Plan : § 3.

## Règles

- Skill de **session principale** ; elle **constate**, ne corrige rien (ni tests, ni code, ni golden masters).
- Un échec n'est jamais « réglé » en relançant jusqu'à ce que ça passe : un test instable est signalé comme tel
  (anomalie de déterminisme → `/failure-triage`, verdict ENVIRONNEMENT ou TEST).
- Critère de passage d'une marche : `http`, `cli`, `db`, `edge` et `diff` **100 % verts** (plan § 3.5). `unit` est un
  complément : ses échecs sont rapportés mais classés à part.

## 1. Préparer

1. `.claude/bin/env.sh status` : `app`, `app-ref` (si `diff` ou `--against ref`) et `tests` sains, sinon
   `/env up --ref` ; persistance de l'échec → gate `G-ENV`.
2. Suite par défaut : `all`. `--against ref` exécute les suites boîte noire contre `app-ref` (sert à valider qu'une
   suite nouvellement écrite décrit bien step-00 : elle **doit** y être verte).

## 2. Lancer

1. `.claude/bin/tests.sh --suite <s> [--filter …] [--group …] [--coverage]` (exécution en arrière-plan si la
   suite est longue ; progression lue sur la sortie : tests faits / total, réussis, échoués, ignorés).
2. Ordre conseillé pour `all` : `db` → `cli` → `http` → `edge` → `diff` → `unit` (on arrête l'affichage détaillé au
   premier lot d'échecs d'infrastructure, mais on va au bout de la suite).
3. Les JUnit sont dans `.claude/state/migration/test-results/junit-<suite>.xml`.

## 3. Résumer

1. `.claude/bin/junit-summary.py .claude/state/migration/test-results/junit-*.xml --json` →
   `.claude/state/migration/test-results/summary.json` :
   ```json
   {
     "step": "step-NN", "at": "…", "suites": { "http": { "total": 0, "passed": 0, "failed": 0, "skipped": 0, "time_s": 0 } },
     "failures": [ { "bh": "BH-012", "suite": "http", "test": "…::bh012_…", "message": "…(tronqué)" } ],
     "untagged_failures": [],
     "green": false
   }
   ```
2. Regrouper les échecs par `BH-xxx` ; un test sans `#[Group('BH-…')]` est une anomalie de la suite (signalée).
3. Enregistrer le résultat : `state.py set last_test_run <green|red>` et `state.py push-event "test-run …"`.

## 4. Suite à donner

- **Vert** : l'indiquer ; si appelé par un orchestrateur, rendre la main.
- **Rouge** : proposer `/failure-triage` (jamais de correction directe). Échecs massifs et homogènes (connexion
  refusée, 502, base absente) → probable environnement : proposer `/env status` puis `/failure-triage`.
- `--against ref` rouge : la suite ne décrit pas step-00 → retour à `test-writer` via `/failure-triage`.

## Rapport final

Tableau par suite (total, ✔, ✘, ↷, durée), liste des `BH-xxx` en échec avec un message court, tests instables,
chemin de `summary.json`, prochaine action proposée.
