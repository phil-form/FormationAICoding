---
name: step-report
description: Produit le bilan d'une marche de migration (docs/migration/steps/step-NN.md) - versions, tests, inventaire, dépréciations et vulnérabilités (diffs), changements de comportement acceptés, gates et décisions ; ou le bilan final de la migration.
when_to_use: À la fin de chaque marche (appelée par /upgrade-symfony ou /upgrade-php avant le commit), ou avec « final » en fin de migration (/migrate).
argument-hint: "<step-NN | final>"
context: fork
agent: auditor
---

# Rapport d'étape

Arguments : `$ARGUMENTS`.

Sources : `docker/step.env`, `.claude/reference/steps.json`, `composer.lock`, `.claude/state/migration/state.json`
(alertes, événements), résultats `test-results/`, `docs/migration/inventory/step-NN/` et l'étape précédente,
`docs/migration/deprecations.json`, `docs/security/vulnerabilities.json`, `docs/migration/accepted-changes.md`,
`.claude/state/migration/triage/step-NN.md`, `git log` / `git diff` depuis `last_green`.

## Règles

- Constat uniquement : aucune modification hors du rapport.
- Écriture : `docs/migration/steps/step-NN.md` (ou `docs/migration/steps/final.md`).
- Les diffs sont calculés par les scripts (`.claude/bin/json-diff.py`, `.claude/bin/junit-summary.py`), pas à la main.

## Contenu de `step-NN.md`

1. **En-tête** : étape, date, PHP et Symfony avant → après, hash du `composer.lock`, commit de départ.
2. **Dépendances** : paquets directs ajoutés / retirés / mis à jour ; principales transitives.
3. **Tests** : totaux par suite (✔ / ✘ / ↷), itérations de correction, verdicts du tri.
4. **Inventaire** : `json-diff.py` par type (routes, commands, services, forms, entities, templates, config) entre
   l'étape précédente et celle-ci ; tout élément disparu ou modifié est mis en évidence (et doit correspondre à une
   décision).
5. **Dépréciations** : résolues / nouvelles / persistantes ; bloquantes restantes pour la marche suivante.
6. **Vulnérabilités** : résolues / ajoutées / persistantes, par sévérité et exposition ; `accepted` arrivant à
   échéance.
7. **Changements de comportement acceptés** pendant la marche (renvoi à `accepted-changes.md`).
8. **Gates et décisions** (depuis `state.json`).
9. **Points d'attention pour la marche suivante** (notes `steps.json`, risques signalés par `migrator`).

## Mode `final`

Synthèse step-00 → dernière étape : tableau des marches, total des changements acceptés, bilan sécurité
(ouvertes au départ / résolues / restantes), dépréciations restantes, recommandations (Symfony 7.4 LTS, corrections
de bugs historiques à planifier en dehors de la migration).

## Rapport final (ta réponse)

Chemin du rapport écrit, et résumé en 10 lignes maximum (tests, inventaire, dépréciations, vulnérabilités, gates).
