---
name: build-test-suite
description: Orchestrateur - construit la suite de caractérisation « bug pour bug » sur step-00 - inventaire, plan de tests, fiches BH, écriture des tests (HTTP, CLI, base, cas limites, différentiels, unitaires), golden masters, exécution, couverture et mutation testing, en boucle jusqu'aux seuils.
when_to_use: Après /baseline, avant toute montée de version ; ou pour compléter la suite sur une zone (--zone) après détection de lacunes.
argument-hint: "[--zone <zone>] [--until-thresholds] [--coverage <min %>] [--msi <min %>] [--max-rounds <n>] [--resume]"
allowed-tools: Bash(.claude/bin/state.py:*), Bash(.claude/bin/env.sh:*), Bash(.claude/bin/seed.sh:*), Bash(.claude/bin/tests.sh:*), Bash(.claude/bin/junit-summary.py:*)
---

# Orchestrateur : construction de la suite de tests

Arguments : `$ARGUMENTS`. Seuils par défaut : couverture lignes `src/` ≥ 95 %, MSI Infection ≥ 85 %, 5 tours
maximum (`--max-rounds`).

Références : `conventions.md` (§ 2 arborescence, § 5.1 fiches BH, § 5.2 tests), plan § 3 et § 6.

## Règles

- Session principale ; chaque étape = invocation de la skill correspondante via l'outil **Skill**.
- Ne s'exécute que sur **step-00** (`docker/step.env`) : la suite décrit la référence. Sur une autre étape,
  seul `--zone` est admis, et les tests produits doivent aussi passer contre `app-ref`.
- Le gel ne doit **pas** être actif (`state.py get frozen`) : sinon s'arrêter et le signaler.
- Les tests sont écrits par `test-writer` à partir des fiches `BH-xxx` uniquement ; aucune correction du code de
  l'application n'est faite ici.
- Un test qui échoue contre `app-ref` est un **test faux** (la référence fait foi) : retour à `test-writer`.

## Déroulé

1. **Environnement** — `state.py set orchestrator build-test-suite` ; skill `env` (`up --ref`, `status`) ;
   skill `seed` (`all --target both --verify`). KO → `G-ENV`.
2. **Inventaire** — skill `inventory` (`all --snapshot`) si l'inventaire de l'étape n'existe pas.
3. **Plan** — skill `test-plan` (`--zone` éventuel). Lire `docs/migration/test-plan.md` : liste des zones.
4. **Caractérisation** — pour chaque zone sans fiche (ou `--zone`) : skill `characterize <zone>`. Les zones sont
   indépendantes : les invoquer successivement, en relisant chaque rapport.
5. **Écriture des tests** — selon la couche indiquée dans les fiches :
   `write-http-tests`, `write-cli-tests`, `write-db-tests`, `write-edge-tests`, `write-unit-tests`,
   puis `write-diff-tests` (rejoue les parcours HTTP/CLI sur `app` et `app-ref`).
6. **Golden masters** — skill `golden-master --record`.
7. **Exécution** — skill `test-run` (`--suite all`). Tout échec contre la référence → feedback
   `.claude/state/migration/feedback-tests.md` (en termes de `BH-xxx`, via `failure-triage`) puis la skill
   `write-*-tests` concernée en `--fix`.
8. **Mesure** — skills `coverage` puis `mutation-check`.
9. **Boucle** — si `--until-thresholds` (ou par défaut) et seuils non atteints, et tours < `--max-rounds` :
   skill `test-plan --gaps` (lacunes : lignes non couvertes, mutants survivants, éléments d'inventaire sans BH),
   puis retour en 4 sur les seules lacunes. `state.py push-event` à chaque tour.
10. **Fin** — quand les seuils sont atteints ou le nombre de tours épuisé : `state.py set suite.status
    ready|incomplete`.

## Rapport final

- Zones, nombre de fiches BH, nombre de tests par suite.
- Résultat de la dernière exécution, couverture, MSI, tours effectués.
- Éléments d'inventaire sans BH, lacunes restantes, BH marqués « bug historique ».
- Recommandation : prêt pour `/upgrade-symfony` ou non.
