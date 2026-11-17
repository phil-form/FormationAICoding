# Dépréciations — step-01 (PHP 7.2 / Symfony 3.4.49), cible step-02 (Symfony 4.4)

> Inventaire en lecture seule (`/deprecation-check --diff --target sf-4.4`), 2026-11-17. Aucune correction : les lots
> ci-dessous sont destinés à `/fix-deprecations` (agent `migrator`).
> Constats bruts : `.claude/state/migration/deprecations/step-01-findings.json` (41 signatures).
> Suivi : `docs/migration/deprecations.json`, fusion par `track-merge.py` (voir § 6 : fusion simulée, en attente de
> l'empreinte de `composer.lock`).

## 1. Synthèse

| Statut après fusion | Nombre | Identifiants |
|---|---|---|
| `fixed` (fixed_in_step = step-01) | **9** | DEP-0001 à DEP-0008, DEP-0010 |
| `introduced` (nouvelles, à arbitrer) | **3** | DEP-0048, DEP-0049, DEP-0050 |
| `open` persistantes | **38** | voir § 3 |
| Total ouvert | **41** | |

**Gate `G-DEPREC` levée** : 6 dépréciations bloquantes pour step-02 et 3 `introduced` non arbitrées.

| Bloque | Nombre | Identifiants |
|---|---|---|
| **step-02** (SF 4.4, Flex) | **6** | DEP-0048, DEP-0049, DEP-0009, DEP-0029, DEP-0012*, DEP-0018* |
| step-04 (SF 5.4, Twig 3) | 15 | DEP-0011, 0013 à 0017, 0019, 0021, 0022, 0033, 0035 à 0038, **0050** |
| step-05 (PHP 8.1) | 4 | DEP-0043 à DEP-0046 |
| step-06 (SF 6.4) | 7 | DEP-0020, DEP-0023 à DEP-0028 |
| non bloquantes | 9 | DEP-0030, 0031, 0032, 0034, 0039 à 0042, 0047 |

\* reclassées de step-04 à step-02 (voir § 2.4).

Par source (ouvertes) : runtime Symfony 3 (DEP-0009, 0048, 0049) · runtime PHP 3 · Rector 2 (DEP-0011, 0050) ·
lecture statique 14 · configuration 6 · Doctrine 2 · Twig 5 · dépendances 6.

### Bloquantes pour step-02

| ID | Signature | Emplacements | Occurrences app | Correction | Risque |
|---|---|---|---|---|---|
| **DEP-0048** (nouv.) | symfony/dependency-injection: private service fetched from the container with get() | `ReservationController` (newAction, returnAction), `FleetReportCommand::execute`, `AdminController` (app.plate_formatter) ; définitions `services.yml` sans `public` | 839 (514 + 325) | manuelle : `public: true` sur `app.reservation_manager` et `app.plate_formatter` (Rector `GetToConstructorInjectionRector` non déclenchée) | faible |
| **DEP-0049** (nouv.) | symfony/security-bundle: firewall logout_on_user_change not enabled (always true since 4.0) | `security.yml`, firewall `main` | 6 (compilation) | manuelle : `logout_on_user_change: true` maintenant, retirer la clé à step-02 (dépréciée en 4.1) | faible |
| DEP-0009 | symfony/console: command auto-registered from the bundle Command/ directory | `FleetReportCommand`, `LoadDemoDataCommand` | 102 (51 par commande, **désormais vue au runtime**) | manuelle : services `console.command` | **élevé si oublié** : les commandes disparaissent en 4.0 |
| DEP-0029 | doctrine/persistence: short entity namespace alias (Bundle:Entity) in getRepository() or DQL | `VehicleController:32`, `ApiController:21`, `ReservationManager:35,91`, `LoadDemoDataCommand:24-25` (DQL) | statique | manuelle : `Vehicle::class`, FQCN en DQL | faible |
| DEP-0012 | sensio/framework-extra-bundle: @Template annotation with guessed template name | `VehicleController:25,53` | statique | avec la restructuration (`render()` explicite ou emplacement exact) | moyen |
| DEP-0018 | symfony/twig-bundle: legacy bundle template notation (::template or Bundle:Dir:file) | `Vehicle/index.html.twig:1`, `Vehicle/show.html.twig:1` | statique | manuelle : `'base.html.twig'` (déjà utilisé par les autres vues) | faible |

## 2. Familles

### 2.1 Formulaires — résolues (DEP-0001 à 0005, 0008)

Lots 1 et 2 de step-00 → 01 : plus aucune occurrence au runtime depuis l'offset 19523657 du journal (avant :
3 119 à 13 387 par signature). DEP-0008 (`isValid()` sans `isSubmitted()`) est corrigée dans le code (3 appels
`isSubmitted() && isValid()`) : pas d'émission en 3.4. Reste DEP-0022 (`format` de `DateTimeType` + html5, émise
au runtime dès Symfony 4.3, bloque step-04).

**Point de vigilance step-02 (hors dépréciation)** : `Form/DataTransformer/LegacyIntegerToLocalizedStringTransformer`
(ajouté à step-01) hérite de `IntegerToLocalizedStringTransformer` et appelle la méthode du grand-parent
`NumberToLocalizedStringTransformer::reverseTransform()`. La signature du constructeur change en 4.2 (`$scale`
retiré) : l'appel sans argument reste valide, mais le comportement interne hérité doit être revérifié après le
composer update (fiches des kilométrages décimaux, `reservation_return`, `admin_vehicle_new`).

### 2.2 Sécurité (DEP-0006, 0007 résolues ; DEP-0049 nouvelle ; DEP-0023 à 0026 step-06)

- DEP-0006/0007 : lot 3 (`isGranted()`), plus d'émission.
- **DEP-0049** : en 4.0 le jeton est invalidé si l'utilisateur rechargé diffère (mot de passe, rôles, nom).
  Utilisateur in-memory immuable et `http_basic` ré-authentifie chaque requête : impact attendu nul. Activer
  l'option dès step-01 permet de **tester le comportement 4.0 sous la suite gelée** avant la montée.

### 2.3 Services et contrôleurs (DEP-0048 nouvelle ; DEP-0014, 0027, 0028)

- **DEP-0048** : en 4.0, les services sont privés par défaut et `get()` lève `ServiceNotFoundException`
  (500 sur `reservation_new` POST valide, `reservation_return`, `admin_vehicle_new` POST valide, `fleet:report`
  en erreur). `public: true` est la correction minimale (mêmes instances, aucun changement de comportement).
  L'injection par constructeur relève de la structure Flex (step-02).
- **DEP-0014** (`Controller` → `AbstractController`) : Rector la propose désormais (`RenameClassRector`), mais
  **à n'appliquer qu'après** la suppression des `$this->get('app.*')` : `AbstractController::get()` ne connaît
  que ses services abonnés.

### 2.4 Routage, templates, SensioFrameworkExtraBundle (DEP-0011, 0012, 0013, 0018, 0037)

- DEP-0011 : Rector applicable dès Symfony 3.4 (`ReplaceSensioRouteAnnotationWithSymfonyRector`,
  `MergeMethodAnnotationToRouteAnnotationRector`, `RemoveServiceFromSensioRouteRector`), 4 contrôleurs, 6 routes.
- **Reclassement DEP-0012 et DEP-0018 (step-04 → step-02)** : l'API n'est supprimée qu'en 5.0, mais les deux
  dépendent du bundle et de `app/Resources/views`, que la restructuration Flex de step-02 fait disparaître
  (`'::base.html.twig'` → introuvable ; `@Template()` deviné `vehicle/index.html.twig` sous `templates/` au lieu
  de `AppBundle:Vehicle:index.html.twig`). Même logique que DEP-0029 (alias `AppBundle:`).
  DEP-0018 se corrige dès step-01 ; DEP-0012 se traite avec le déplacement des templates dans `/upgrade-step`.

### 2.5 Console (DEP-0009, DEP-0015)

DEP-0009 est maintenant **confirmée au runtime** (la suite cli tourne : 51 occurrences par commande, à chaque
démarrage de la console). En 4.0, `fleet:report` et `fleet:demo:load` disparaissent si elles ne sont pas déclarées
en services. Garder des déclarations non paresseuses (sans attribut `command` sur le tag) pour ne rien changer à
`list` / `help` (ACC-002 porte déjà sur ces sorties).

### 2.6 Événements (DEP-0019, DEP-0020, DEP-0050 nouvelle)

- **DEP-0050** : `LegacyAllowHeaderListener` (ajouté à step-01 pour préserver l'en-tête `Allow` des 405 de 2.8)
  utilise `GetResponseForExceptionEvent`, `getException()`, `setException()` : dépréciés en 4.3/4.4, supprimés en
  5.0. Rector (`RenameClassRector` + `RenameMethodRector`) les convertit, mais les remplaçants **n'existent pas en
  3.4** : à appliquer après le composer update de step-02 (même contrainte pour DEP-0019 `FilterResponseEvent`).

### 2.7 Doctrine, Twig, configuration, dépendances, PHP — persistantes, sans changement de classement

- Doctrine : DEP-0029 (step-02, ci-dessus), DEP-0030/0031/0032 non bloquantes.
- Twig 1.42 n'émet toujours rien ; Twig 2 (step-02) émettra DEP-0035 (`spaceless`), DEP-0036 (`for … if`),
  DEP-0033 (`Twig_*`, Rector).
- Configuration : DEP-0016 (`kernel.root_dir`), 0017 (`templating`), 0021 (`Debug`) absorbées par la structure Flex.
- PHP (DEP-0043 à 0047) : toujours présentes dans `error.log` (`number_format()` sur chaîne, arithmétique non
  numérique, index absent) ; aucune `E_DEPRECATED` en PHP 7.2.

### 2.8 Dépendances : prérequis de `/upgrade-step` step-02 (non suivis comme dépréciations)

`composer why-not symfony/symfony 4.4.0 --locked` : seul **`sensio/framework-extra-bundle` 3.0.29** l'interdit
(`symfony/dependency-injection ~2.3|~3.0`) → passer en 5.x. `doctrine/doctrine-bundle` 1.6.13 accepte `~4.0` par
contrainte (cible `targets.json` : ORM 2.7, DBAL 2.13) ; `symfony/security-acl` 3.0.4 accepte `^4.0`.
Abandonnés inchangés : DEP-0037 à DEP-0041 ; `symfony/security-acl` toujours requis sans être utilisé (DEP-0042).

## 3. Diff avec step-00

| | Nombre | Détail |
|---|---|---|
| Nouvelles (`introduced`) | 3 | DEP-0048 (services privés, runtime), DEP-0049 (`logout_on_user_change`, runtime), DEP-0050 (`GetResponseForExceptionEvent`, Rector, code ajouté à step-01) |
| Résolues (`fixed`) | 9 | DEP-0001 à 0007 (lots 1 à 3, runtime nul), DEP-0008 (code corrigé au lot 1), DEP-0010 (vendor 3.4 : `ConstraintValidator::$context` typé sur l'interface non dépréciée, code inchangé, aucun avis runtime) |
| Persistantes | 38 | dont DEP-0009 passée de « statique » à « runtime », emplacements mis à jour (DEP-0013, 0022, 0027, 0028, 0031), corrections Rector précisées (DEP-0011, 0014, 0019, 0029, 0033), reclassement step-02 (DEP-0012, 0018) |
| Réapparitions | 0 | |

Attendus de step-00 : DEP-0008 et DEP-0009 étaient pré-enregistrées (pas d'`introduced`) : DEP-0008 a été corrigée
avant d'émettre, DEP-0009 émet bien. Les services privés obtenus par `get()`, annoncés comme possibles, sont arrivés
(DEP-0048). `choices_as_values` n'apparaît pas (option retirée au lot 2).

## 4. Plan de lots pour `/fix-deprecations` (une famille par lot)

| Lot | Moment | Contenu | Méthode | Tests à surveiller |
|---|---|---|---|---|
| **4 — Services privés** | step-01, avant montée | DEP-0048 | manuel : `public: true` sur `app.reservation_manager`, `app.plate_formatter` | `reservation_new` (POST valide, chevauchement), `reservation_return`, `admin_vehicle_new` (POST valide), cli `fleet:report` |
| **5 — Console** | step-01, avant montée | DEP-0009 | manuel : 2 services tag `console.command` (non paresseux) | suite cli complète, `list`/`help` (ACC-002), codes de retour |
| **6 — Sécurité** | step-01, avant montée | DEP-0049 | manuel : `logout_on_user_change: true` (firewall `main`) ; retirer la clé à step-02 | 401 + défi Basic `/admin`, création de véhicule, lien d'administration de la nav |
| **7 — Doctrine (alias)** | step-01, avant montée | DEP-0029 | manuel : `::class` dans `getRepository()`, FQCN en DQL | `vehicle_index`, `api_vehicles`, `reservation_new` (chevauchement), `fleet:report`, `fleet:demo:load` |
| **8 — Templates** | step-01 (DEP-0018) / `/upgrade-step` step-02 (DEP-0012) | DEP-0018, DEP-0012 | manuel : `{% extends 'base.html.twig' %}` ; `@Template` traité avec le déplacement Flex | goldens `vehicle_index`, `vehicle_show` (HTML complet) |
| 9 — Routage Sensio (facultatif) | step-01 ou step-02 | DEP-0011 | Rector (3 règles Symfony34) | 404/405 (en-tête `Allow`, cf. DEP-0050), ordre des routes, `requirements` |
| 10 — Après composer update | step-02 | DEP-0050, DEP-0019, DEP-0033, puis DEP-0014 (après lot 4 + injection) | Rector `RenameClassRector` / `RenameMethodRector` | 405 (`Allow`), en-tête `X-Fleet-Version`, filtres Twig |

Les lots 4 à 7 et DEP-0018 lèvent la partie « bloquantes step-02 » de la gate ; ils sont tous applicables sous
Symfony 3.4 avec la suite gelée.

## 5. Couverture et limites

- **Runtime HTTP et CLI** : la suite complète (404 tests, 0 échec) a tourné sur `app` en 3.4 ; le journal
  contient désormais des lignes CLI. `app-ref` (2.8) n'émet aucune des 3 dépréciations 3.4 (normal).
- **Pas de mesure de couverture à step-01** (`.claude/state/migration/coverage/summary.json` absent) : impossible
  d'affirmer que chaque appel `get('app.*')` ou chaque branche a été exécuté ; les emplacements de DEP-0048 sont
  issus de la lecture du code, croisée avec les 839 occurrences.
- **Non servis / non exercés** : `web/app_dev.php` (DEP-0021) ; `app/console` en `dev` active `Debug::enable()`, qui
  peut remplacer le gestionnaire du journal de dépréciations (les 102 lignes CLI montrent qu'au moins le démarrage
  est journalisé).
- **Dépréciations de compilation** (conteneur, Twig) : émises à la construction du cache seulement (6 pour
  DEP-0049). `cache:clear -v` / `debug:config` non lancés (écriture du cache de l'environnement partagé).
  `lint:twig --show-deprecations` indisponible en 3.4.
- **PHPStan** (niveau 2, PHP 7.2) : aucune règle de dépréciation déclenchée. Le vendor 3.4 ne marque pas encore
  `@deprecated` les API supprimées en 5.0 et PHPStan ne voit pas `$this->get('…')`. Les erreurs remontées
  (`Doctrine\Persistence\ManagerRegistry` inconnue, `findAllOrderedByPlate()`, `Constraint::$message`) relèvent du
  typage, pas des dépréciations.
- **Peuvent apparaître à step-02** (non pré-enregistrées) : « Auto-injection of the container » si des contrôleurs
  ne sont pas déclarés en services, dépréciations propres à DoctrineBundle 2 / ORM 2.7 (DEP-0031 attendue).
- **Ce que les tests n'exercent pas** : changement d'utilisateur en session (DEP-0049) ; erreurs de compilation du
  conteneur.

## 6. Fusion

Fusion simulée (`--dry-run`) : `new 3`, `fixed 9`, `open 41`, `reappeared 0`, alertes = les 3 `introduced`.
La fusion réelle n'a pas été lancée par l'agent : aucun script autorisé ne calcule l'empreinte SHA-256 de
`composer.lock`, et une seconde fusion pour l'ajouter ferait passer les `introduced` en `open` sans arbitrage.
Commande à lancer dans la session principale (code retour 1 attendu : alertes `introduced`) :

```
.claude/bin/track-merge.py deprecations --step step-01 --php 7.2 --symfony 3.4 --lock-sha <sha256 de composer.lock> --input .claude/state/migration/deprecations/step-01-findings.json
```
