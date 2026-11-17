---
name: write-cli-tests
description: Écrit les tests boîte noire des commandes console (sortie normalisée, code de retour, effets en base) à partir des seules fiches BH de couche cli, via l'agent isolé test-writer.
when_to_use: Dans /build-test-suite pour les zones cli-* (fleet:report, fleet:demo:load) ; complément après /test-plan --gaps ; correction (--fix).
argument-hint: "<zone> [--fix]"
context: fork
agent: test-writer
---

# Tests des commandes console

Arguments : `$ARGUMENTS`. Référence : `.claude/reference/conventions.md` § 5.2 ; plan § 3.1.

Règles, sources autorisées, gel, mode `--fix`, exigence « vert sur `app-ref` » et squelette du projet de tests :
**identiques à `.claude/skills/write-http-tests/SKILL.md`** (lis-la d'abord).

## Spécificités

- Classe `tests/src/Cli/<Zone>Test.php`, une méthode par fiche `BH-xxx` de couche `cli`.
- Exécution via `Support/Console.php` : `run(<target>, <commande>, <options>)` → `{exitCode, stdout, stderr}`.
  Il s'appuie sur la variable `CONSOLE_<TARGET>_CMD` fournie par l'environnement (cf. `docker-compose.yml`, service
  `tests`) ; si elle est absente, le test est marqué **incomplet** avec un message explicite, jamais vert.
- Assertions :
  - code de retour exact ;
  - sortie **normalisée** (fins de ligne, espaces de fin, largeur de tableau) puis comparée à un golden
    (`Golden::assertMatchesGolden('cli/<zone>/<bh>', …)`) : séparateurs de milliers, décimales, symbole €, ordre
    des lignes, ligne TOTAL ;
  - effets en base via `Db` (`fleet:demo:load` purge puis insère : comparer le dump canonique) ;
  - option par défaut dépendant de l'horloge (`--month` absent) : la date figée (`FAKETIME`) donne la valeur
    attendue ; l'écrire dans le test à partir de la fiche, pas de `date()` dans le test.
- Cas limites issus des fiches : mois invalide, mois 13, mois sans données, km égaux (ordre des ex-æquo).

## Étapes

1. Comme `write-http-tests` étapes 1–2.
2. Écris les tests ; exécute `.claude/bin/tests.sh --suite cli --filter <Zone>Test` sur la référence.
3. Rapport (même format que `write-http-tests`).

## Mise à jour du document de fiches (obligatoire après chaque implémentation)

Document concerné : **`tmp/tests-application.md`** (document unique ; section du groupe testé : § 2
`VehicleController`, § 3 `ReservationController`, § 4 `ApiController`, § 5 `AdminController`, § 6 commandes). Le
mettre à jour **dans toutes ses parties** (`.claude/reference/conventions.md` § 5.6) :

1. **Lignes de fiches** : pour chaque fiche implémentée dont la valeur observée sur `app-ref` diffère de la fiche, ou
   était « à confirmer » / « déduite », corriger la cellule avec la **valeur observée** et ajouter
   `(observé à l'implémentation)`. Jamais de fiche supprimée ni renumérotée ; jamais de valeur ajustée sans
   observation sur la référence.
2. **Journal d'implémentation** (section unique en fin de document, avant « Blocages ») : une ligne par fiche —
   `| date | BH-NNN | Classe::méthode | suite(s) | golden(s) | app-ref ✔/✘ | écart corrigé oui/non |`.
3. **Blocages** : `- BH-NNN : ⏸ <raison>` pour chaque fiche non implémentée faute de décision, de reproductibilité ou
   d'environnement ; retirer la ligne d'un blocage levé.
4. **Points à trancher** (sous-section du groupe) : ne rien décider ; ajouter `→ bloque BH-NNN` à la ligne concernée si nécessaire.
5. **Exemple complet** du document : aligner ses valeurs sur le test si elles ont changé.
6. **Bloc de suivi** : lancer `.claude/bin/tests.sh --suite <suite> --target ref` (qui régénère le bloc), puis
   `.claude/bin/test-tracking.py` après les dernières modifications.

Rapport final : lister les fiches dont la ligne a été corrigée (avant → après) et les blocages ajoutés.

## Après la skill

La session principale lance **`/step-commit`** (mode hors marche) pour les fichiers produits **et le document de fiches mis à jour** (règle « Commit automatique des tests » de
`CLAUDE.md` ; l'agent `test-writer` n'a pas accès à git). Signaler dans le rapport final la liste des fichiers à commiter.
