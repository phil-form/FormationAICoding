# Inventaire des templates

> Inventorie les templates Twig (nom logique, héritage, blocs, inclusions, variables attendues, filtres/fonctions/tests utilisés, constructions dépréciées) dans docs/migration/inventory/step-NN/templates.json.

Référence : `.claude/reference/conventions.md` § 5.3 (clé `path` **logique**).

## Clé logique

Le chemin physique change pendant la migration :
`src/AppBundle/Resources/views/Vehicle/show.html.twig` (référence `AppBundle:Vehicle:show.html.twig` ou
`@App/Vehicle/show.html.twig`), `app/Resources/views/reservation/new.html.twig`, puis `templates/…` en step-02+.
La clé est le chemin **relatif au dossier de vues, sans extension, sans préfixe de bundle** : `Vehicle/show`,
`reservation/new`, `base`. Le chemin physique va dans `file`, la référence d'origine dans `reference`.

## Sources

1. Fichiers `*.twig` sous `app/Resources/views`, `src/*/Resources/views`, `templates/`.
2. `.claude/bin/console.sh lint:twig <dossier>` (≥ 2.7) et, si l'option existe (≥ 4.x),
   `lint:twig --show-deprecations`.
3. Contrôleurs : qui rend quel template (`render()`, `@Template()` implicite = convention de nommage !) et avec quelles
   variables.
4. Extensions Twig applicatives (`src/**/Twig/*`) : filtres et fonctions fournis.

## Structure

```json
{
  "path": "Vehicle/show",
  "file": "src/AppBundle/Resources/views/Vehicle/show.html.twig",
  "reference": "AppBundle:Vehicle:show.html.twig (implicite via @Template)",
  "extends": "base",
  "blocks": ["title", "body"],
  "includes": [],
  "rendered_by": ["vehicle_show"],
  "variables": ["vehicle"],
  "filters": ["date", "plate"],
  "functions": ["path"],
  "tests": [],
  "deprecated_constructs": [ { "construct": "for … if …", "line": 14, "removed_in": "twig 3.0" } ]
}
```

- Relever les constructions dépréciées connues : `{% for … if … %}`, `{% spaceless %}`, `{% filter %}`,
  `{% raw %}` → `verbatim`, tests `sameas`/`divisibleby`, `_self` comme nom de template, extensions sans
  `getName()` (Twig 1).
- Tri par `path`.

## Étapes

1. Collecte ; écris `docs/migration/inventory/<STEP>/templates.json`.
2. Rapport : templates, templates rendus implicitement (`@Template`), constructions dépréciées.
