# Inventaire des services

> Inventorie les services applicatifs du conteneur Symfony (id, classe, arguments, tags, visibilité, alias) et repère les accès par $this->get()/getContainer() à migrer, dans docs/migration/inventory/step-NN/services.json.

Référence : `.claude/reference/conventions.md` § 4 et § 5.3 (clé `id`).

## Sources

1. `.claude/bin/console.sh debug:container --format=json` (≥ 2.8 ; sinon `container:debug`), avec
   `--show-private` si l'option existe ; détail : `debug:container <id> --format=json`.
2. Fichiers `services.yml` / `config/services.yaml`, attributs `#[Autowire]`, `#[AsTwigExtension]`, tags.
3. **Consommateurs** : `$this->get('…')`, `$this->container->get`, `getContainer()->get`, `$this->getDoctrine()`,
   `$this->get('session')`, `security.context`… dans contrôleurs, commandes, extensions Twig.

Ne retenir que les services **applicatifs** : ids `app.*`, classes sous `src/`, et les services du framework
**consommés** par le code applicatif (ex. `security.context`, `session`, `doctrine`) — ces derniers avec
`"framework": true`.

## Structure

```json
{
  "id": "app.reservation_manager",
  "class": "AppBundle\\Service\\ReservationManager",
  "public": true,
  "aliases": [],
  "arguments": ["@doctrine.orm.entity_manager", "%app.cost_per_km%"],
  "tags": [],
  "framework": false,
  "consumed_by": ["src/AppBundle/Controller/ReservationController.php:31"]
}
```

- Après le passage à l'autowiring (step-02+), l'id devient souvent le FQCN : conserver la correspondance dans
  `aliases` (ou noter `"renamed_from"`) pour que `/inventory --diff` ne lève pas de faux « disparu ».
- Tri par `id`.

## Étapes

1. Collecte ; écris `docs/migration/inventory/<STEP>/services.json`.
2. Rapport : services applicatifs, accès conteneur à migrer (`$this->get`, services supprimés comme
   `security.context`), services du framework consommés.
