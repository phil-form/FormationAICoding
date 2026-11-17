---
name: list-routes
description: Inventorie les routes HTTP de l'application (nom, chemin, méthodes, prérequis, contrôleur, rôle exigé, paramètres de requête lus, type de réponse) dans docs/migration/inventory/step-NN/routes.json.
when_to_use: Appelée par /inventory ; seule, pour lister les routes d'une étape ou avant d'écrire les tests HTTP.
argument-hint: "[--step step-NN]"
context: fork
agent: cartographer
---

# Inventaire des routes

Arguments : `$ARGUMENTS` (étape ; par défaut `STEP` de `docker/step.env`).
Référence : `.claude/reference/conventions.md` § 4 et § 5.3 (clé `name`).

## Sources (par ordre de fiabilité)

1. **Console** — `.claude/bin/console.sh debug:router --format=json` (Symfony ≥ 2.8 ; si la sous-commande
   n'existe pas : `router:debug`, puis `debug:router` sans `--format` et analyse du tableau texte). Pour chaque
   route : `.claude/bin/console.sh debug:router <nom> --format=json` (contrôleur, options, défauts).
2. **Code** — annotations `@Route` / `@Method` (Sensio, ≤ 5.x), attributs `#[Route(methods: …)]` (≥ 5.2/6.x),
   fichiers `routing.yml` / `config/routes*.yaml`, préfixes de classe.
3. **Sécurité** — `access_control` de `security.yml` / `config/packages/security.yaml`, `isGranted()` /
   `#[IsGranted]` / `denyAccessUnlessGranted()` dans le contrôleur → `roles`.
4. **Contrôleur** — paramètres lus : `$request->query->get('x')`, `->request->get`, `ParamConverter` / entity value
   resolver, formulaires traités (`handleRequest`) → `query_params`, `body`, `form`.
5. **Réponse** — `render`/`@Template` → `html` ; `JsonResponse`/`json()` → `json` ; `redirect*` → `redirect` ;
   plusieurs issues → liste.

## Structure

```json
{
  "name": "vehicle_index",
  "path": "/vehicules",
  "methods": ["GET"],
  "requirements": {},
  "defaults": {},
  "host": "",
  "schemes": [],
  "controller": "AppBundle\\Controller\\VehicleController::indexAction",
  "roles": [],
  "auth": "anonymous | http_basic | …",
  "query_params": ["status"],
  "body": [],
  "form": null,
  "param_conversion": [],
  "response_type": ["html"],
  "redirects_to": [],
  "source": "annotation | attribute | yaml",
  "file": "src/AppBundle/Controller/VehicleController.php:24"
}
```

- `methods` vide dans la console = toutes méthodes : écrire `["ANY"]`.
- `requirements` : exclure `_method`/`_scheme` (déjà dans `methods`/`schemes`) pour rester comparable entre versions.
- Exclure les routes techniques (`_wdt`, `_profiler*`, `_twig_error_test`, `_preview_error`) mais les compter dans le
  résumé.
- Tri par `name`, clés d'objet dans l'ordre ci-dessus.

## Contrôle de complétude (pas une source)

Le README annonce : `/vehicules` (filtre `?status=`), `/vehicules/{id}`, `/vehicules/{id}/reserver`,
`/reservations/{id}/retour`, `/api/vehicules`, `/admin/vehicules/nouveau` (Basic auth), plus `/` (redirection).
Toute route attendue non trouvée, ou trouvée sans être documentée, est signalée.

## Étapes

1. Collecte via les sources 1 à 5 ; en cas de désaccord console / code, la **console fait foi** et le désaccord est
   noté.
2. Écris `docs/migration/inventory/<STEP>/routes.json`.
3. Rapport : nombre de routes, désaccords, contrôle de complétude.
