# Couche `cli` — commandes console

Sortie normalisée, code de retour, effets en base (zones `cli-*` : `fleet:report`, `fleet:demo:load`).
Référence : plan § 3.1. Inventaire utile : `commands.json`.

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

## Exécution

`.claude/bin/tests.sh --suite cli --filter <Zone>Test --target ref`.
