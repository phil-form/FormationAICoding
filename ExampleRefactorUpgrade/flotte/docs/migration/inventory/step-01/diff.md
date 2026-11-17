# Inventaire step-01 : écarts avec step-00 (Symfony 2.8.52 → 3.4.49, PHP 7.2)

Comparaison : `.claude/bin/json-diff.py docs/migration/inventory/step-00/<type>.json docs/migration/inventory/step-01/<type>.json --key <clé> --strict`
(clés stables, conventions § 5.3). Base de comparaison : `step-00`.

| Type | Clé | Ajoutés | Supprimés | Modifiés | `--strict` | Alerte `G-INVENTORY` |
|---|---|---:|---:|---:|---|---|
| config | `key` | 2 | 0 | 2 | code 1 | non |
| routes | `name` | 0 | 0 | 2 | code 1 | non |
| commands | `name` | 0 | 0 | 0 | code 0 | non |
| services | `id` | 4 | 1 | 10 | code 1 | non (les services ne sont pas couverts par la gate) |
| forms | `block_prefix` | 0 | 0 | 3 | code 1 | non, avec un point à confirmer (voir Formulaires) |
| entities | `table` | 0 | 0 | 0 | code 0 | non |
| templates | `path` | 0 | 0 | 0 | code 0 | non |

Le code 1 de `--strict` signale toute suppression ou modification, quel que soit le champ. Chaque écart est classé
ci-dessous :

- **attendu** : changement prévu par la marche, sans effet sur une interface (route, méthode, nom, prérequis, commande,
  option, formulaire, champ, table, colonne) ;
- **G-INVENTORY** : disparition ou modification d'une interface.

**Verdict : `G-INVENTORY` non déclenchée.** Aucune route, commande, aucun formulaire, champ, aucune entité, colonne
ni aucun template n'a disparu ou changé. Le seul point à confirmer est l'équivalence de rendu de `vehicle[status]`
(voir Formulaires) : elle est déduite du code, je ne l'ai pas observée.

## Routes

| Élément | Champ | Avant → après | Classement |
|---|---|---|---|
| `reservation_new` | `file` | `ReservationController.php:17` → `:19` | attendu (deux `use` ajoutés en tête de fichier) |
| `reservation_return` | `file` | `ReservationController.php:46` → `:48` | attendu (même cause) |

Les 7 routes ont les mêmes noms, chemins, méthodes, prérequis, hôtes, schémas et défauts dans `debug:router` 3.4 et
dans step-00. Les rôles, l'authentification, les paramètres, les formulaires et les types de réponse sont inchangés
(déduit du code). `homepage` accepte toujours toutes les méthodes (`ANY`).

Hors inventaire : la nouvelle écoute `app.listener.legacy_allow_header` rétablit l'en-tête `Allow` des réponses 405
(`HEAD` ajouté après `GET`) sans modifier les routes.

## Commandes

Aucun écart. Les deux commandes `fleet:*` ont la même description, la même usage et la même option `--month`
(`-m`, valeur requise, défaut `date('Y-m')`, observé à `2026-01`).

Information (code, sans effet sur l'inventaire) : `fleet:report` lève maintenant elle-même
`The "--month" option requires a value.` quand la valeur est vide. En 2.8, c'est le parseur de la console qui
renvoyait cette erreur. Le code de sortie reste 1. Ce comportement est couvert par les tests CLI, que je n'ai pas
relancés ici.

Commandes du framework : 45 en 3.4 contre 46 en 2.8. Elles sont exclues de l'inventaire. ACC-002 couvre les options
globales `--shell` et `--process-isolation`, qui ont disparu.

## Services

| Élément | Champ | Avant → après | Classement |
|---|---|---|---|
| `security.context` | (élément) | présent → **disparu** | attendu : service supprimé en Symfony 3.0. Son seul consommateur (`AdminController.php:21`) appelle maintenant `isGranted()`, qui passe par `security.authorization_checker`. Plus aucun `get('security.context')` dans `src/` |
| `security.authorization_checker` | (élément) | — → ajouté | attendu : remplace `security.context` (helper `isGranted()`, et `is_granted` dans `base`) |
| `app.listener.legacy_allow_header` | (élément) | — → ajouté | attendu : rétablit l'en-tête `Allow` des 405 de 2.8 (`kernel.exception`, priorité 64) |
| `app.session.handler.native` | (élément) | — → ajouté | attendu : `\SessionHandler` natif, comme 2.8 avec `handler_id: ~`. Il est inliné sous l'id `session.handler` dans `session.storage.native` (observé dans le conteneur compilé) |
| `logger` | (élément) | — → ajouté | attendu : `Psr\Log\NullLogger` qui remplace le logger stderr par défaut de 3.4 (2.8 n'en avait aucun) |
| `app.listener.version_header` | `public` | `true` → `false` | attendu (voir la note sur `public`) |
| `app.listener.version_header` | `tags` | + `container.hot_path` | attendu : tag interne ajouté par le compilateur 3.4 |
| `app.plate_formatter` | `public` | `true` → `false` | attendu, **dépréciation à traiter** (voir la note) |
| `app.reservation_manager` | `public` | `true` → `false` | attendu, **dépréciation à traiter** (voir la note) |
| `app.reservation_manager` | `consumed_by` | lignes 30/30/59 → 36/32/65 | attendu (lignes déplacées) |
| `app.validator.belgian_plate` | `public` | `true` → `false` | attendu (voir la note) |
| `app.twig.fleet_extension` | `provenance` | `id`, `public` et `arguments` passent de déduit à observé | information : `debug:container --show-private` 3.4 trouve le service. Il reste inliné dans `twig` (`in_compiled_container: false`, observé) |
| `form.factory` | `consumed_by` | lignes 25/51 → 27/53 | attendu |
| `router` | `alias_of`, `consumed_by`, `provenance` | `null` → `router.default`, lignes 33/65 → 35/71 | information : alias constaté par la console 3.4. La classe cible est inchangée |
| `security.token_storage` | `tags` | + `kernel.reset` | attendu : tag ajouté par le framework 3.4 |
| `session` | `consumed_by` | lignes 31/60 → 33/66 | attendu |
| `templating` | `alias_of`, `consumed_by`, `provenance` | `null` → `templating.engine.twig`, lignes 39/71 → 41/77 | information : alias constaté. La classe cible `TwigEngine` est inchangée |

Note sur `public` : en 3.4, `debug:container` affiche `Public: no` pour tout service qui n'est pas déclaré
explicitement public. C'est le cas de tous les services de `app/config/services.yml`, qui n'a pas de `_defaults`. Le
journal des dépréciations confirme la conséquence (`php-logs.sh deprecations --summary`, observé) :
`The "app.reservation_manager" service is private, getting it from the container is deprecated since Symfony 3.2 and will fail in 4.0`
(514 occurrences), et la même chose pour `app.plate_formatter` (325). Les deux services restent accessibles par
`get()` en 3.4. Il faudra les rendre publics ou les injecter avant la marche 4.4 : cela relève de `G-DEPREC` et de
`/fix-deprecations`, pas de cette gate.

## Formulaires

| Élément | Champ | Avant → après | Classement |
|---|---|---|---|
| `reservation`, `vehicle`, `form` | `fields[].type_raw` | `'text'`, `'datetime'`, `'integer'`, `'choice'` → `TextType::class`, `DateTimeType::class`, `IntegerType::class`, `ChoiceType::class` | attendu : `type_raw` garde la notation d'origine. Le `type` normalisé est inchangé |
| `vehicle` (`mileage`), `form` (`endKm`) | `fields[].view_transformer` | — → `LegacyIntegerToLocalizedStringTransformer` | attendu : conserve l'analyse 2.8 (une décimale est arrondie vers le bas au lieu d'être rejetée) |
| `vehicle` (`status`) | `fields[].options.choices_as_values` | défaut 2.8 (`false`) → option supprimée en 3.1, choices déclarés `libellé => valeur` via `array_flip()` | attendu, **à confirmer** : la liste normalisée valeur → libellé `{0: Disponible, 1: En entretien, 2: Hors service}` et son ordre sont identiques (déduit du code). Je n'ai pas observé le rendu HTML |
| `reservation`, `vehicle` | `instantiation` | `new XxxType()` → `createForm(XxxType::class)` | attendu |
| `reservation`, `vehicle` | `name_source` | `getName()` → `getBlockPrefix()` | attendu. Le `block_prefix` reste `reservation` / `vehicle` (**observé** par `debug:form`), donc les noms HTML `reservation[…]` / `vehicle[…]` sont conservés |
| `reservation`, `vehicle`, `form` | `submission_check` | `isValid()` sans `isSubmitted()` → `isSubmitted() && isValid()` | attendu : en 2.8, `isValid()` renvoyait déjà `false` pour un formulaire non soumis (déduit) |
| `reservation`, `vehicle`, `form` | `provenance` | `block_prefix` et `class` observés par `debug:form` (3.4) | information |

Inchangés : noms de champs, `html_name`, types normalisés, libellés, formats de date `dd/MM/yyyy HH:mm`, contraintes,
CSRF (`_token`), `data_class`, routes utilisatrices.

## Entités

Aucun écart. Le DDL réel (`sqlite_master`, observé) est identique octet pour octet à step-00 : 2 tables, 16 colonnes,
index `IDX_42C84955545317D1` et `UNIQ_1B80E486719ED75B`, clé étrangère `FK_42C84955545317D1`.
`doctrine:schema:update --dump-sql` affiche toujours le même faux positif `COLLATE BINARY` (Doctrine ORM 2.5.14,
inchangé).

## Templates

Aucun écart. Les 6 templates sont inchangés (`git status`) et `lint:twig` les valide (observé). Twig est toujours
en 1.42.5.

## Configuration

| Clé | Avant → après | Classement |
|---|---|---|
| `framework.session.handler_id` | `null` → `app.session.handler.native` | attendu : conserve le gestionnaire natif 2.8. Avec `null`, 3.4 l'aurait encapsulé dans `StrictSessionHandler`, ce qui supprime le cookie des sessions vides |
| `framework.session.use_strict_mode` | — → `true` (clé nouvelle, défaut 3.4) | information, observable mais **sans effet constaté** : la clé n'apparaît pas dans `session.storage.options` (`cache_limiter`, `cookie_httponly`, `gc_probability`, observé) parce que `handler_id` est explicite. `session.use_strict_mode` de PHP reste donc à 0, comme en 2.8 |
| `security.firewalls.main.anonymous` | `{secret: <redacted>}` → `{secret: null}` | attendu : en 3.4, la clé interne du jeton anonyme n'est plus résolue dans la configuration. Aucun effet visible (déduit) |
| `security.firewalls.main.logout_on_user_change` | — → `false` (clé nouvelle, dépréciée) | information : la valeur sera imposée à `true` en 4.0 (dépréciation journalisée 6 fois). À suivre en step-02 |

Inchangés : paramètres métier (`app.cost_per_km` 0.35, `app.version` 1.4.2, `locale` fr), CSRF, `access_control`,
`http_basic`, encodeur, firewalls, Twig (formats de date et de nombre, `strict_variables`, `autoescape`), Doctrine
(stratégie de nommage).
