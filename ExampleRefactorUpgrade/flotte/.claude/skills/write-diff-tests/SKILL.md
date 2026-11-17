---
name: write-diff-tests
description: Écrit les tests différentiels qui rejouent les mêmes parcours sur l'application en cours (APP_URL) et sur la référence step-00 (REF_URL) et comparent les réponses normalisées, via l'agent isolé test-writer.
when_to_use: Dans /build-test-suite une fois les tests HTTP en place ; complément quand une marche révèle un écart non couvert ; correction (--fix).
argument-hint: "[<zone>|all] [--fix]"
context: fork
agent: test-writer
---

# Tests différentiels app ↔ app-ref

Arguments : `$ARGUMENTS`. Référence : plan § 3.4 ; `.claude/reference/conventions.md` § 3 (services `app`,
`app-ref`, `tests`), § 5.2.

Règles, sources autorisées, gel, `--fix` et squelette : **identiques à `.claude/skills/write-http-tests/SKILL.md`**.

## Principe

- Un parcours = une suite de requêtes (GET, soumissions de formulaires avec CSRF, Basic auth) décrite dans
  `tests/src/Diff/Journeys/<zone>.yaml` (données, pas de code) et dérivée des fiches `BH` de la zone (et des
  fiches de couche `diff`).
- `tests/src/Diff/JourneyTest.php` : pour chaque parcours (data provider) :
  1. restaure **le même scénario** sur les deux cibles (`Fixtures::restore($s, 'app')`, `…'ref'`) ;
  2. rejoue chaque requête sur `APP_URL` et `REF_URL` avec des sessions séparées ;
  3. normalise (jetons CSRF, cookies de session, en-tête de version `X-…`, dates dépendant de l'heure réelle,
     espaces non significatifs, ordre des en-têtes) ;
  4. compare statut, `Location`, `Content-Type`, corps normalisé, puis le **dump canonique de la base** des deux
     cibles après le parcours ;
  5. en cas d'écart : message avec le diff, la requête et les `BH` concernés.
- **Les normalisations sont listées et justifiées** dans `tests/src/Diff/README.md` : chacune masque une
  différence ; aucune ne doit masquer un comportement métier (montant, ordre, statut, message).
- Les différences **acceptées** (cf. `docs/migration/accepted-changes.md`) ne sont pas normalisées en silence :
  elles sont listées dans `tests/src/Diff/accepted.yaml` (BH, étape, référence de décision), ajoutées uniquement en
  mode `--fix` sur retour explicite de l'auditeur.

## Vérification sur step-00

`app` et `app-ref` servent alors le même code : la suite doit être **verte avec zéro écart**. C'est la preuve que les
normalisations sont suffisantes et l'environnement déterministe. Exécute `.claude/bin/tests.sh --suite diff`.

## Rapport final

- Parcours écrits (zone → BH couverts), normalisations ajoutées et justification, résultat sur step-00.

## Après la skill

La session principale lance **`/step-commit`** (mode hors marche) pour les fichiers produits (règle « Commit automatique des tests » de
`CLAUDE.md` ; l'agent `test-writer` n'a pas accès à git). Signaler dans le rapport final la liste des fichiers à commiter.
