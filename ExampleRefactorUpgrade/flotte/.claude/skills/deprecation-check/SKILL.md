---
name: deprecation-check
description: Inventorie les dépréciations de l'étape courante (runtime Symfony et PHP, analyse statique PHPStan et Rector, Twig, Doctrine, configuration, dépendances abandonnées), normalise leurs signatures, met à jour docs/migration/deprecations.json et signale celles qui bloquent la version visée - sans rien corriger.
when_to_use: Avant et après chaque marche de migration (appelé par /upgrade-symfony et /upgrade-php), avant /fix-deprecations, ou à la demande pour faire le point.
argument-hint: "[--snapshot|--diff] [--target sf-x.y] [--php x.y] [--source runtime|static|all]"
context: fork
agent: deprecation-analyst
---

# Dépréciations : inventaire et suivi

Arguments : `$ARGUMENTS`. Conventions : `.claude/reference/conventions.md` (§ 4, § 5.4, § 7). Plan : § 5.

## Règles

- **Lecture seule** : aucune modification de code, de configuration ou de dépendance. La correction relève de
  `/fix-deprecations` (agent `migrator`).
- Une dépréciation est une **panne future** : priorité à celles supprimées dans la version visée (`--target`,
  `--php`) → **bloquantes** (gate `G-DEPREC`).
- Le runtime ne voit que le code **exécuté** : toujours croiser avec l'analyse statique.
- `deprecations.json` n'est modifié **que** par `.claude/bin/track-merge.py deprecations`.

## 1. Préparer

1. Étape courante : `docker/step.env` (`STEP`, versions). Version visée : `--target` / `--php`, sinon la marche
   suivante de `.claude/reference/steps.json`.
2. `--source` défaut `all`. Pour `runtime`, l'environnement doit être sain (`/env status`) et une exécution de la
   suite récente doit exister (`.claude/state/migration/test-results/summary.json` de la même étape) ; sinon
   demander `/test-run` au préalable (la session principale le lance).

## 2. Collecter

| Source | Collecte |
|---|---|
| Runtime Symfony | `E_USER_DEPRECATED` du journal `/var/log/php/deprecations.log` de `app` (alimenté pendant `/test-run`) et logs Symfony (`var/log` ou `app/logs`, canal `php`) |
| Runtime PHP | `E_DEPRECATED` du même journal |
| Statique PHP | `.claude/bin/tools.sh phpstan analyse --error-format=json` avec `phpstan-deprecation-rules` et `phpstan-symfony`, `phpVersion` = PHP visé |
| Rector | `.claude/bin/tools.sh rector process --dry-run --output-format=json` avec les sets Symfony / Doctrine / Twig / PHP de la version visée ; noter la règle Rector = correction automatique possible |
| Twig | `.claude/bin/console.sh lint:twig --show-deprecations` (Symfony ≥ 4) ; en step-00/01 : lecture des templates contre la liste des syntaxes supprimées (`{% for … if %}`, filtres et tags retirés) |
| Doctrine | `doctrine/deprecations` en mode trace (ORM ≥ 2.10) ; avant : lecture du mapping (annotations, `AppBundle:Entity`, `getRepository('AppBundle:…')`) |
| Configuration | `.claude/bin/console.sh cache:clear -v` et `debug:config` : clés et services dépréciés, `$this->get('…')` sur services privés |
| Dépendances | `.claude/bin/composer.sh outdated --direct --format=json` et champ `abandoned` du lock (`SensioFrameworkExtraBundle`, `security-acl`…) |

## 3. Normaliser

1. **Signature** stable : message sans numéros de ligne, chemins, identifiants ni valeurs variables (ex.
   `symfony/form: isValid() called on a form that is not submitted`). Regrouper les occurrences sous la signature,
   `location` = liste des emplacements.
2. Pour chaque signature : `source`, `removed_in` (version qui supprime), `blocking_for` (étape de
   `steps.json` où la suppression intervient), `autofix` (règle Rector ou `none`).
3. Écrire les constats bruts dans `.claude/state/migration/deprecations/<step>-findings.json` (tableau d'objets
   au format du plan § 5.3, sans `status`).

## 4. Fusionner

`.claude/bin/track-merge.py deprecations --step <step> --input .claude/state/migration/deprecations/<step>-findings.json`
→ transitions de statut (conventions § 5.4). `--snapshot` : fusion seule. `--diff` : en plus, comparaison avec la
marche précédente (nouvelles / résolues / persistantes).

## 5. Rapport `docs/migration/deprecations/<step>.md`

1. Synthèse : totaux par statut et par source ; **bloquantes** pour la version visée en tête.
2. Regroupement par famille (formulaires, sécurité, routing, Twig, Doctrine, PHP, configuration, dépendances).
3. Pour chaque famille : correction automatique (Rector) ou manuelle, risque de changement de comportement (lien
   avec les `BH-xxx` concernés si connus), proposition de **lots** pour `/fix-deprecations` (une famille par lot).
4. Diff avec l'étape précédente (`--diff`).
5. Gate : bloquantes > 0 ou `introduced` non arbitrées → `G-DEPREC`.

## Rapport final

Totaux, bloquantes (nombre et familles), nouvelles / résolues, gate levée ou non, chemins du rapport et du JSON,
lots proposés.
