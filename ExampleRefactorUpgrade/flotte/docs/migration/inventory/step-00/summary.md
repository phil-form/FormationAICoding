# Inventaire step-00 (référence PHP 7.2 / Symfony 2.8)

Produit par `/inventory all --snapshot` (agent `cartographer`, 2026-11-17), en lecture seule sur l'application
step-00 (`app` = `app-ref`, tag `flotte-migration/step-00`). Chaque élément des fichiers JSON porte un champ
`provenance` (observé par la console / déduit de la lecture du code). Valeurs sensibles masquées par `"<redacted>"`.

| Fichier | Clé stable (conventions § 5.3) | Éléments | Remarque |
|---|---|---:|---|
| `routes.json` | `name` | 7 | aucune route technique (pas de profiler) |
| `commands.json` | `name` | 2 | `fleet:demo:load`, `fleet:report` (46 commandes du framework exclues) |
| `services.json` | `id` | 13 | 5 `app.*` + 8 services du framework utilisés par le code |
| `forms.json` | `block_prefix` | 3 | `reservation`, `vehicle`, `form` (formulaire anonyme du retour) |
| `entities.json` | `table` | 2 | 2 tables, 16 colonnes |
| `templates.json` | `path` (logique) | 6 | |
| `config.json` | `key` | 51 | dont 29 avec un effet observable |

Contrôle de complétude : les 7 routes et 2 commandes du `README.md` sont présentes, rien d'autre n'existe.

## Déduit, non observé

- Routes : authentification, rôles, paramètres de requête, corps, formulaire, conversion de paramètre, type de
  réponse, redirections ; contrôleur complet (la console ne donne que `AppBundle:Xxx:yyy`).
- Commandes : codes de retour, effets en base, format de sortie (observés par les tests du § 6 de
  `tmp/tests-application.md`).
- Services : arguments (non affichés par la console 2.8) et lieux d'utilisation.
- Formulaires : entièrement (pas de `debug:form` en 2.8).
- Configuration en environnement `prod` : `debug:config` exige le mode debug ; seules les valeurs `dev` sont observées.

## Anomalies relevées (à suivre pendant la migration)

1. `homepage` (`/`) accepte toutes les méthodes HTTP.
2. `app.twig.fleet_extension` est privé et inliné : introuvable par son identifiant, seulement par son tag.
3. `security.context` utilisé dans `AdminController` (supprimé en Symfony 3.0) ; accès au conteneur par `get()` /
   `getContainer()` partout.
4. `doctrine:schema:validate` échoue (faux positif DBAL 2.5 : `COLLATE BINARY`) alors que le DDL réel est identique
   au mapping et à `tests/fixtures/schema.sql`.
5. Formulaires : `isValid()` sans `isSubmitted()` (×3), `new XxxType()`, types par nom, `choices` à la manière 2.8.
6. Templates : `{% for … if %}` (×2), `{% spaceless %}`, notation `'::base.html.twig'` à côté de `'base.html.twig'`.
7. Aucune contrainte `UniqueEntity` sur la plaque (seul l'index unique protège, d'où les 500 de BH-309).
8. Traducteur désactivé ; `twig.strict_variables` vrai en console, faux en HTTP.
9. Description de `fleet:demo:load` sans accents (« donnees de demonstration »).
10. Secrets en clair (secret Symfony de démo, encodeur `plaintext`, mot de passe dans `security.yml`) : transmis à
    `/vuln-audit`.

## Décision prise

Les formulaires et les entités étaient prévus avec la clé `class`, qui change en step-02 (`AppBundle\…` → `App\…`) et
aurait fait signaler tous ces éléments comme disparus. Clés stables retenues : `block_prefix` et `table`
(conventions § 5.3, skills `list-forms` et `list-entities` mis à jour) ; `class` reste un champ comparé.
