---
name: list-entities
description: Inventorie les entités Doctrine et le schéma SQLite réel (tables, colonnes, types, nullabilité, index, relations, callbacks de cycle de vie, repositories) dans docs/migration/inventory/step-NN/entities.json.
when_to_use: Appelée par /inventory ; seule, avant une marche qui touche à Doctrine (ORM, DBAL, annotations → attributs) ou pour vérifier la non-dérive du schéma.
argument-hint: "[--step step-NN]"
context: fork
agent: cartographer
---

# Inventaire des entités et du schéma

Arguments : `$ARGUMENTS`. Référence : `.claude/reference/conventions.md` § 5.3 (clé `class`).

## Sources

1. **Mapping** : annotations `@ORM\…` (≤ 5.x) ou attributs `#[ORM\…]` ; `.claude/bin/console.sh doctrine:mapping:info`
   pour la liste des entités reconnues.
2. **Schéma réel** : `.claude/bin/console.sh doctrine:schema:validate` (mapping ↔ base) et
   `.claude/bin/console.sh doctrine:schema:update --dump-sql` (doit être **vide** ; sinon dérive à signaler).
   Pour le DDL exact : base du scénario `empty` (`tests/fixtures/db/empty.sqlite`) ou, à défaut,
   `.claude/bin/console.sh doctrine:query:sql "SELECT name, sql FROM sqlite_master WHERE type IN ('table','index') ORDER BY name"`.
3. **Comportements** : `@HasLifecycleCallbacks` (`@PrePersist`, `@PreUpdate`), valeurs par défaut des propriétés,
   constantes de statut, méthodes de repository personnalisées (nom, paramètres, tri appliqué).

## Structure

```json
{
  "class": "AppBundle\\Entity\\Vehicle",
  "table": "vehicle",
  "repository": "AppBundle\\Repository\\VehicleRepository",
  "repository_methods": [ { "name": "findAllOrderedByPlate", "params": [], "order_by": ["plate ASC"] } ],
  "columns": [
    { "property": "plate", "column": "plate", "type": "string", "length": 20, "nullable": false, "unique": true, "sqlite_type": "VARCHAR(20)" }
  ],
  "relations": [ { "property": "reservations", "type": "OneToMany", "target": "AppBundle\\Entity\\Reservation", "mapped_by": "vehicle", "fetch": "LAZY", "order_by": {} } ],
  "lifecycle_callbacks": { "prePersist": ["onPrePersist"] },
  "constants": { "STATUS_AVAILABLE": 0, "STATUS_MAINTENANCE": 1, "STATUS_OUT_OF_SERVICE": 2 },
  "defaults": { "mileage": 0, "status": 0 },
  "indexes": [],
  "schema_drift": []
}
```

- `sqlite_type` vient du DDL réel : c'est lui qui détecte une dérive DBAL 2 → 3 (ex. `DATETIME` vs
  `DATETIME(0)`, ordre des colonnes, index renommés).
- Le nom d'entité court (`AppBundle:Vehicle`) disparaît en 6.x : ne pas l'utiliser comme clé.
- Tri par `class`, colonnes dans l'ordre du DDL.

## Étapes

1. Collecte ; écris `docs/migration/inventory/<STEP>/entities.json`.
2. Rapport : entités, colonnes, dérive mapping ↔ schéma, tri implicite des collections (piège : ordre non garanti
   sans `order_by`).
