---
name: upgrade-symfony
description: Orchestrateur - monte Symfony LTS par LTS jusqu'à la version cible (6.4 par défaut) en enchaînant inventaire, dépréciations, montée, tests, tri des échecs, contrôles de sécurité, rapport d'étape et commit ; appelle /upgrade-php quand la version suivante exige un PHP plus récent.
when_to_use: Après /baseline et /build-test-suite (suite verte sur l'étape courante), pour faire avancer la migration Symfony d'une ou plusieurs marches.
argument-hint: "[<cible, ex. 6.4>] [--until step-NN] [--auto|--interactive] [--dry-run] [--resume]"
allowed-tools: Bash(.claude/bin/state.py:*), Bash(.claude/bin/track-merge.py:*), Bash(.claude/bin/env.sh:*), Bash(.claude/bin/tests.sh:*), Bash(.claude/bin/junit-summary.py:*), Bash(.claude/bin/json-diff.py:*), Bash(git status:*), Bash(git log:*), Bash(git diff:*)
---

# Orchestrateur : montée de Symfony

Arguments : `$ARGUMENTS`. Cible par défaut : `target.symfony` de `.claude/reference/steps.json`. Mode par défaut :
`--interactive`.

Références obligatoires : `.claude/reference/conventions.md` (chemins, scripts, gates § 7, état § 6),
`.claude/reference/steps.json`, `tmp/plan-migration-php84-sf64.md` § 1.1.

## Règles

- S'exécute dans la **session principale** : chaque étape est déléguée en invoquant la skill correspondante avec
  l'outil **Skill** (jamais en refaisant son travail ici).
- **Une marche = une version Symfony** de `steps.json` (`kind: symfony`). Jamais de saut de LTS.
- Pendant une marche, le gel est actif (`state.py freeze on`) : `tests/`, fixtures et golden masters sont intouchables.
- Les gates de `conventions.md` § 7 arrêtent l'orchestrateur **même en `--auto`** ; la décision revient à l'utilisateur
  (`AskUserQuestion` : corriger / accepter avec raison / reporter / arrêter).
- Aucune acceptation de changement de comportement, de vulnérabilité ou de dépréciation sans réponse explicite de
  l'utilisateur.
- Jamais de `git push`.

## 0. Initialisation

1. Lire `steps.json`, `docker/step.env` (étape courante), `.claude/state/migration/state.json` s'il existe.
2. `--resume` : reprendre à `current_step` / `phase` de l'état (si `orchestrator` ≠ `upgrade-symfony`, demander).
   Sinon : `state.py set orchestrator upgrade-symfony`, `target`, `mode`, `max_fix_iterations` (3 par défaut).
3. Calculer la liste des marches restantes : étapes `kind: symfony` après l'étape courante, jusqu'à la cible ou
   `--until`. Pour chacune, vérifier `php_min_for_symfony` : si le PHP de l'étape courante est inférieur, une marche
   `/upgrade-php` (étape `kind: php` qui la précède dans `steps.json`, ou la plus petite version satisfaisante) doit
   être intercalée.
4. **`--dry-run`** : afficher le plan (marches, versions, PHP intercalés, dépréciations bloquantes connues dans
   `docs/migration/deprecations.json`, risques notés dans `steps.json`) et s'arrêter sans rien modifier.
5. En `--interactive`, faire valider le plan à l'utilisateur.

## Pour chaque marche Symfony `step-NN`

Après chaque phase : `state.py set phase <phase>` et `state.py push-event "<résumé>"`.

1. **Pré-requis** (`phase: prereq`)
   - Skill `env` (`up`, puis `status`) ; si un conteneur n'est pas sain → gate `G-ENV`.
   - Skill `test-run` (`--suite all`). Suite non verte sur l'étape courante → arrêt (`G-TESTS`) : on ne monte pas
     sur une base rouge.
   - `state.py set current_step step-NN` puis `state.py freeze on`.
2. **État initial** (`phase: snapshot`)
   - Skills `inventory` (`all --snapshot`), `deprecation-check` (`--snapshot --target sf-<version>`),
     `vuln-audit` (`--snapshot`).
3. **Nettoyage des dépréciations** (`phase: fix-deprecations`)
   - Tant que `deprecation-check --target sf-<version>` signale des dépréciations **bloquantes** :
     skill `fix-deprecations` (`--target sf-<version>`), puis skill `test-run`.
     - Rouge → skill `failure-triage` ; selon le verdict : `fix-deprecations --fix` (REGRESSION),
       gate `G-BEHAVIOR` (BEHAVIOR-CHANGE), `G-ENV` (ENV), arrêt et signalement (TEST : les tests sont gelés,
       seul l'utilisateur peut lever le gel).
     - Incrémenter `fix_iterations` ; au-delà de `max_fix_iterations` → gate `G-TESTS`.
   - Dépréciation bloquante sans correction possible → gate `G-DEPREC`.
4. **Compatibilité PHP** (`phase: php`)
   - Si `php_min_for_symfony[<version>]` > PHP courant : skill `upgrade-php` avec la version PHP requise
     (`--until` sur l'étape php correspondante, même mode). Reprendre ensuite ici.
5. **Montée** (`phase: upgrade`) — skill `upgrade-step` avec `<version>`.
6. **Vérification** (`phase: verify`)
   - Skill `test-run` (`--suite all`, différentiel compris contre `app-ref`).
   - Rouge → `failure-triage` puis `upgrade-step --fix` (REGRESSION), boucle bornée par `max_fix_iterations`
     → sinon `G-TESTS`.
7. **Contrôles** (`phase: controls`)
   - Skills `inventory` (`all --diff`), `deprecation-check` (`--diff`), `vuln-audit` (`--diff`).
   - Élément d'inventaire disparu ou modifié → `G-INVENTORY` ; vulnérabilité `introduced` → `G-VULN` ;
     dépréciation `introduced` non arbitrée → `G-DEPREC`.
8. **Bilan** (`phase: report`) — skill `step-report` avec `step-NN`.
9. **Passage** (`phase: gate`)
   - `--interactive` ou alerte présente dans `state.json` : présenter le rapport d'étape et demander validation.
   - `--auto` sans alerte : continuer.
10. **Commit** (`phase: commit`) — skill `step-commit` avec `step-NN`. Puis `state.py freeze off`,
    `state.py set last_green.step step-NN`, `fix_iterations` remis à 0.

## Gate levée

1. `state.py alert <G-CODE> "<texte>"` et `state.py push-event "<résumé>"`.
2. Résumer : marche, phase, code, éléments en cause (BH, dépréciation, vulnérabilité, élément d'inventaire).
3. `AskUserQuestion` : **Corriger** (reprendre la phase), **Accepter** (raison exigée), **Reporter**
   (arrêt propre, état conservé pour `--resume`), **Arrêter**.
4. **Accepter** — consigner la décision, selon la gate :
   - `G-BEHAVIOR` : skill `failure-triage` en mode consignation (l'auditeur écrit `docs/migration/accepted-changes.md` :
     BH, comportement avant / après, raison, auteur, date, étape). Les tests concernés devront ensuite être mis à jour
     par `test-writer --fix` : cela exige la levée du gel **par l'utilisateur** (`! .claude/bin/state.py freeze off`).
   - `G-VULN` / `G-DEPREC` : `.claude/bin/track-merge.py <vulnerabilities|deprecations> --accept <clé> --reason "…"
     --by "<utilisateur>" --until-step step-NN` (ou `--not-applicable`).
   - `G-INVENTORY` : consigner dans `accepted-changes.md` (via l'auditeur), comme un changement de comportement.
   Puis `state.py alerts-clear` et reprise de la phase.
5. Le gel n'est levé que si l'utilisateur le demande explicitement, ou automatiquement en fin de marche verte.

## Rapport final

- Marches réalisées (avant → après, PHP intercalés), commits créés.
- Gates rencontrées et décisions.
- Diffs cumulés : vulnérabilités (résolues / ajoutées), dépréciations, inventaire.
- État final et commande de reprise (`/upgrade-symfony --resume`) si interrompu.
