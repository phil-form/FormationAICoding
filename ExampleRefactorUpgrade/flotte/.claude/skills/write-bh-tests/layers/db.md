# Couche `db` — état de la base et non-dérive du schéma

Cible : une zone, ou `schema` pour le seul test de schéma. Référence : conventions § 5.3 ; plan § 6.1 (« schéma
figé »). Inventaire utile : **`docs/migration/inventory/step-00/entities.json`** (à lire en étape 1).

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

## Exécution

`.claude/bin/tests.sh --suite db --target ref`.

## Scénarios et chargeur

Les scénarios `tests/fixtures/<scenario>.yaml`, `tests/fixtures/README.md` et le chargeur `tests/bin/seed.php`
relèvent de cette couche (voir `/seed`) ; un scénario signalé incorrect par `/seed` se corrige en `--fix`.
