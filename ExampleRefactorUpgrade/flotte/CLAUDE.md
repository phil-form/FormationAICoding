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
- **Secrets et données sensibles inaccessibles** : le hook `secrets-guard` (`.claude/hooks/secrets-guard.sh`, toute la
  session, sous-agents compris) et les règles `permissions.deny` de `.claude/settings.json` refusent lecture, écriture,
  recherche et affichage des `.env` (hors modèles et `.env`/`.env.test` Flex de flotte), `parameters.yml`, caches
  compilés, clés/certificats/jetons, `secrets/`, `init_db/`, identifiants de l'utilisateur, variables
  d'environnement (`env`, `docker inspect`, `docker compose config`) et lectures OpenBao. Ne jamais le contourner :
  demander à l'utilisateur de consulter lui-même (`! commande`) sans recopier le contenu. Tests :
  `.claude/hooks/secrets-guard.test.sh`.
- Une marche = une version (Symfony ou PHP), jamais de saut de LTS ; zéro dépréciation bloquante avant de monter.
- Commits : **toujours `/step-commit`** (mode marche pour une étape de migration, mode hors marche pour l'outillage,
  les tests et les documents). **Ne pas utiliser le `/commit` du dépôt parent** ni sa suite Java : le projet Java du
  dossier parent est un autre exemple de la formation. Jamais de `git commit` direct, jamais de push.
- **Commit automatique des tests** : à la fin de **chaque génération de tests** (skill `write-bh-tests`,
  `golden-master --record`, `seed --rebuild`, ou mise à jour du document de fiches `tmp/tests-application.md`),
  lancer `/step-commit` **sans attendre de demande**, dans un commit dédié (type `test(flotte): …`), sans y mêler d'autres
  changements. Le hook `Stop` `.claude/hooks/commit-after-tests.sh` le rappelle si c'est oublié (une fois par lot de
  changements). Les décisions du commit (dérogation, résultats bloquants) restent celles de l'utilisateur.
- **Dérogation durable des commits (décision de l'utilisateur, 2026-11-17)** : les résultats bloquants **legacy
  step-00** ci-dessous ne demandent plus de décision ; les commits portent le trailer
  `Security-Exception: legacy step-00 baseline (Symfony 2.8/Twig 1.x CVEs and default demo secret) is the migration
  starting point, tracked by /vuln-audit; no new secret or vulnerable code introduced.` Périmètre **strict** :
  secret Symfony de démo dans `app/config/parameters.yml.dist` et dans les fichiers générés ignorés par git
  (`app/cache/*`, `app/config/parameters.yml`) ; CVE de `symfony/symfony` 2.8.52 et `twig/twig` 1.42.5 du
  `composer.lock` step-00. **Tout autre** résultat bloquant (nouveau secret, SAST, autre paquet ou version, image,
  configuration) reste soumis à la décision de l'utilisateur. Fin de la dérogation : quand la migration retire ces versions.
  **Extension (décision de l'utilisateur, 2026-11-17, commit de step-01)** : la dérogation couvre aussi les **versions
  intermédiaires** de `symfony/symfony` et `twig/twig` installées par une marche de la migration (ex. Symfony 3.4.49),
  **à condition** que chacun de leurs avis bloquants soit déjà suivi dans `docs/security/vulnerabilities.json` sans
  statut `introduced` (gate `G-VULN` non levée par `/vuln-audit --diff` de la marche). Même trailer
  `Security-Exception`, complété par la version concernée. Un avis `introduced`, un autre paquet, un secret, un constat
  SAST ou de configuration restent soumis à la décision de l'utilisateur.
- Toute faille de sécurité ou dépréciation est tracée via `track-merge.py` dans `docs/security/vulnerabilities.json`
  / `docs/migration/deprecations.json` ; jamais d'édition manuelle de ces JSON.
- Ne pas ajouter de dépendance non exigée par une marche sans le signaler.
- **Rector** : toujours via `.claude/bin/rector.sh <step-NN>` (configurations `rector/`, simulation par défaut,
  `--apply` par lots, relancé jusqu'à stabilité, tests après chaque lot). Uniquement des règles de compatibilité,
  jamais de jeux de modernisation : `rector/README.md`.

## Outillage IA (`.claude/`)

| Niveau | Skills |
|---|---|
| Orchestrateurs | `/migrate` (manuel), `/build-test-suite`, `/upgrade-symfony <cible>`, `/upgrade-php <cible>` |
| Étapes | `/baseline`, `/upgrade-step <symfony\|php> <version>`, `/fix-deprecations`, `/test-plan`, `/characterize`, `/failure-triage`, `/step-report`, `/step-commit` |
| Outils | `/env`, `/seed`, `/inventory [all\|routes\|commands\|services\|forms\|entities\|templates\|config]`, `/write-bh-tests <http\|cli\|db\|edge\|diff\|unit> <cible>`, `/golden-master`, `/test-run`, `/coverage`, `/mutation-check`, `/deprecation-check`, `/compat-scan`, `/vuln-audit` |
| Formation | `/export-prompts` : exporte les prompts des sessions dans `tmp/prompts.md` (verbatim, avec skills et prompts délégués) |

Agents (isolés par `.claude/hooks/guard.sh <mode>`) : `cartographer`, `characterizer`, `test-writer` (aucun accès au
code de l'application), `migrator` (aucune écriture dans `tests/`, `docs/`, `.claude/`), `deprecation-analyst`,
`security-auditor`, `auditor` (seul à tout voir, arbitre), `log-analyst` (lecture seule).

Les orchestrateurs tournent dans la session principale (un sous-agent ne peut pas en lancer d'autres) ; leur état
est dans `.claude/state/migration/state.json` (`--resume`). Ils s'arrêtent sur toute gate `G-*` (conventions § 7),
même en `--auto`.

## Suivi des tests

Toute la mise en place des tests est suivie dans **un document unique : `tmp/tests-application.md`** (gabarit
commun § 1, puis une section par contrôleur et une pour les commandes : 209 fiches `BH-NNN`, exemples, points à
trancher, journal d'implémentation, blocages). **Après chaque implémentation de tests, il est mis à jour dans toutes ses parties** (lignes de
fiches corrigées avec la valeur observée, journal d'implémentation, blocages, points à trancher, exemple, bloc de
suivi) : `.claude/reference/conventions.md` § 5.6, étape obligatoire des skills `write-bh-tests` et `golden-master`.
Son bloc « Suivi d'avancement » (global, par groupe, par route, détail par fiche) est régénéré à chaque `.claude/bin/tests.sh` par `.claude/bin/test-tracking.py`
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
