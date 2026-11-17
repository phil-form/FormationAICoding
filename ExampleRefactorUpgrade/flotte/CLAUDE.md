# CLAUDE.md — flotte (migration PHP 8.4 / Symfony 6.4)

Ce fichier **prime** sur le `CLAUDE.md` du dépôt parent (projet Java/Spring) pour tout ce qui se trouve sous
`flotte/`. Lancer Claude Code **depuis ce dossier**.

## Projet

Application legacy « Flotte Delvaux & Fils » (2016) : PHP 7.2, Symfony 2.8, Doctrine 2.5, Twig 1, SQLite (voir
`README.md`). Objectif : la migrer vers **PHP 8.4 / Symfony 6.4** sans changer son comportement — **bug pour bug** —
en passant LTS par LTS.

- Plan de référence : `tmp/plan-migration-php84-sf64.md`.
- Conventions de l'outillage (chemins, scripts, formats, gates) : `.claude/reference/conventions.md` — **fait foi**.
- Étapes : `.claude/reference/steps.json` ; étape courante : `docker/step.env`.

## Règles

- Répondre et documenter **en français** ; code, identifiants et commits en anglais.
- **Aucun changement de comportement** sans décision explicite de l'utilisateur, consignée dans
  `docs/migration/accepted-changes.md`. Un bug historique se préserve ; sa correction est une étape à part.
- **Tests gelés pendant une marche** (`state.py freeze on`) : `tests/`, scénarios et golden masters ne se modifient
  pas pour « faire passer » une migration. Le hook `freeze-guard` l'impose à toute la session.
- Une marche = une version (Symfony ou PHP), jamais de saut de LTS ; zéro dépréciation bloquante avant de monter.
- Commits : **`/step-commit`** pour une marche de migration (tag d'étape, diffs de sécurité / dépréciations) ;
  **`/commit`** pour le reste (outillage, tests, documents). Jamais de `git commit` direct, jamais de push.
- **Commit automatique des tests** : à la fin de **chaque génération de tests** (skills `write-*-tests`,
  `golden-master --record`, `seed --rebuild`, ou création / mise à jour d'un document de fiches `tmp/tests-*.md`),
  lancer `/commit` **sans attendre de demande**, dans un commit dédié (type `test(flotte): …`), sans y mêler d'autres
  changements. Le hook `Stop` `.claude/hooks/commit-after-tests.sh` le rappelle si c'est oublié (une fois par lot de
  changements). Les décisions de `/commit` (dérogation, résultats bloquants) restent celles de l'utilisateur.
- **Dérogation durable de `/commit` (décision de l'utilisateur, 2026-11-17)** : les résultats bloquants **legacy
  step-00** ci-dessous ne demandent plus de décision ; les commits portent le trailer
  `Security-Exception: legacy step-00 baseline (Symfony 2.8/Twig 1.x CVEs and default demo secret) is the migration
  starting point, tracked by /vuln-audit; no new secret or vulnerable code introduced.` Périmètre **strict** :
  secret Symfony de démo dans `app/config/parameters.yml.dist` et dans les fichiers générés ignorés par git
  (`app/cache/*`, `app/config/parameters.yml`) ; CVE de `symfony/symfony` 2.8.52 et `twig/twig` 1.42.5 du
  `composer.lock` step-00. **Tout autre** résultat bloquant (nouveau secret, SAST, autre paquet ou version, image,
  configuration) reste soumis à la décision de l'utilisateur. Fin de la dérogation : quand la migration retire ces versions.
- Toute faille de sécurité ou dépréciation est tracée via `track-merge.py` dans `docs/security/vulnerabilities.json`
  / `docs/migration/deprecations.json` ; jamais d'édition manuelle de ces JSON.
- Ne pas ajouter de dépendance non exigée par une marche sans le signaler.

## Outillage IA (`.claude/`)

| Niveau | Skills |
|---|---|
| Orchestrateurs | `/migrate` (manuel), `/build-test-suite`, `/upgrade-symfony <cible>`, `/upgrade-php <cible>` |
| Étapes | `/baseline`, `/upgrade-step`, `/php-step`, `/fix-deprecations`, `/test-plan`, `/characterize`, `/failure-triage`, `/step-report`, `/step-commit` |
| Outils | `/env`, `/seed`, `/inventory` + `/list-routes`, `/list-commands`, `/list-services`, `/list-forms`, `/list-entities`, `/list-templates`, `/list-config`, `/write-http-tests`, `/write-cli-tests`, `/write-db-tests`, `/write-unit-tests`, `/write-edge-tests`, `/write-diff-tests`, `/golden-master`, `/test-run`, `/coverage`, `/mutation-check`, `/deprecation-check`, `/compat-scan`, `/vuln-audit` |
| Formation | `/export-prompts` : exporte les prompts des sessions dans `tmp/prompts.md` (verbatim, avec skills et prompts délégués) |

Agents (isolés par `.claude/hooks/guard.sh <mode>`) : `cartographer`, `characterizer`, `test-writer` (aucun accès au
code de l'application), `migrator` (aucune écriture dans `tests/`, `docs/`, `.claude/`), `deprecation-analyst`,
`security-auditor`, `auditor` (seul à tout voir, arbitre), `log-analyst` (lecture seule).

Les orchestrateurs tournent dans la session principale (un sous-agent ne peut pas en lancer d'autres) ; leur état
est dans `.claude/state/migration/state.json` (`--resume`). Ils s'arrêtent sur toute gate `G-*` (conventions § 7),
même en `--auto`.

## Suivi des tests

Les fiches de tests par contrôleur sont dans `tmp/tests-*.md` (gabarit : `tmp/tests-vehicle-controller.md` § 2).
Leur bloc « Suivi d'avancement » est régénéré à chaque `.claude/bin/tests.sh` par `.claude/bin/test-tracking.py`
(à lancer aussi à la main après l'écriture d'un test). Bloquer une fiche : `- BH-NNN : ⏸ raison` dans la section
« Blocages » du document.

## Status line

`.claude/statusline.sh` (reprise du dépôt parent) : modèle, contexte (barre, tokens), coût, branche, **étape de
migration** (`🔀 step-NN PHP x.y SF x.y`, `❄ gelé`, gates ouvertes) ; tokens session 5 h / semaine ; pendant un run
de `.claude/bin/tests.sh` et 15 min après : progression des tests (suite, %, ETA, ✔/✘/↷, cible `app-ref`).

## Démarrage

```text
/env init            # crée docker-compose.yml et docker/ (première fois)
/baseline            # lock, empreintes, audits step-00, tag de référence
/build-test-suite    # caractérisation + tests jusqu'aux seuils
/upgrade-symfony 6.4 --interactive
/upgrade-php 8.4
```
