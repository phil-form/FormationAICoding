# Inventaire des commandes console

> Inventorie les commandes console applicatives (nom, description, arguments, options, valeurs par défaut, codes de retour, effets) dans docs/migration/inventory/step-NN/commands.json.

Référence : `.claude/reference/conventions.md` § 4 et § 5.3 (clé `name`).

## Sources

1. `.claude/bin/console.sh list --format=json` (≥ 2.8 ; sinon `--format=xml`), puis
   `.claude/bin/console.sh help <nom> --format=json` pour chaque commande applicative.
2. Code de la commande (`configure()`, `#[AsCommand]`, `execute()`) : codes de retour (`return 0/1`,
   `Command::SUCCESS/FAILURE`, exceptions), sorties `writeln`, accès base (lecture / écriture / purge).

Ne retenir que les commandes **applicatives** (namespace `fleet:` et toute commande déclarée dans `src/`). Les
commandes du framework sont seulement comptées dans le résumé (leur liste change à chaque version).

## Structure

```json
{
  "name": "fleet:report",
  "description": "Rapport mensuel d'utilisation de la flotte",
  "aliases": [],
  "arguments": [],
  "options": [
    { "name": "month", "shortcut": "m", "mode": "required", "default": "<date('Y-m') à l'exécution>", "description": "…" }
  ],
  "exit_codes": { "0": "succès", "1": "format de mois invalide" },
  "output": "table | text",
  "db_effects": "read | write | purge+write",
  "class": "AppBundle\\Command\\FleetReportCommand",
  "file": "src/AppBundle/Command/FleetReportCommand.php"
}
```

- Une valeur par défaut calculée (date, horloge) est décrite, pas recopiée (sinon l'inventaire dépend du jour).
- Tri par `name`.

## Contrôle de complétude

Attendues d'après le README : `fleet:report --month=AAAA-MM`, `fleet:demo:load`.

## Étapes

1. Collecte ; écris `docs/migration/inventory/<STEP>/commands.json`.
2. Rapport : commandes, options, écarts avec le contrôle de complétude.
