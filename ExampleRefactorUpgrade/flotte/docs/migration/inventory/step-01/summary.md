# Inventaire step-01 (PHP 7.2 / Symfony 3.4.49)

Produit par `/inventory all --diff` (agent `cartographer`, 2026-11-17), en lecture seule (aucun scénario, aucun test,
pas de `fleet:demo:load` ; la base du volume `db-app` a seulement été lue via `sqlite_master`). Champ `provenance`
par élément ; secrets `"<redacted>"`. Écarts détaillés : `diff.md`.

| Fichier | Clé | Éléments | step-00 | Remarque |
|---|---|---:|---:|---|
| `routes.json` | `name` | 7 | 7 | seuls les numéros de ligne des contrôleurs changent |
| `commands.json` | `name` | 2 | 2 | 45 commandes du framework exclues (46 en 2.8) |
| `services.json` | `id` | 16 | 13 | + `security.authorization_checker`, `app.listener.legacy_allow_header`, `app.session.handler.native`, `logger` (NullLogger) ; − `security.context` |
| `forms.json` | `block_prefix` | 3 | 3 | types par classe, transformateur de kilométrage hérité, préfixes inchangés |
| `entities.json` | `table` | 2 | 2 | 16 colonnes, DDL identique |
| `templates.json` | `path` | 6 | 6 | inchangés |
| `config.json` | `key` | 53 | 51 | `session.handler_id`, `logout_on_user_change` (nouvelles) |

Contrôle de complétude : les 7 routes et 2 commandes du README sont présentes, rien d'autre.

**Verdict `G-INVENTORY` : non déclenchée** (aucune interface disparue ou modifiée).

Anomalies suivies : services `app.reservation_manager` / `app.plate_formatter` privés mais appelés par `get()`
(DEP-0048, bloque step-02) ; `homepage` accepte toutes les méthodes (bug historique conservé) ; faux positif DBAL
`COLLATE BINARY` ; constructions Twig dépréciées inchangées ; secrets en clair (dérogation step-00, `/vuln-audit`).
Non observé : rendu HTML de `vehicle[status]` (couvert par les goldens BH-303 / BH-323, verts) ; effet de
`use_strict_mode` (non transmis : `handler_id` explicite).
