---
name: write-edge-tests
description: Écrit un test ciblé par piège de migration connu (tri des ex-æquo, comparaisons lâches, dates, arrondis, null vers fonctions internes, serialize_precision, Twig for…if, isValid sans isSubmitted) à partir des fiches BH de couche edge, via l'agent isolé test-writer.
when_to_use: Dans /build-test-suite après les tests HTTP/CLI ; à chaque nouveau piège identifié par /compat-scan ou /deprecation-check ; correction (--fix).
argument-hint: "[<piège>] [--fix]"
context: fork
agent: test-writer
---

# Tests des cas limites de migration

Arguments : `$ARGUMENTS` — un piège particulier, sinon tous. Référence : plan `tmp/plan-migration-php84-sf64.md`
§ 0 (« Pièges bug pour bug ») et § 3.3 ; `.claude/reference/conventions.md` § 5.2.

Règles, sources autorisées, gel, `--fix`, « vert sur `app-ref` » et squelette : **identiques à
`.claude/skills/write-http-tests/SKILL.md`**.

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

## Étapes

1. Comme `write-http-tests` étapes 1–2 ; lis le plan § 0.
2. Écris les tests ; exécute `.claude/bin/tests.sh --suite edge` sur la référence.
3. Rapport : matrice piège → BH → test, pièges sans fiche.

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
