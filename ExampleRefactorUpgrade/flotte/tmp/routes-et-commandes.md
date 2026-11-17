# Flotte — routes et commandes (step-00)

> Inventaire préalable à l'écriture des tests de caractérisation. Version de référence : **PHP 7.2.34 /
> Symfony 2.8.52** (step-00), application servie par le conteneur `app`.
>
> Sources, dans cet ordre de confiance :
> 1. **observé** : `debug:router`, `list`, `help` dans l'application réelle, et requêtes HTTP sur le scénario `demo`
>    (2026-11-17, horloge des conteneurs au 15/01/2026) ;
> 2. **lu dans le code** : contrôleurs, formulaires, services, sécurité, templates.
>
> Ce document sert de point de départ à `/inventory` (JSON comparable d'une étape à l'autre) et à `/characterize`
> (fiches `BH-xxx`). Il décrit ce que fait l'application, **bugs compris**, sans les juger.

## 1. Vue d'ensemble

7 routes (toutes déclarées par annotations `@Route` dans `src/AppBundle/Controller/`, chargées par
`app/config/routing.yml`), 4 contrôleurs, 2 commandes applicatives.

| Contrôleur | Route | Méthodes | Chemin | Prérequis | Accès | Réponse |
|---|---|---|---|---|---|---|
| `VehicleController::homeAction` | `homepage` | toutes | `/` | — | public | 302 → `/vehicules` |
| `VehicleController::indexAction` | `vehicle_index` | GET | `/vehicules` | — | public | HTML (`Vehicle/index.html.twig`) |
| `VehicleController::showAction` | `vehicle_show` | GET | `/vehicules/{id}` | `id: \d+` | public | HTML (`Vehicle/show.html.twig`) |
| `ReservationController::newAction` | `reservation_new` | GET, POST | `/vehicules/{id}/reserver` | `id: \d+` | public | HTML formulaire / 302 |
| `ReservationController::returnAction` | `reservation_return` | GET, POST | `/reservations/{id}/retour` | `id: \d+` | public | HTML formulaire / 302 |
| `ApiController::vehiclesAction` | `api_vehicles` | GET | `/api/vehicules` | — | public | JSON |
| `AdminController::newVehicleAction` | `admin_vehicle_new` | GET, POST | `/admin/vehicules/nouveau` | — | HTTP Basic, `ROLE_ADMIN` | HTML formulaire / 302 |

Sortie de `debug:router` (step-00) :

```text
  admin_vehicle_new    GET|POST   ANY      ANY    /admin/vehicules/nouveau
  api_vehicles         GET        ANY      ANY    /api/vehicules
  reservation_new      GET|POST   ANY      ANY    /vehicules/{id}/reserver
  reservation_return   GET|POST   ANY      ANY    /reservations/{id}/retour
  homepage             ANY        ANY      ANY    /
  vehicle_index        GET        ANY      ANY    /vehicules
  vehicle_show         GET        ANY      ANY    /vehicules/{id}
```

## 2. Comportements transverses (toutes les routes)

| Sujet | Comportement observé |
|---|---|
| En-tête de version | `X-Fleet-Version: 1.4.2` sur toutes les réponses principales (`VersionHeaderListener`, service `kernel.response`, paramètre `app.version`) |
| Session | `Set-Cookie: PHPSESSID=…; path=/; HttpOnly` sur les pages HTML (le layout lit le flashbag) ; **pas** sur `/api/vehicules` |
| Cache | `Cache-Control: no-cache` |
| Fuite d'information | `X-Powered-By: PHP/7.2.34` (configuration PHP par défaut) |
| Route inconnue | 404 HTML (`/inexistant`) |
| Barre oblique finale | `/vehicules/` → **404** (pas de redirection) |
| Méthode non autorisée | 405 HTML (`POST /vehicules`, `POST /api/vehicules`, `DELETE /reservations/1/retour`) |
| Id non numérique | 404 (`/vehicules/abc` : prérequis `\d+`) |
| Id inexistant | 404 (ParamConverter Doctrine : `/vehicules/999`, `/vehicules/999/reserver`, `/reservations/999/retour`) |
| Front controller de dev | `/app_dev.php/…` → 403 (accès réservé à localhost dans `web/app_dev.php`) |
| Environnement HTTP | `prod`, sans debug (`web/app.php`) : les notices et warnings PHP **ne cassent pas** la page (ils vont dans `/var/log/php/error.log`) |
| Layout (`base.html.twig`) | Titre « Flotte Delvaux & Fils », lien « Véhicules », lien « Ajouter un véhicule » **seulement si** `ROLE_ADMIN`, messages flash `success` |
| Langue / formats | Libellés en français ; kilométrage `84.210 km` (filtre `km`) ; montants `73,50 €` (filtre `euros`) ; plaques normalisées à l'affichage (filtre `plate`) |

## 3. Routes par contrôleur

### 3.1 `VehicleController`

#### `homepage` — `/` (toutes méthodes)

- Redirection **302** vers `/vehicules` (`redirect()` sans code explicite).
- Aucune restriction de méthode : `POST /` redirige aussi.

#### `vehicle_index` — `GET /vehicules`

- Paramètre de requête **`status`** (facultatif) : `0` Disponible, `1` En entretien, `2` Hors service.
- Charge **tous** les véhicules triés par plaque (`ORDER BY plate ASC`, tri binaire SQLite : minuscules après
  majuscules), puis filtre **en PHP** avec une comparaison lâche `$vehicle->getStatus() != $status`.
- Affiche : plaque (normalisée, lien vers la fiche), « marque modèle », kilométrage, libellé de statut ;
  « Aucun véhicule. » si la liste est vide. Liste déroulante de filtre (option « Tous » + 3 statuts).
- **Filtre observé sur `demo`** (3 disponibles, 1 en entretien, 1 hors service) :

  | `?status=` | Lignes | Remarque |
  |---|---:|---|
  | absent, vide | 5 | pas de filtre |
  | `0` / `1` / `2` | 3 / 1 / 1 | nominal |
  | `3` | 0 | statut inconnu |
  | `abc` | **3** | PHP 7 : `0 != 'abc'` est faux (`'abc'` → 0) → véhicules disponibles. **PHP 8 : 0 ligne** |
  | `0abc` | **3** | idem (chaîne à préfixe numérique). **PHP 8 : 0 ligne** |
  | `01`, ` 1` | 1 | chaîne numérique → 1 |

- Statut inconnu en base (scénario `anomalies`) : `getStatusLabel()` → notice « Undefined offset », libellé vide,
  page 200.

#### `vehicle_show` — `GET /vehicules/{id}`

- Titre : plaque normalisée — marque modèle ; kilométrage et statut.
- Lien « Réserver ce véhicule » **seulement si** le véhicule est disponible (`status == 0`).
- « Réservations à venir » : toutes les réservations au statut `confirmee`, **y compris passées** (retard), triées
  par date de début (`@OrderBy startAt ASC`), avec un lien « retour » ; « Aucune réservation à venir. » sinon
  (construction Twig `{% for … if … %}`, **supprimée en Twig 3**).
- « Historique » : réservations `terminee` : date de début, conducteur, km parcourus, coût.
- Réservations d'un autre statut (`annulee`, valeurs inconnues) : n'apparaissent nulle part.

### 3.2 `ReservationController`

#### `reservation_new` — `GET|POST /vehicules/{id}/reserver`

- Formulaire `ReservationType` (nom `reservation`) :

  | Champ | Type | Format / contrainte |
  |---|---|---|
  | `reservation[driverName]` | texte | « Conducteur », `NotBlank` (« Le nom du conducteur est obligatoire. ») |
  | `reservation[startAt]` | datetime `single_text` | `dd/MM/yyyy HH:mm`, `NotBlank` |
  | `reservation[endAt]` | datetime `single_text` | `dd/MM/yyyy HH:mm`, `NotBlank` |
  | `reservation[_token]` | CSRF | obligatoire |

- `GET` : formulaire affiché (200) **même pour un véhicule indisponible** ; le refus n'arrive qu'à la soumission.
- `POST` valide → `ReservationManager::create()` :
  1. véhicule non disponible → erreur de formulaire « Ce véhicule n'est pas disponible. » ;
  2. fin ≤ début → « La date de fin doit être postérieure à la date de début. » ;
  3. chevauchement avec une réservation **confirmée** du véhicule, **bornes incluses** (`startAt <= fin` et
     `endAt >= début` : deux créneaux qui se touchent se chevauchent) → « Ce véhicule est déjà réservé sur cette
     période. » ; les réservations terminées ou annulées ne bloquent pas ;
  4. sinon : `start_km` = kilométrage actuel du véhicule, statut `confirmee`, enregistrement, flash
     « Réservation enregistrée. », **302** vers la fiche du véhicule.
- Les erreurs métier sont des `RuntimeException` transformées en erreur de formulaire (page 200).
- `$form->isValid()` est appelé sans `isSubmitted()` (lève une exception à partir de Symfony 4).
- Aucune contrainte sur une date dans le passé.

#### `reservation_return` — `GET|POST /reservations/{id}/retour`

- Formulaire anonyme (`createFormBuilder`, nom `form`) : `form[endKm]` (entier, « Kilométrage au retour »),
  `form[_token]`.
- `POST` valide → `ReservationManager::close()` :
  1. statut ≠ `confirmee` (terminée **ou annulée**) → « Cette réservation est déjà clôturée. » ;
  2. `endKm` < `start_km` → « Kilométrage invalide. » ;
  3. sinon : km = `endKm − start_km`, coût = `round(km × 0,35, 2)` (`app.cost_per_km`), statut `terminee`,
     **kilométrage du véhicule = `endKm`**, flash « Retour enregistré. Coût : 35,00 € », **302** vers la fiche.
- Aucune vérification de la date (une réservation future peut être clôturée) ni du kilométrage actuel du véhicule.

### 3.3 `ApiController`

#### `api_vehicles` — `GET /api/vehicules`

- « API utilisée par l'écran de planning des dépôts ». Publique, sans authentification, sans session.
- `Content-Type: application/json` ; tableau trié par plaque ; barres obliques échappées (`15\/06\/2030`).
- Champs par véhicule :

  | Champ | Valeur |
  |---|---|
  | `id` | entier |
  | `plate` | plaque **brute** de la base (non normalisée, contrairement aux pages HTML) |
  | `label` | `"<marque> <modèle>"` |
  | `mileage` | entier |
  | `status` | libellé (« Disponible »…) |
  | `nextReservation` | début de la **première réservation confirmée** dans l'ordre des dates (`d/m/Y H:i`), **même passée** ; `""` s'il n'y en a pas |

- Exemple observé (`demo`) :
  `{"id":1,"plate":"1-ABC-123","label":"Renault Kangoo","mileage":84210,"status":"Disponible","nextReservation":"15\/06\/2030 08:00"}`

### 3.4 `AdminController`

#### `admin_vehicle_new` — `GET|POST /admin/vehicules/nouveau`

- Sécurité (`app/config/security.yml`) : firewall `main` en **HTTP Basic** (`WWW-Authenticate: Basic
  realm="Secured Area"`), `access_control ^/admin → ROLE_ADMIN`, utilisateur en mémoire `gestionnaire` /
  `flotte2016`, encodeur **`plaintext`**. Le contrôleur revérifie via `security.context` (supprimé en Symfony 3).
- Observé : sans identifiants → 401 ; mauvais mot de passe → 401 ; `gestionnaire:flotte2016` → 200.
- Formulaire `VehicleType` (nom `vehicle`) :

  | Champ | Type | Contrainte |
  |---|---|---|
  | `vehicle[plate]` | texte | `NotBlank`, `BelgianPlate` (« La plaque "%plate%" n'est pas une plaque belge valide (format 1-ABC-123). ») — la validation normalise la saisie, donc ` 1abc 123` est acceptée |
  | `vehicle[brand]` | texte | `NotBlank` |
  | `vehicle[model]` | texte | `NotBlank` |
  | `vehicle[mileage]` | entier | `GreaterThanOrEqual(0)` |
  | `vehicle[status]` | choix | 0 Disponible, 1 En entretien, 2 Hors service |
  | `vehicle[_token]` | CSRF | obligatoire |

- `POST` valide : plaque normalisée (`1-ABC-123`) avant enregistrement, `created_at` = maintenant (`PrePersist`),
  flash « Véhicule 1-ABC-123 ajouté par gestionnaire. », **302** vers la fiche.
- Plaque déjà existante : pas de contrainte `UniqueEntity`, seulement l'index unique SQLite → **erreur 500**
  attendue (à confirmer en caractérisation).

## 4. Commandes console

Exécutées par `.claude/bin/console.sh` (`app/console`, environnement **`dev` par défaut**, debug actif : au
contraire des pages HTTP, un warning PHP y devient une exception `ContextErrorException`).

### 4.1 `fleet:report` — « Rapport mensuel d'utilisation de la flotte »

```text
fleet:report [-m|--month=MONTH]     Mois au format AAAA-MM [default: date du jour, ex. "2026-01"]
```

- Valeur par défaut calculée **à la configuration** (`date('Y-m')`) : dépend de l'horloge.
- Format contrôlé par `^\d{4}-\d{2}$` ; sinon « Format de mois invalide (attendu : AAAA-MM). », **code 1**.
- `--month=2016-13` est **accepté** (titre « Rapport de flotte - 2016-13 ») : `createFromFormat` déborde sur le mois
  suivant (janvier 2017) ; code 0.
- Données : réservations **terminées** dont la date de **fin** est dans `[1er du mois 00:00, 1er du mois suivant[`,
  regroupées par plaque (nombre, km, coût cumulé), triées par km décroissants avec `usort` et un comparateur
  **booléen** (ordre des ex æquo non garanti, change en PHP 8).
- Sortie : titre `Rapport de flotte - <mois>`, tableau `Plaque | Réservations | Km | Coût`, ligne `TOTAL` ;
  km `10.181`, coût `3 563,35 €`. Exemple observé (`demo`, `--month=2016-05`) :

  ```text
  Rapport de flotte - 2016-05
  +-----------+--------------+-----+----------+
  | Plaque    | Réservations | Km  | Coût     |
  +-----------+--------------+-----+----------+
  | 1-BCD-234 | 2            | 450 | 157,50 € |
  | 1-ABC-123 | 1            | 210 | 73,50 €  |
  | 2-EFG-567 | 1            | 157 | 54,95 €  |
  | TOTAL     |              | 817 | 285,95 € |
  +-----------+--------------+-----+----------+
  ```

- Coût non numérique en base (scénario `anomalies`) : `ContextErrorException: A non-numeric value encountered`
  (PHP 8 : erreur de nature différente).

### 4.2 `fleet:demo:load` — « Vide la base et charge les données de démonstration »

- **Destructive, sans confirmation** : supprime toutes les réservations puis tous les véhicules (DQL `DELETE`).
- Charge 5 véhicules (créés le 04/01/2016 08:00) et 5 réservations (4 terminées en mai 2016, 1 confirmée le
  15/06/2030) ; ids recommencent à 1.
- Sortie : « Données de démonstration chargées : 5 véhicules, 5 réservations. », code 0.
- Résultat identique, types SQLite compris, au scénario de test `demo` (vérifié).

### 4.3 Commandes du framework disponibles (non applicatives)

Présentes en step-00 et susceptibles de changer de nom ou de disparaître pendant la migration (à ne tester que si
un usage est documenté) :

`assets:install`, `cache:clear`, `cache:warmup`, `config:dump-reference`, `debug:config`, `debug:container`,
`debug:event-dispatcher`, `debug:router`, `debug:translation`, `debug:twig`, `doctrine:cache:*` (10),
`doctrine:database:create|drop|import`, `doctrine:ensure-production-settings`, `doctrine:generate:entities`,
`doctrine:mapping:convert|import|info`, `doctrine:query:dql|sql`, `doctrine:schema:create|drop|update|validate`,
`lint:twig`, `lint:yaml`, `router:dump-apache`, `router:match`, `security:encode-password`,
`server:run|start|status|stop`, `translation:update`.

Utilisées par le README : `doctrine:schema:create`, `fleet:demo:load`, `fleet:report`.

## 5. Services et extensions utilisés par ces routes

| Service | Classe | Rôle |
|---|---|---|
| `app.reservation_manager` | `ReservationManager` | création, clôture, rapport (`app.cost_per_km` = 0,35) |
| `app.plate_formatter` | `PlateFormatter` | normalisation `1-ABC-123` (sinon saisie nettoyée : majuscules, sans espaces, tirets ni points) |
| `app.twig.fleet_extension` | `FleetExtension` | filtres `plate`, `km`, `euros` |
| (validateur) | `BelgianPlateValidator` | contrainte `@BelgianPlate` |
| (listener) | `VersionHeaderListener` | en-tête `X-Fleet-Version` |

## 6. Pièges de migration repérés dans ces routes (à couvrir par des tests)

| Route / commande | Piège | Étape concernée |
|---|---|---|
| `vehicle_index` | comparaison lâche du filtre `status` (`abc`, `0abc`) | PHP 8.0 (step-05) |
| `vehicle_show` | `{% for … if %}` | Twig 3 (step-04) |
| `reservation_new`, `reservation_return`, `admin_vehicle_new` | `isValid()` sans `isSubmitted()`, types de formulaire par nom (`'text'`), `new XxxType()` | Symfony 3.0 / 4.0 (step-01, step-02) |
| `admin_vehicle_new` | `security.context`, encodeur `plaintext`, `anonymous`, `http_basic` | Symfony 3.0 / 5.3 / 6.0 |
| toutes | annotations `@Route`, `@Method`, `@Template` (SensioFrameworkExtraBundle abandonné) | step-04 / step-06 |
| `fleet:report` | `usort` booléen (ex æquo), `createFromFormat` (mois 13), valeur par défaut horodatée, arithmétique sur coût non numérique | PHP 8.0 |
| `fleet:report`, filtres Twig | `number_format()` sur `null` ou chaîne non numérique | PHP 8.0 / 8.1 |
| toutes (commandes) | `ContainerAwareCommand`, `getContainer()->get()` | Symfony 4.2+ |
| `api_vehicles` | encodage JSON (échappement des `/`, types), plaque brute | toutes |
| toutes | en-tête `X-Fleet-Version`, cookie de session, codes 404/405, barre oblique finale | toutes |
