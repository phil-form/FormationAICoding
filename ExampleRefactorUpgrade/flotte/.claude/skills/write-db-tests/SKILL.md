---
name: write-db-tests
description: Écrit les tests d'état de la base (effets exacts des actions qui écrivent, intégrité, non-dérive du schéma SQLite par rapport à step-00) à partir des fiches BH de couche db et de l'inventaire des entités, via l'agent isolé test-writer.
when_to_use: Dans /build-test-suite après les tests HTTP/CLI ; complément après /test-plan --gaps ; correction (--fix).
argument-hint: "[<zone>|schema] [--fix]"
context: fork
agent: test-writer
---

# Tests d'état de la base et de non-dérive du schéma

Arguments : `$ARGUMENTS` — zone, ou `schema` pour le seul test de schéma.
Référence : `.claude/reference/conventions.md` § 5.2, § 5.3 ; plan § 6.1 (« schéma figé »).

Règles, sources autorisées, gel, `--fix`, « vert sur `app-ref` » et squelette : **identiques à
`.claude/skills/write-http-tests/SKILL.md`**.

## Spécificités

1. **Non-dérive du schéma** — `tests/src/Db/SchemaTest.php` :
   - le DDL de la base de la cible (après chargement du scénario `empty` par l'**application**, pas par le chargeur)
     est extrait de `sqlite_master`, normalisé (espaces, ordre des index) et comparé au golden
     `tests/golden/db/schema.sql` enregistré sur step-00 ;
   - toute différence échoue avec le diff lisible : c'est à `/failure-triage` de l'arbitrer (DBAL 3, types
     `datetime`…), pas au test de la tolérer.
2. **Effets des écritures** — `tests/src/Db/<Zone>StateTest.php` : pour chaque fiche `db` (création de réservation,
   clôture, ajout de véhicule, `fleet:demo:load`) : état avant (scénario), action (via `HttpClient` ou `Console`),
   **dump canonique** des tables concernées (tri par clé primaire, colonnes dans l'ordre du DDL, dates au format
   stocké) comparé à l'état attendu de la fiche ; vérifier aussi ce qui **ne doit pas** changer (autres lignes,
   autres tables).
3. **Intégrité** : unicité de `plate`, clés étrangères orphelines, statuts hors constantes — tels qu'observés,
   y compris s'ils sont permissifs (bug pour bug).

## Étapes

1. Comme `write-http-tests` étapes 1–2 ; lis aussi `docs/migration/inventory/step-00/entities.json`.
2. Écris les tests ; exécute `.claude/bin/tests.sh --suite db` sur la référence.
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
