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
