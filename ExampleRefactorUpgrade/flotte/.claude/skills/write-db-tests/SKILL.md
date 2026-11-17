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
