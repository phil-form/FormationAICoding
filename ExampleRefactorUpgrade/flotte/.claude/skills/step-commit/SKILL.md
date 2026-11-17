---
name: step-commit
description: Commit de flotte (seul commit à utiliser sous flotte/ ; le /commit du dépôt parent concerne un autre projet, Java) - suite de tests flotte, scan de sécurité du seul contenu indexé (fichiers interdits, secrets, configuration, dépendances, SAST), contrôle des diffs de migration pour une marche, git add ciblé, message conventionnel. Deux modes - marche de migration (tag d'étape sur confirmation) et hors marche (outillage, tests, documents). Ne pousse jamais.
when_to_use: Chaque fois qu'il faut commiter dans flotte/ - fin de marche (orchestrateurs), commit automatique après une génération de tests, ou demande de l'utilisateur (« commit », « commite »…).
argument-hint: "[step-NN | --hors-marche] [intention du message] [--no-sast]"
allowed-tools: Bash(.claude/bin/tests.sh:*), Bash(.claude/bin/junit-summary.py:*), Bash(.claude/bin/json-diff.py:*), Bash(.claude/bin/state.py:*), Bash(.claude/bin/test-tracking.py:*), Bash(.claude/skills/step-commit/scripts/commit-scan.sh:*), Bash(git status:*), Bash(git diff:*), Bash(git log:*), Bash(git add:*), Bash(git commit:*), Bash(git tag:*), Bash(git rev-parse:*), Bash(git restore --staged:*)
---

# Commit flotte

Arguments : `$ARGUMENTS`.

**Mode** :
- **marche** — un argument `step-NN`, ou appel par `/upgrade-symfony` / `/upgrade-php` en fin de marche : §§ 1 à 6 ;
- **hors marche** (défaut sinon, ou `--hors-marche`) — outillage, tests, fiches, documents : §§ 1, 2, 3, 5 ; pas de
  contrôle des diffs de migration (§ 4) ni de tag (§ 6).

Le dépôt Java du dossier parent est **un autre exemple** : ne jamais lancer sa suite de tests ni son `/commit`, ne
jamais indexer de chemin hors de `flotte/`.

## Règles absolues

- **Aucune modification** de code ou de configuration pendant cette skill : on constate, on rapporte,
  l'utilisateur décide.
- Jamais `git push`, jamais `--no-verify`, jamais de réécriture d'historique (`--amend`, `rebase`, `reset`) sauf
  demande explicite.
- Fichiers **interdits** dans un commit : `.env` (et `.env.*` hors `.env.example` / `.env.dist`), `.ref/`,
  `.claude/state/`, `vendor/`, `tests/vendor/`, `app/cache/`, `var/`, `app/logs/`, `app/config/parameters.yml`,
  tout `*.sqlite` **hors** `tests/fixtures/db/`, clés et certificats.
- Le dépôt git est le dépôt parent (`Formation/`) : n'indexer que des chemins sous `flotte/`.

## 1. Préparer

1. `git status --short -- .` et `git diff --stat -- .` (+ `--cached`).
2. Indexer avec `git add <chemins>` explicites (jamais `git add -A` ni `.`), uniquement ce qui relève de l'intention.
   Changements sans rapport → commits séparés (ex. outillage d'un côté, fiches de tests de l'autre).
   Commit automatique après une génération de tests : **uniquement** les fichiers de tests produits
   (`tests/…`, `tmp/tests-*.md`).

## 2. Tester (suite flotte)

1. Si `tests/phpunit.xml.dist` existe : `.claude/bin/tests.sh --suite all` (et `--target ref` si `.ref/` existe et
   que le commit ajoute ou modifie des tests : un test de caractérisation doit être vert sur la référence).
   Sinon : noter « suite de tests flotte pas encore créée » dans le rapport et le message (`Tests: none yet`).
2. Rouge → **arrêt** : rapport des échecs (`junit-summary.py`) et proposition `/failure-triage` ; `AskUserQuestion` :
   trier, relancer, commiter malgré tout (trailer `Tests-Exception: <justification de l'utilisateur>`), annuler.
3. `.claude/bin/test-tracking.py` (suivi des fiches à jour) ; si un document `tmp/tests-*.md` change, l'indexer.

## 3. Contrôler la sécurité (contenu indexé uniquement)

`.claude/skills/step-commit/scripts/commit-scan.sh [--no-sast]` : instantané de l'index sous `flotte/`, puis
fichiers interdits, secrets (Trivy), configuration Docker (Trivy, HIGH/CRITICAL), dépendances des `composer.lock`
indexés (Trivy, HIGH/CRITICAL corrigeables), SAST (Semgrep : PHP, OWASP Top 10, secrets, Dockerfile, Python).
Rapport : `.claude/state/commit-scan/report.md`.

| Code | Suite |
|---|---|
| 0 | § 4 (marche) ou § 5 |
| 10 | résultats bloquants : rapport synthétique (quoi, où, pourquoi c'est un risque ici, piste de correction **non appliquée**) puis `AskUserQuestion` : planifier les corrections, approfondir, commiter malgré tout (trailer `Security-Exception: <justification de l'utilisateur>`), annuler |
| 2 | scan incomplet : expliquer (`*.stderr`), proposer relancer / commiter sans ce contrôle (trailer) / annuler |

Seule exception sans question : la **dérogation durable** consignée dans `CLAUDE.md` (versions legacy step-00 de
`symfony/symfony` 2.8.52 et `twig/twig` 1.42.5, secret de démo de `parameters.yml.dist`), qui n'apparaît que si le
commit contient ces fichiers ; ajouter alors son trailer.

## 4. Contrôler les diffs de migration (mode marche)

Lire, pour l'étape :
- `docs/security/vulnerabilities.json` : vulnérabilités `introduced` (ou `accepted` échues) → arrêt sauf décision
  déjà consignée ;
- `docs/migration/deprecations.json` : `introduced` non arbitrées → arrêt ;
- inventaire : `.claude/bin/json-diff.py` étape précédente / étape courante par type → élément disparu ou modifié
  sans décision → arrêt ;
- `state.json` : alertes `G-*` non résolues → arrêt.

En cas d'arrêt : rapport et `AskUserQuestion` (corriger / accepter avec raison / commiter quand même avec la
mention dans le message / annuler).

## 5. Commiter

Message en anglais, Conventional Commits (`type(scope): résumé` ≤ 72 caractères, corps en puces : le **pourquoi**).

Mode marche :

```
chore(migration): step-NN symfony A.B -> C.D (php X.Y)

Tests: <n> passed (http/cli/db/edge/diff/unit)
Vulnerabilities: -<résolues> fixed, +<ajoutées> introduced, <ouvertes> open
Deprecations: -<résolues> fixed, +<ajoutées> introduced, <bloquantes> blocking for next step
Inventory: <inchangé | n changes, see docs/migration/steps/step-NN.md>
Accepted behavior changes: <n> (docs/migration/accepted-changes.md)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```

Mode hors marche : type adapté (`test(flotte):`, `feat(flotte-tooling):`, `build(flotte):`, `docs(flotte):`…),
ligne `Tests:` (résultat de la suite flotte ou `none yet`), trailers éventuels, puis `Co-Authored-By`.

`git commit -F <fichier-message>` (fichier dans le scratchpad), puis `git log --oneline -1` et `git log -1 --stat`.

## 6. Tag d'étape (mode marche)

Uniquement si la marche est terminée **et** que l'utilisateur confirme : `git tag flotte-migration/step-NN`.
Puis `state.py set last_green.step step-NN` et `state.py set last_green.commit <hash>`.

## Rapport final

Hash et titre du commit, fichiers commités, résultat des tests flotte, résultat du scan (rapport
`.claude/state/commit-scan/report.md`), tag créé ou non. Rappel : rien n'a été poussé.
