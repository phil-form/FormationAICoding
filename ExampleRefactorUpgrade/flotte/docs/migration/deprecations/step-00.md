# Dépréciations — step-00 (PHP 7.2 / Symfony 2.8), cible step-01 (Symfony 3.4)

> Inventaire en lecture seule (`/deprecation-check --snapshot --target sf-3.4`), 2026-11-17. Aucune correction :
> les lots ci-dessous sont destinés à `/fix-deprecations` (agent `migrator`).
> Suivi : `docs/migration/deprecations.json` (fusion `track-merge.py`, 47 entrées `open`, DEP-0001 à DEP-0047).
> Constats bruts : `.claude/state/migration/deprecations/step-00-findings.json`.

## 1. Synthèse

**Gate `G-DEPREC` levée** : 7 dépréciations supprimées par Symfony 3.0, donc bloquantes pour step-01.

| Bloque | Nombre | Identifiants |
|---|---|---|
| **step-01** (SF 3.4) | **7** | DEP-0001 à DEP-0007 |
| step-02 (SF 4.4, Flex) | 3 | DEP-0008, DEP-0009, DEP-0029 |
| step-04 (SF 5.4, Twig 3) | 16 | DEP-0011 à 0019, 0021, 0022, 0033, 0035, 0036, 0037, 0038 |
| step-05 (PHP 8.1) | 4 | DEP-0043 à DEP-0046 (incompatibilités de comportement) |
| step-06 (SF 6.4) | 7 | DEP-0020, DEP-0023 à DEP-0028 |
| non bloquantes | 10 | DEP-0010, 0030, 0031, 0032, 0034, 0039 à 0042, 0047 |
| **Total** | **47** | statut `open` (étape de référence : pas de `introduced`) |

Par source : runtime Symfony 7 · runtime PHP 3 · Rector 2 · PHPStan 1 · lecture statique 15 · configuration 6 ·
Doctrine 2 · Twig 5 · dépendances 6.

### Bloquantes pour step-01 (toutes `removed_in` = symfony 3.0, toutes vues au runtime)

| ID | Signature | Emplacements | Occurrences app / ref | Correction | Risque |
|---|---|---|---|---|---|
| DEP-0001 | symfony/form: form type instance passed to createForm()/FormFactory instead of its FQCN | `ReservationController:25`, `AdminController:26` | 3 119 / 3 635 | Rector `FormTypeInstanceToClassConstRector` (non déclenchée en simulation) → manuelle triviale | faible |
| DEP-0002 | symfony/form: FormTypeInterface::getName() implemented by a form type (use getBlockPrefix()) | `ReservationType:35`, `VehicleType:33` | 3 119 / 3 635 | manuelle (renommer en `getBlockPrefix()`, même valeur) | **moyen** : noms des champs HTML |
| DEP-0003 | symfony/form: form type referenced by its string name instead of its FQCN | `ReservationType` (text, datetime×2), `VehicleType` (text×3, integer, choice), `ReservationController:52` (integer) | 13 387 / 15 596 | Rector `StringFormTypeToClassRector` (partielle, à relancer) + manuelle pour `returnAction` | faible |
| DEP-0004 | symfony/form: ChoiceType option choices_as_values=false (choice keys used as submitted values) | `VehicleType:19-22` | 1 015 / 1 248 | **manuelle** | **élevé** |
| DEP-0005 | symfony/form: legacy ArrayKeyChoiceList choice list instantiated | conséquence de DEP-0004 | 1 015 / 1 248 | disparaît avec DEP-0004 | — |
| DEP-0006 | symfony/security: security.context service fetched from the container | `AdminController:21` | 1 015 / 1 248 | manuelle (`$this->isGranted()` / `security.authorization_checker`) | faible |
| DEP-0007 | symfony/security: SecurityContext class instantiated | conséquence de DEP-0006 | 1 015 / 1 248 | disparaît avec DEP-0006 | — |

Les signatures runtime sont **identiques** sur `app` (23 685 lignes) et `app-ref` (27 858 lignes) : le code de
travail n'a pas divergé de la référence. Aucun `E_DEPRECATED` (PHP) n'a été émis en PHP 7.2.

## 2. Familles

### 2.1 Formulaires (DEP-0001 à 0005, 0008, 0022) — lot 1 et lot 2

- **Correction** : FQCN des types (`TextType::class`…), `createForm(ReservationType::class, …)`, `getName()` →
  `getBlockPrefix()` en conservant `reservation` / `vehicle` (la valeur par défaut dérivée du nom de classe est la même,
  mais l'expliciter évite toute dérive), `ChoiceType` : choix inversés `libellé => valeur`.
- **DEP-0004, point d'attention** : en 2.8, passer `choices_as_values => true` **et** inverser `getStatusLabels()`
  (sans modifier la méthode de l'entité, utilisée aussi par la liste et l'API). Dès Symfony 3.1, l'option
  `choices_as_values` est elle-même dépréciée : la **retirer** à step-01. À préserver : valeurs d'option `0/1/2`,
  option sélectionnée par défaut, rejet d'une valeur hors liste (goldens `admin-vehicle-new/bh323-3`, `bh323-abc`).
- **DEP-0008** (`isValid()` sans `isSubmitted()`, exception en 4.0) : pré-enregistrée pour éviter un `introduced` au
  runtime de step-01 ; corrigeable dès maintenant (`isSubmitted() && isValid()`, même résultat sur GET).
- **DEP-0022** (`format` de `DateTimeType` avec `html5` actif, exception en 5.0) : `'html5' => false` à step-04 ;
  risque élevé (type d'input, analyse `dd/MM/yyyy HH:mm`, dates invalides).

### 2.2 Sécurité (DEP-0006, 0007, 0023 à 0026) — lot 3

- step-01 : `security.context` → `isGranted()`. Le refus reste `AccessDeniedException` ; `access_control ^/admin`
  intervient avant le contrôleur (401 Basic inchangé).
- step-06 : `User` → `InMemoryUser`, `encoders` → `password_hashers` (garder `plaintext`), `anonymous` → système
  d'authenticators (risque élevé : 401/403 et défi Basic), `getUsername()` → `getUserIdentifier()`.

### 2.3 Routage et SensioFrameworkExtraBundle (DEP-0011 à 0013, 0037) — lot 5 (step-01 possible, obligatoire step-04)

- `@Route`/`@Method` Sensio → `@Route(methods=…)` Symfony : **Rector applicable** (`ReplaceSensioRouteAnnotationWithSymfonyRector`,
  `MergeMethodAnnotationToRouteAnnotationRector`, déclenchées sur les 4 contrôleurs), possible dès SF 3.4.
- `@Template()` deviné et `ParamConverter` implicite : manuels ; le nom de template deviné change avec la structure Flex
  (step-02), le 404 sur identifiant inconnu doit rester identique.

### 2.4 Contrôleurs, console, événements, configuration (DEP-0009, 0014 à 0021, 0027, 0028) — step-02 / step-04 / step-06

- step-02 : enregistrement des commandes comme services (DEP-0009 ; dépréciation émise à partir de 3.4, en CLI seulement).
- step-04 : `Controller` → `AbstractController`, `ContainerAwareCommand`, `kernel.root_dir`, `framework.templating`,
  notation `::base.html.twig`, `FilterResponseEvent`, composant `Debug` (la plupart absorbés par la structure Flex).
- step-06 : `isMasterRequest()`, service `session`, `getDoctrine()`.

### 2.5 Doctrine (DEP-0029 à 0032)

- **DEP-0029** (alias `AppBundle:Entity` dans `getRepository()` et en DQL) : bloquante à step-02, l'alias disparaît
  avec `AppBundle` → `App`. Correction : `Vehicle::class` / FQCN en DQL.
- Annotations ORM, stratégie `underscore`, `AnnotationRegistry` : non bloquants dans le plan actuel (bloquants
  seulement si ORM 3 est retenu à step-06).

### 2.6 Twig (DEP-0018, 0033 à 0036)

Twig 1.42 n'émet **aucune** dépréciation pour ces syntaxes (vérifié dans le CHANGELOG 1.x) : constats statiques.
`{% spaceless %}` (déprécié 2.7) et `{% for … if %}` (déprécié 2.10) sont supprimés en Twig 3 → step-04 ; classes
`Twig_*` → namespaces (Rector) ; `getName()` de l'extension sans effet.

### 2.7 Dépendances (DEP-0037 à 0042)

Abandonnés : `sensio/framework-extra-bundle` (remplacé par le cœur Symfony), `doctrine/doctrine-cache-bundle`,
`doctrine/cache`, `doctrine/common`, `doctrine/annotations`. `symfony/security-acl` est requis mais non enregistré
dans `AppKernel` (retrait prévu par le plan). Rien de bloquant pour step-01 (FEB 3.0.29, DoctrineBundle 1.6 et
Twig 1.42 acceptent Symfony 3.4).

### 2.8 PHP (DEP-0043 à 0047) — incompatibilités, à confirmer par `/compat-scan --php 8.1`

Ce ne sont pas des dépréciations Symfony mais des **changements de comportement** de PHP 8.0, visibles dès step-00
dans `error.log` (Warning/Notice en PHP 7.2) :

| ID | Constat | Risque |
|---|---|---|
| DEP-0043 | `number_format()` reçoit une chaîne non numérique (`FleetExtension` : filtres `km`, `euros`) — Warning + `null` en 7.2, **TypeError** (500) en 8 | élevé |
| DEP-0044 | arithmétique sur chaîne non numérique (`ReservationManager::close`, `monthlyReport`) — TypeError en 8 | élevé |
| DEP-0045 | `usort()` avec un comparateur booléen — tri stable en 8 : ordre des ex-æquo de `fleet:report` | élevé |
| DEP-0046 | `0 != 'abc'` (filtre `?status=abc`, option sélectionnée de la liste) — résultat inversé en 8 | élevé |
| DEP-0047 | lecture d'un index absent (`Vehicle::getStatusLabel`) — Notice → Warning, valeur inchangée | faible |

Chacune exigera une décision « bug pour bug » consignée dans `docs/migration/accepted-changes.md` avant step-05.

## 3. Plan de lots pour `/fix-deprecations` (une famille par lot)

| Lot | Marche | Contenu | Méthode | Tests à surveiller |
|---|---|---|---|---|
| **1 — Formulaires (types)** | step-00 → 01 | DEP-0001, 0002, 0003 (+ 0008 conseillé) | Rector `StringFormTypeToClassRector` (relancer jusqu'à stabilité) puis manuel : `createForm(X::class)`, `getBlockPrefix()`, `returnAction` ; `isSubmitted() && isValid()` | http/diff `reservation_new`, `reservation_return`, `admin_vehicle_new` ; noms de champs HTML |
| **2 — Formulaires (ChoiceType)** | step-00 → 01 | DEP-0004, 0005 | manuel, isolé (risque élevé) | goldens `admin-vehicle-new` (statuts, valeur hors liste `bh323-*`) |
| **3 — Sécurité** | step-00 → 01 | DEP-0006, 0007 | manuel : `$this->isGranted('ROLE_ADMIN')` | 401/403 `/admin`, création de véhicule |
| 4 — Console (préparation) | step-01 → 02 | DEP-0009 | manuel : services `console.command` | suite cli (**à exécuter** : jamais lancée) |
| 5 — Routage Sensio | step-01 (facultatif) → 04 | DEP-0011 (Rector), 0012, 0013 | Rector puis manuel | 404/405, ordre des routes |
| 6 — Doctrine alias | step-01 → 02 | DEP-0029 | manuel (FQCN) | API, liste, rapport, `fleet:demo:load` |
| 7+ | step-02 à step-06 | familles 2.4, 2.6, 2.2 (step-06), 2.8 (step-05) | voir § 2 | — |

Les lots 1 à 3 lèvent la gate pour step-01. Ils peuvent être appliqués en Symfony 2.8 (toutes les API de
remplacement existent en 2.8) : la suite gelée doit rester verte avant la montée.

## 4. Diff avec l'étape précédente

Sans objet (step-00 est la référence). Prochaine exécution : `/deprecation-check --diff --target sf-4.4` à step-01.
Attendus à step-01 sans alerte `introduced` (pré-enregistrés) : DEP-0008, DEP-0009. Peuvent apparaître : dépréciation
de l'option `choices_as_values` elle-même (3.1) si le lot 2 la conserve, services privés obtenus par `get()`.

## 5. Couverture et limites

- **Runtime HTTP uniquement** : toutes les occurrences sont `apache2handler`. Aucune ligne CLI dans le journal et pas
  de `junit-cli.xml` : `fleet:report`, `fleet:demo:load` (et `app/console`) ne sont **pas exercés**. De plus,
  `app/console` appelle `Debug::enable()` hors `prod`, ce qui remplace le gestionnaire du journal de dépréciations :
  une suite CLI en `dev` ne journaliserait rien. Les constats console (DEP-0009, 0015, 0021, 0029 en DQL, 0044/0045)
  sont **statiques**.
- **Non servis** : `web/app_dev.php`.
- **Dépréciations de compilation** (Twig, conteneur) : émises une seule fois à la construction du cache `prod` ;
  non garanties dans le journal. Pas de `lint:twig --show-deprecations` en 2.8 ; `cache:clear -v` / `debug:config`
  non lancés (environnement partagé avec un autre agent).
- **Routes sans aucune dépréciation au runtime** : `vehicle_index`, `vehicle_show`, `api_vehicles`, `homepage` (leurs
  constats sont statiques : Sensio, alias Doctrine, Twig).
- **Outils** : PHPStan 2.3.6 niveau 2 (PHP 7.2) ne voit pas les appels via `$this->get('…')` (conteneur non typé) ;
  Rector 2.8.5 n'a plus de sets Symfony 2/3 et ses règles sont liées à la version installée : exécution en
  `--dry-run` avec un manifeste simulant Symfony 4.4 (`.claude/state/migration/deprecations/rector-target/`).
  `FormTypeInstanceToClassConstRector`, `FormIsValidRector`, `RemoveDefaultGetBlockPrefixRector` ne se sont pas
  déclenchées (inférence de type) : correction manuelle.
