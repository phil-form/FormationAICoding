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
- Commits : **toujours via `/step-commit`** (pas le `/commit` Java du parent), jamais de push.
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

## Démarrage

```text
/env init            # crée docker-compose.yml et docker/ (première fois)
/baseline            # lock, empreintes, audits step-00, tag de référence
/build-test-suite    # caractérisation + tests jusqu'aux seuils
/upgrade-symfony 6.4 --interactive
/upgrade-php 8.4
```
