# Couche `edge` — pièges de migration connus

Cible : un piège particulier, sinon tous. Référence : plan `tmp/plan-migration-php84-sf64.md` § 0 (« Pièges bug
pour bug », **à lire en étape 1**) et § 3.3.

## Principe

Un piège = **une classe** `tests/src/Edge/<Piege>Test.php`, avec au moins un test par fiche `BH` de couche `edge`
qui s'y rattache. Chaque test documente en commentaire d'en-tête : le piège, la version qui change le comportement,
la fiche. Ce sont les premiers tests à casser pendant la migration : leur message d'échec doit dire **quel**
comportement historique a changé.

## Pièges attendus (contrôle de complétude, cf. plan § 0)

| Piège | Ce que le test fige (via HTTP/CLI uniquement) | Scénario |
|---|---|---|
| Tri du rapport à km égaux (`usort` booléen, tri stable PHP 8) | ordre exact des lignes ex-æquo de `fleet:report` | `edge` |
| Comparaison chaîne/nombre (`?status=`) | liste retournée pour `abc`, `01`, ` 1`, `1abc`, `0`, vide | `nominal` |
| Statut comparé à `'confirmee'` | `nextReservation` de l'API et sections de la fiche véhicule | `edge` |
| `createFromFormat` en débordement | `fleet:report --month=2016-13`, `--month=2016-00` | `report-months` |
| Formats de date `JJ/MM/AAAA HH:MM` | réservation acceptée/refusée pour formats ambigus | `nominal` |
| Arrondis du coût | coût affiché (flash) et stocké pour km donnant ,005 | `edge` |
| `serialize_precision` | représentation JSON des nombres de l'API (octet par octet) | `edge` |
| `isValid()` sans `isSubmitted()` | GET des pages de formulaire : pas d'erreur affichée, statut 200 | `nominal` |
| `{% for … if %}` | contenu exact des sections « à venir » / « historique » | `edge` |
| Option par défaut liée à l'horloge | `fleet:report` sans `--month` sous `FAKETIME` | `report-months` |

Un piège sans fiche `BH` correspondante → pas de test, signalé (à caractériser d'abord).

## Exécution

`.claude/bin/tests.sh --suite edge --target ref`.

## Rapport (complément)

Matrice piège → BH → test ; pièges sans fiche.
