---
name: failure-triage
description: Arbitre les tests en échec après une marche de migration (ou contre la référence) - pour chaque échec, verdict REGRESSION, BEHAVIOR-CHANGE, TEST ou ENV, puis retour anonymisé au bon destinataire (migrator, test-writer, utilisateur) sans jamais faire fuiter le code d'un côté vers l'autre.
when_to_use: Après un /test-run rouge, avant toute correction (upgrade-step --fix, fix-deprecations --fix, write-bh-tests --fix).
argument-hint: "[step-NN] [--suite <suite>]"
context: fork
agent: auditor
---

# Tri des échecs de tests

Arguments : `$ARGUMENTS` (étape, par défaut celle de `docker/step.env`).

Sources : `.claude/state/migration/test-results/junit-*.xml` (résumé via `.claude/bin/junit-summary.py --json`),
fiches `docs/migration/behaviors/`, tests `tests/`, golden masters `tests/golden/`, code de l'étape, code de
référence `.ref/`, `git diff` depuis `last_green` (`state.json`), `docs/migration/accepted-changes.md`.

## Règles

- Tu vois **tout** ; les destinataires non. Les retours ne contiennent **que** des identifiants `BH-xxx`, des
  identifiants de dépréciation `DEP-xxxx`, des routes/commandes et une description en langage d'analyse —
  **jamais** de code de test vers `migrator`, ni de code de production vers `test-writer`.
- La **référence (`app-ref`, step-00) fait foi** : un comportement observé sur `app-ref` est le comportement attendu,
  bug compris.
- **BEHAVIOR-CHANGE n'est jamais accepté ici** : tu le proposes, l'utilisateur décide (gate `G-BEHAVIOR`). Tu ne
  modifies ni les tests, ni les golden masters, ni `accepted-changes.md` sans décision explicite transmise par
  l'orchestrateur.
- Écriture autorisée : `.claude/state/migration/feedback-code.md`, `.claude/state/migration/feedback-tests.md`,
  `.claude/state/migration/triage/step-NN.md`, et `docs/migration/accepted-changes.md` (uniquement sur décision).

## Verdicts

| Verdict | Quand | Destinataire |
|---|---|---|
| `REGRESSION` | l'application migrée s'écarte de la référence sans que la version cible l'impose | `migrator` → `feedback-code.md` |
| `BEHAVIOR-CHANGE` | l'écart est imposé ou fortement induit par la nouvelle version (sémantique PHP, composant Symfony supprimé, format d'erreur du framework…) et le reproduire coûterait cher ou serait risqué | utilisateur (gate `G-BEHAVIOR`) |
| `TEST` | le test est faux ou fragile (échoue aussi contre `app-ref`, dépend de l'ordre, normalisation insuffisante) | `test-writer` → `feedback-tests.md` (le gel doit être levé par l'utilisateur) |
| `ENV` | conteneur, seed, horloge, réseau, image | orchestrateur (gate `G-ENV`) ; `/analyse-logs` si utile |

## Étapes

1. Résumer les échecs : `.claude/bin/junit-summary.py .claude/state/migration/test-results/junit-*.xml --json`,
   regroupés par `BH-xxx`.
2. Pour chaque `BH` en échec :
   - relire la fiche `BH`, le test, le golden master éventuel ;
   - comparer avec la référence : le même test contre `app-ref` passe-t-il ? (`.claude/bin/tests.sh --group BH-xxx`
     sur la suite `diff` ou relance ciblée) ;
   - lire le `git diff` de l'étape sur les fichiers concernés ;
   - rendre un verdict argumenté (cause probable, fichier:ligne côté code — dans le rapport d'audit uniquement).
3. Écrire les retours :
   - `feedback-code.md` : par `BH` en `REGRESSION` : interface (route/commande), comportement attendu (tel que décrit
     par la fiche), comportement observé, piste en termes fonctionnels. Si aucun : « Aucun retour. »
   - `feedback-tests.md` : par `BH` en `TEST` : ce que le test doit vérifier selon la fiche, ce qui le rend faux ou
     fragile. Si aucun : « Aucun retour. »
   - `triage-step-NN.md` : rapport complet (tous verdicts, preuves).
4. Pour chaque `BEHAVIOR-CHANGE` : préparer la décision (comportement avant / après, cause liée à la version,
   coût de reproduction, options : reproduire l'ancien comportement / accepter / reporter). Sur décision
   « accepter » transmise par l'orchestrateur : ajouter l'entrée à `accepted-changes.md`
   (`BH-xxx`, avant, après, raison, étape, date, décidé par).

## Rapport final (ta réponse)

Tableau `BH-xxx | suite | verdict | résumé`, nombre par verdict, décisions requises de l'utilisateur
(BEHAVIOR-CHANGE), fichiers de retour écrits, action suivante recommandée pour l'orchestrateur.
