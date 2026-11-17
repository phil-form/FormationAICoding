# Plan de migration — Flotte Delvaux & Fils vers PHP 8.4 / Symfony 6.4

> Document de cadrage. Rien n'est codé à ce stade : il fixe l'outillage IA (skills orchestrées, agents), la stratégie
> de tests « bug pour bug », le suivi des vulnérabilités et des dépréciations, le jeu de données de référence et
> l'environnement conteneurisé.

## 0. Constat de départ

| Élément | Actuel | Cible |
|---|---|---|
| PHP | 7.2 (`config.platform.php = 7.2.24`, EOL depuis 2020) | **8.4** |
| Symfony | 2.8.52 (`symfony/symfony` monolithique) | **6.4 LTS** (composants séparés, Flex) |
| Doctrine | ORM 2.5 / DBAL 2.5, annotations | ORM 2.17+ (ou 3.x) / DBAL 3, attributs |
| Twig | 1.42 | 3.x |
| Bundles | `SensioFrameworkExtraBundle` 3 (abandonné), `security-acl` (**non utilisé**) | Attributs natifs Symfony ; suppression de `security-acl` |
| Structure | `AppBundle`, `app/`, `web/` | `src/` (namespace `App\`), `config/`, `public/` |
| Base | SQLite (`app/data/flotte.sqlite`) | SQLite (inchangé pendant la migration) |
| Tests | **aucun** | Suite de caractérisation complète |
| `composer.lock` | **absent** → dépendances transitives non figées | Versionné à chaque étape |

Volume : ~1 000 lignes PHP (4 contrôleurs, 2 entités, 2 services, 2 commandes, 1 validateur, 1 extension Twig, 6 templates).

### Chemin de migration (LTS par LTS, jamais de saut)

```
step-00 baseline   PHP 7.2 + SF 2.8   (lock + tests + audits)
step-01            PHP 7.2 + SF 3.4
step-02            PHP 7.2 + SF 4.4   (passage à la structure Flex, AppBundle → App)
step-03            PHP 7.4 + SF 4.4
step-04            PHP 7.4 + SF 5.4
step-05            PHP 8.1 + SF 5.4
step-06            PHP 8.1 + SF 6.4   (nouveau système d'authentification, password hasher, attributs)
step-07            PHP 8.4 + SF 6.4   (cible)
```

Règle à chaque marche : **zéro dépréciation bloquante** avant de passer à la suivante, suite de tests **100 % verte**,
diffs de sécurité et de dépréciations consignés, commit via `/step-commit`.

> ⚠ Symfony 6.4 n'a plus de correctifs de sécurité après **novembre 2027** (dans un an). Garder 7.4 LTS en
> ligne de mire comme étape suivante ; l'outillage décrit ici est réutilisable tel quel.

### Pièges « bug pour bug » déjà repérés dans le code

| Endroit | Comportement actuel | Ce qui change |
|---|---|---|
| `ReservationManager::monthlyReport` | `usort` avec un comparateur qui renvoie un **booléen** | PHP 8 : tri stable et comparateur booléen déprécié → l'ordre des ex-æquo (km égaux) peut changer |
| `VehicleController::indexAction` | `$vehicle->getStatus() != $status` | PHP 8 : nouvelle comparaison chaîne/nombre → `?status=abc` ne renvoie plus la même liste |
| `ApiController`, templates | `== 'confirmee'` | comparaisons lâches à figer par test |
| `Vehicle/show.html.twig` | `{% for … if … %}` | Supprimé en Twig 3 |
| `AdminController`, `ReservationController` | `$form->isValid()` sans `isSubmitted()` | Exception à partir de Symfony 4 |
| `AdminController` | `security.context`, `new VehicleType()`, type `'integer'` | Supprimés en 3.0 |
| `security.yml` | encodeur `plaintext`, `anonymous`, `http_basic` | Remplacés par le password hasher et les authenticators en 5.3/6.x |
| `monthlyReport` | `createFromFormat` avec `--month=2016-13` | Comportement de débordement à figer |
| `ReservationType` | dates `JJ/MM/AAAA HH:MM` | Parsing des dates à figer |
| Coût | `round($km * 0.35, 2)` puis `number_format` | Arrondis et `serialize_precision` (JSON des flottants) |
| `FleetReportCommand` | `date('Y-m')` comme valeur par défaut, `number_format` | Dépend de l'horloge ; `null` passé à des fonctions internes est déprécié en 8.1 |

---

## 1. Skills : architecture en trois niveaux

Les skills sont rangées en trois niveaux. Un **orchestrateur** enchaîne des **skills d'étape**, qui s'appuient sur
des **skills outils** (atomiques, réutilisables, sans décision). L'utilisateur peut appeler n'importe quel niveau à la
main ; l'orchestrateur ne fait qu'automatiser l'enchaînement et les contrôles de passage.

```
NIVEAU 1 — orchestrateurs     /migrate   /upgrade-symfony   /upgrade-php   /build-test-suite
                                   │              │                │               │
NIVEAU 2 — étapes             /baseline  /upgrade-step  /php-step  /test-plan  /characterize  /fix-deprecations
                              /failure-triage  /step-report
                                   │
NIVEAU 3 — outils             /env  /seed  /inventory (+ /list-*)  /deprecation-check  /compat-scan
                              /vuln-audit  /write-*-tests  /golden-master  /test-run  /coverage  /mutation-check
                              /step-commit
```

> **Nommage** : les skills Java du dépôt parent (`/deprecations`, `/run-tests`, `/audit-deps`, `/write-tests`,
> `/triage-failures`, `/mutation-test`) restent visibles depuis `flotte/`. Les skills PHP portent donc des noms
> **distincts** pour éviter toute ambiguïté de déclenchement.

### 1.1 Niveau 1 — orchestrateurs

#### `/upgrade-symfony <cible> [--until <step>] [--auto|--interactive] [--dry-run] [--resume]`

Monte Symfony LTS par LTS jusqu'à la cible (6.4 par défaut). Pour **chaque** version intermédiaire :

```
 1. pré-requis      /env up · /test-run (suite verte sur l'étape courante, sinon arrêt)
 2. état initial    /inventory all --snapshot · /deprecation-check --snapshot · /vuln-audit --snapshot
 3. nettoyage       /fix-deprecations --target <version suivante>   (boucle jusqu'à 0 bloquante)
                    └─ après chaque lot : /test-run → si rouge : /failure-triage
 4. compat PHP      la version suivante exige-t-elle un PHP plus récent ? → appel de /upgrade-php <min requis>
 5. montée          /upgrade-step <sf-version>   (composer, recettes Flex, Rector, config)
 6. vérification    /test-run (boîte noire + cas limites + différentiel vs app-ref)
                    └─ si rouge : /failure-triage → correction (max N itérations) ou arrêt
 7. contrôles       /inventory all --diff · /deprecation-check --diff · /vuln-audit --diff
 8. bilan           /step-report <step>   → docs/migration/steps/step-NN.md
 9. point de passage (gate) → validation utilisateur si --interactive ou si une alerte est levée
10. /step-commit
```

#### `/upgrade-php <cible> [--until <step>] [--auto|--interactive] [--dry-run] [--resume]`

Même squelette, centré sur le langage :

```
1. pré-requis      /env up · /test-run vert
2. analyse         /compat-scan <cible> · /deprecation-check --php <cible>
3. nettoyage       /fix-deprecations --php <cible>   (Rector sets PHP, corrections manuelles ciblées)
4. montée          /php-step <cible>   (image Docker, platform composer, extensions, php.ini)
5. vérification    /test-run · /failure-triage si rouge
6. contrôles       /deprecation-check --diff · /vuln-audit --diff (runtime PHP inclus)
7. /step-report · gate · /step-commit
```

Il peut être appelé seul ou par `/upgrade-symfony` (étape 4) quand une version de Symfony impose un PHP minimal
(5.4 → PHP 7.2.5, 6.4 → PHP 8.1).

#### `/build-test-suite [--zone <zone>] [--until-thresholds]`

Construit la suite de caractérisation sur step-00 (avant toute migration) :

```
/env up --ref · /seed all
/inventory all              → ce qui existe (routes, commandes, formulaires, entités, templates…)
/test-plan                  → matrice de ce qu'il faut couvrir
/characterize <zone>        → fiches BH-xx (par zone, en parallèle)
/write-http-tests · /write-cli-tests · /write-db-tests · /write-unit-tests · /write-edge-tests · /write-diff-tests
/golden-master --record
/test-run · /coverage · /mutation-check
└─ boucle tant que seuils non atteints : lacunes → /test-plan --gaps → nouvelles fiches et tests
```

#### `/migrate [--auto|--interactive] [--resume]`

Orchestrateur maître (en second temps) : `/baseline` → `/build-test-suite` → alternance de `/upgrade-symfony` et
`/upgrade-php` selon le chemin du § 0 → bilan final (comportements acceptés, sécurité, dépréciations).

#### Règles communes aux orchestrateurs

- **Exécution dans la session principale** : un sous-agent ne peut pas lancer d'autres sous-agents. L'orchestrateur
  tourne donc dans la conversation et délègue chaque étape à l'agent compétent (§ 2).
- **État reprenable** : `.claude/state/migration/state.json` (étape en cours, sous-étape, itérations de correction,
  dernières empreintes). `--resume` repart du dernier point validé.
- **Arrêt automatique** (même en `--auto`) :
  - tests rouges après N itérations de correction (N = 3 par défaut) ;
  - changement de comportement candidat à l'acceptation (décision humaine obligatoire) ;
  - vulnérabilité `introduced` ;
  - dépréciation nouvelle ou bloquante non résolue ;
  - route, commande ou service disparu de l'inventaire ;
  - modification détectée de `tests/`, des golden masters ou des jeux de données par l'agent de migration.
- **`--dry-run`** : produit le plan (versions, paquets, recettes, sets Rector, dépréciations à traiter) sans rien
  modifier.
- **Un commit par sous-étape réussie** (via `/step-commit`) : chaque marche est révocable isolément.

### 1.2 Niveau 2 — skills d'étape

| Skill | Rôle |
|---|---|
| `/baseline` | Reconstruit l'environnement d'origine, génère et versionne le `composer.lock` de référence, prend les empreintes (schéma, inventaire, dépréciations, vulnérabilités) de step-00. |
| `/upgrade-step <sf-version>` | Une seule montée de Symfony : contraintes Composer, `composer update` ciblé, recettes Flex, sets Rector Symfony/Doctrine/Twig, adaptation de la configuration. |
| `/php-step <php-version>` | Une seule montée de PHP : image Docker, `config.platform.php`, extensions, `php.ini`, sets Rector PHP. |
| `/fix-deprecations [--target sf-x.y] [--php x.y]` | Corrige les dépréciations par **lots** cohérents (une famille par lot), à partir de l'inventaire de `/deprecation-check` ; test après chaque lot. |
| `/test-plan [--gaps]` | Construit la matrice de couverture à partir de l'inventaire : route × méthode × rôle × scénario de données × cas limites ; avec `--gaps`, part des lacunes (couverture, mutants survivants, inventaire non couvert). → `docs/migration/test-plan.md` |
| `/characterize <zone>` | Fiches de comportement `BH-xx` d'une zone (entrées, sorties, effets en base, bizarreries). → `docs/migration/behaviors/` |
| `/failure-triage` | Pour chaque test cassé : **régression** (à corriger) ou **changement accepté** (décision utilisateur, consignée dans `docs/migration/accepted-changes.md`), ou problème d'**environnement**. |
| `/step-report <step>` | Bilan d'une marche : versions, tests, inventaire (diff), dépréciations (diff), vulnérabilités (diff), changements acceptés. |

### 1.3 Niveau 3 — skills outils

#### Environnement et données

| Skill | Rôle |
|---|---|
| `/env [up\|down\|reset\|status] [--ref] [--step NN]` | Pilote l'environnement conteneurisé (§ 7). |
| `/seed [scenario\|all] [--verify]` | Charge un jeu de données déterministe (§ 6) et vérifie son empreinte. |

#### Inventaire (cartographie de l'application)

`/inventory [all\|<type>] [--snapshot\|--diff]` appelle les skills `/list-*` et écrit
`docs/migration/inventory/<type>.json`. Avec `--diff`, compare avec l'étape précédente : tout élément **disparu** ou
**modifié** (méthode HTTP, nom de route, prérequis, rôle) est une alerte.

| Skill | Source | Contenu relevé |
|---|---|---|
| `/list-routes` | `debug:router` + annotations/attributs + `access_control` | nom, chemin, méthodes, prérequis, contrôleur, rôle exigé, paramètres de requête lus (`?status=`) |
| `/list-commands` | `list` / `debug:command` | nom, options, valeurs par défaut, codes de retour |
| `/list-services` | `debug:container` | identifiants publics, classes, arguments, tags (repère les `$this->get('…')` à migrer) |
| `/list-forms` | Classes `*Type`, `createFormBuilder` | champs, types, options, contraintes, formats de date |
| `/list-entities` | Mapping Doctrine + schéma SQLite | tables, colonnes, types, index, relations, callbacks de cycle de vie |
| `/list-templates` | `app/Resources/views`, `src/*/Resources/views` | templates, héritage, variables, filtres et fonctions Twig utilisés |
| `/list-config` | `config*.yml`, `parameters`, `security.yml`, `services.yml` | paramètres, firewalls, encodeurs, valeurs métier (`cost_per_km`) |

#### Création et exécution des tests

| Skill | Rôle |
|---|---|
| `/write-http-tests <zone>` | Tests boîte noire HTTP (pages, formulaires, CSRF, auth, codes de statut, HTML normalisé, JSON octet par octet). |
| `/write-cli-tests <commande>` | Tests des commandes console (sortie, code de retour, effets en base). |
| `/write-db-tests` | Assertions sur l'état de la base après chaque action, et test de non-dérive du schéma. |
| `/write-unit-tests <classe>` | Tests unitaires du domaine (complément, cf. § 3.2). |
| `/write-edge-tests` | Un test par piège du § 0 (ex-æquo, comparaisons lâches, dates, arrondis). |
| `/write-diff-tests` | Tests différentiels : mêmes requêtes sur `app-ref` et `app`, réponses normalisées comparées. |
| `/golden-master [--record\|--verify]` | Enregistrement (step-00) ou vérification des sorties de référence. |
| `/test-run [--suite …] [--diff]` | Lance la suite dans le conteneur `tests`, progression dans la status line, rapport par `BH-xx`. |
| `/coverage` | Couverture (PCOV) de `src/` sur l'application exercée par la suite boîte noire. |
| `/mutation-check` | Infection : mutants survivants traduits en lacunes exprimées en `BH-xx`. |

#### Contrôles

| Skill | Rôle |
|---|---|
| `/deprecation-check` | Inventaire et suivi des dépréciations (détaillé au § 5). |
| `/compat-scan <php-version>` | Compatibilité avec le PHP visé : PHPCompatibility, PHPStan à la version cible, recherche des changements de sémantique (comparaisons, tri, `null` vers fonctions internes, `serialize_precision`). |
| `/vuln-audit [--snapshot\|--diff]` | Audit des vulnérabilités et mise à jour du JSON de suivi (§ 4). |
| `/step-commit` | Remplace le `/commit` Java du dépôt parent pour flotte : suite complète, contrôles de sécurité, décision de l'utilisateur ; message enrichi des diffs de sécurité et de dépréciations. |

---

## 2. Agents

| Agent | Lit | Écrit | Skills servies |
|---|---|---|---|
| `cartographer` | Code, configuration, conteneur qui tourne | `docs/migration/inventory/` | `/inventory`, `/list-*` |
| `characterizer` | Code legacy, inventaire, application qui tourne | `docs/migration/behaviors/`, `test-plan.md` | `/characterize`, `/test-plan` |
| `test-writer` | Fiches `BH-xx`, inventaire, jeux de données, application en boîte noire | `tests/` | `/write-*-tests`, `/golden-master --record` |
| `migrator` | Code, plan, rapports de dépréciations et de compatibilité | `src/`, `config/`, `templates/`, `composer.*`, `docker/app/` | `/upgrade-step`, `/php-step`, `/fix-deprecations` |
| `deprecation-analyst` | Tout, en lecture ; logs | `docs/migration/deprecations/` | `/deprecation-check`, `/compat-scan` |
| `security-auditor` | `composer.lock`, bases d'advisories, code (lecture) | `docs/security/` | `/vuln-audit` |
| `auditor` | Tout | Arbitrages, `accepted-changes.md`, rapports d'étape | `/failure-triage`, `/coverage`, `/mutation-check`, `/step-report` |
| `log-analyst` | Logs | rien | analyse d'incidents (repris tel quel) |

**Règles d'étanchéité** (hooks `PreToolUse`, comme dans le projet Java) :
- `migrator` **ne peut pas** modifier `tests/`, les golden masters, `tests/fixtures/`, ni les inventaires et rapports ;
- `test-writer` n'a **pas** accès au code de production (il voit l'application uniquement par HTTP et CLI) ;
- tests, golden masters et jeux de données sont **gelés** pendant une marche. Seul l'`auditor`, sur décision de
  l'utilisateur, peut les faire évoluer, et chaque évolution est tracée dans `docs/migration/accepted-changes.md`
  (comportement, raison, étape, date).

---

## 3. Suite de tests « bug pour bug »

### 3.1 Couche 1 — boîte noire, indépendante de la version (la vraie garantie)

Projet de tests **séparé** (son propre `composer.json`, son propre PHP 8.4, PHPUnit récent), qui pilote l'application
par HTTP et par la ligne de commande, dans son propre conteneur (§ 7). Il ne dépend d'aucune classe de l'application :
il ne change donc pas d'une marche à l'autre. Son périmètre est dérivé de l'inventaire (`/inventory`) : **chaque
route et chaque commande inventoriée doit avoir au moins un test**.

- **Routes** du README, cas nominaux et erreurs : 404 (id inconnu, id non numérique), 405, formulaires invalides,
  CSRF absent ou faux, Basic auth OK/KO/absente, `/admin` sans droit.
- **Réservation** : chevauchement, bornes jointives, fin ≤ début, véhicule en entretien ou hors service, format de
  date invalide.
- **Retour** : kilométrage inférieur au départ, double clôture, arrondi du coût, mise à jour du kilométrage du véhicule.
- **API** `/api/vehicules` : JSON comparé **octet par octet** (ordre des clés, types, `nextReservation` vide).
- **Liste filtrée** : `?status=` vide, `0`, `1`, `2`, `3`, `abc`, `01`, ` 1`.
- **Commandes** : `fleet:report` (format, ordre, ex-æquo, totaux, mois invalide, mois 13, mois sans données) et
  `fleet:demo:load`.
- **État de la base** vérifié après chaque action qui modifie des données.
- **HTML normalisé** avant comparaison : jetons CSRF, dates relatives et en-tête de version masqués.

### 3.2 Couche 2 — tests unitaires du domaine (complément)

`ReservationManager`, `PlateFormatter`, `BelgianPlateValidator`, entités, `FleetExtension`. Ils sont utiles pour
localiser une régression, mais leur API (PHPUnit, constructeurs, namespaces) évoluera avec PHP et Symfony. Ce ne
sont donc pas eux qui garantissent le « bug pour bug ».

### 3.3 Couche 3 — cas limites ciblés

Un test par piège listé au § 0 : ex-æquo dans le rapport, `status` non numérique, mois 13, dates ambiguës, `null`,
arrondis à ,005, `serialize_precision`.

### 3.4 Tests différentiels

Grâce au § 7, la version de référence (step-00) et la version en cours tournent **en même temps**. Un test
différentiel rejoue les mêmes requêtes sur les deux et compare les réponses normalisées. C'est le filet le plus
large : il couvre aussi ce qu'aucune fiche `BH-xx` n'a prévu.

### 3.5 Déterminisme et qualité

- Horloge figée (libfaketime dans le conteneur ; le code appelle `date()` et `new \DateTime()` directement),
  `TZ=Europe/Brussels`, locale et `php.ini` identiques entre étapes.
- Base remise dans un état de référence connu avant chaque test (§ 6).
- Couverture (PCOV) mesurée sur step-00, objectif proche de 100 % des lignes de `src/`.
- **Infection** (mutation testing) sur step-00 : vérifie que la suite détecte réellement les écarts.

**Critère de passage d'une marche** : 100 % vert (couches 1 + 3 + différentiel), aucune nouvelle dépréciation,
inventaire sans élément disparu, diffs de sécurité et de dépréciations consignés.

---

## 4. Suivi des vulnérabilités (sans test d'exploitation)

### 4.1 Sources

- `composer audit --locked --format=json` (base FriendsOfPHP / GitHub Advisory). Composer 2.4+ est nécessaire : il
  tourne dans un **conteneur d'outillage récent** sur le `composer.lock`, indépendamment du PHP de l'étape.
- `osv-scanner` sur `composer.lock` pour recouper (OSV.dev).
- **Runtime** : CVE de PHP lui-même (version de l'image de l'étape) et de l'image Docker (Trivy).
- Pour chaque vulnérabilité : version qui corrige, et **exposition** évaluée par lecture du code (composant
  utilisé ? chemin atteignable ?), sans test.

### 4.2 Fichier `docs/security/vulnerabilities.json`

Versionné, mis à jour à chaque étape par **fusion** (jamais d'écrasement : seulement des transitions de statut).

```json
{
  "schema_version": 1,
  "steps": [
    {
      "step": "step-00",
      "date": "2026-11-17",
      "php": "7.2.24",
      "symfony": "2.8.52",
      "composer_lock_sha256": "…",
      "counts": { "open": 0, "new": 0, "fixed": 0, "accepted": 0 }
    }
  ],
  "vulnerabilities": [
    {
      "id": "GHSA-xxxx-xxxx-xxxx",
      "aliases": ["CVE-YYYY-NNNNN"],
      "package": "symfony/http-kernel",
      "kind": "composer | php-runtime | os-image",
      "summary": "…",
      "severity": "critical | high | medium | low",
      "cvss": 7.5,
      "affected_range": ">=2.0.0,<3.4.36",
      "fixed_versions": ["3.4.36", "4.3.9"],
      "url": "https://…",
      "exposure": {
        "level": "reachable | used-not-reachable | not-used | unknown",
        "justification": "…"
      },
      "status": "open | fixed | introduced | accepted | not_applicable",
      "accepted": { "reason": "…", "by": "…", "date": "…", "until_step": "…" },
      "first_seen_step": "step-00",
      "fixed_in_step": null,
      "history": [
        { "step": "step-00", "installed_version": "2.8.52", "status": "open" }
      ]
    }
  ]
}
```

Règles :
- une entrée par couple **vulnérabilité × paquet** ;
- `introduced` = apparue à une étape (nouvelle dépendance ou nouvelle version vulnérable) ;
- `fixed` = absente de l'audit de l'étape alors qu'elle était présente avant (`fixed_in_step` renseigné) ;
- `accepted` exige une raison, un auteur, une date et une échéance.

### 4.3 À chaque marche

Audit, fusion dans le JSON, puis rapport lisible `docs/security/steps/step-NN.md` (résolues / ajoutées /
persistantes, triées par sévérité et exposition) et résumé dans le message de `/step-commit`. **Une vulnérabilité
`introduced` bloque la marche** jusqu'à décision de l'utilisateur.

---

## 5. Suivi des dépréciations — `/deprecation-check`

### 5.1 Usage

```
/deprecation-check [--snapshot|--diff] [--target sf-x.y] [--php x.y] [--source runtime|static|all]
```

- `--snapshot` : inventaire de l'étape courante (lecture seule, aucune correction).
- `--diff` : comparaison avec l'étape précédente → **nouvelles / résolues / persistantes**.
- `--target` / `--php` : ne retient comme **bloquantes** que les dépréciations supprimées dans la version visée
  (ce qui doit disparaître avant la prochaine marche).

### 5.2 Sources

| Source | Outil | Remarque |
|---|---|---|
| Runtime Symfony | Exécution de la suite boîte noire avec collecte des `E_USER_DEPRECATED` (canal de log `php`, `debug.log` ; `symfony/phpunit-bridge` et `SYMFONY_DEPRECATIONS_HELPER` côté unitaire) | Ne voit que le code **exécuté** : d'où l'importance de la couverture |
| Runtime PHP | `E_DEPRECATED` dans le log PHP du conteneur | `null` vers fonctions internes (8.1), propriétés dynamiques (8.2), paramètres implicitement nullables (8.4)… |
| Statique PHP | PHPStan + `phpstan-deprecation-rules` + `phpstan-symfony`, à la version PHP cible | Voit aussi le code non exécuté |
| Statique migrations | `rector --dry-run` (sets Symfony, Doctrine, Twig, PHP de la version visée) | Indique en plus la correction automatique disponible |
| Twig | `lint:twig --show-deprecations` (SF ≥ 4) ; collecteur de dépréciations Twig 1.x sur step-00 | `{% for … if %}`, filtres et tags supprimés |
| Doctrine | `doctrine/deprecations` (ORM ≥ 2.10) activé en mode trace | Annotations → attributs, DBAL 3 |
| Configuration | Lecture des YAML et des avertissements du conteneur au démarrage (`cache:clear` en mode verbeux) | Clés supprimées, services privés appelés via `get()` |
| Dépendances | `composer outdated --direct --format=json`, champ `abandoned` | `SensioFrameworkExtraBundle`, `security-acl` |

### 5.3 Fichier `docs/migration/deprecations.json`

Même logique que le suivi des vulnérabilités (fusion, statut, historique par étape) :

```json
{
  "id": "DEP-0007",
  "signature": "symfony/form: isValid() called on a form that is not submitted",
  "source": "runtime-symfony | runtime-php | phpstan | rector | twig | doctrine | config | dependency",
  "location": ["src/AppBundle/Controller/AdminController.php:29"],
  "removed_in": "symfony 4.0",
  "blocking_for": "step-02",
  "autofix": "rector:… | none",
  "status": "open | fixed | introduced | accepted",
  "first_seen_step": "step-00",
  "fixed_in_step": null
}
```

La **signature** est normalisée (sans numéros de ligne ni valeurs variables) pour regrouper les occurrences et
suivre une même dépréciation d'une étape à l'autre.

### 5.4 Rapport et règles

- Rapport `docs/migration/deprecations/step-NN.md` : bloquantes pour la prochaine marche, regroupement par famille,
  correction automatique possible (Rector) ou manuelle, plan de lots pour `/fix-deprecations`.
- **Lecture seule** : `/deprecation-check` ne corrige rien ; c'est `/fix-deprecations` (agent `migrator`) qui agit.
- **Gate** : aucune dépréciation bloquante pour la version visée, et aucune dépréciation `introduced` non arbitrée.

---

## 6. Jeu de données de référence (seeding)

### 6.1 Principes

- **Neutre vis-à-vis de la version** : le seed doit produire **exactement la même base** en step-00 et en step-07.
  Les scénarios sont décrits dans un format déclaratif (YAML/JSON) sous `tests/fixtures/`, et chargés par un
  chargeur du **projet de tests** (PDO/SQL direct sur le schéma), pas par le code de l'application qui change à
  chaque marche.
- **Déterministe** : identifiants explicites, dates absolues, aucune valeur aléatoire (ou générateur à graine fixe).
  L'horloge figée (§ 3.5) rend stables les notions « à venir » / « passé ».
- **Vérifiable** : empreinte (hash) de la base après chargement, contrôlée par `/seed --verify`.
- **Rapide à restaurer** : chaque scénario est aussi figé sous forme de fichier SQLite de référence, copié avant
  chaque test (remise à zéro en quelques millisecondes).
- **Schéma figé** : le schéma est capturé en step-00 (`.schema` SQLite). Toute différence de schéma produite par la
  migration (Doctrine DBAL 3, types `datetime`…) est détectée par un test dédié et arbitrée.
- `fleet:demo:load` reste **tel quel** : c'est un comportement de l'application, couvert lui-même par des tests. Il ne
  sert pas de seed de test.

### 6.2 Scénarios

| Scénario | Contenu | Sert à |
|---|---|---|
| `empty` | Schéma sans données | listes vides, rapport vide, première création |
| `demo` | Identique à `fleet:demo:load` (5 véhicules, 5 réservations) | non-régression de la commande, parcours nominaux |
| `nominal` | ~20 véhicules dans tous les statuts, réservations passées, en cours, à venir, clôturées | parcours fonctionnels, API, liste filtrée |
| `edge` | Véhicules à km **égaux** dans le rapport (ex-æquo), réservations jointives et chevauchantes, chevauchement à cheval sur deux mois, coûts à arrondi limite (,005), kilométrage 0 et très grand, noms accentués et longs, plaques limites, véhicule sans réservation, réservation sans statut | pièges du § 0, tris, arrondis, comparaisons |
| `report-months` | Réservations clôturées réparties sur plusieurs mois, dont un mois sans données et des bornes à minuit | `fleet:report` |
| `volume` (optionnel) | Quelques milliers de lignes générées à graine fixe | performances, temps de réponse comparés |

Chaque scénario est documenté (`tests/fixtures/README.md`) avec les fiches `BH-xx` qu'il alimente.

---

## 7. Environnement conteneurisé

### 7.1 Objectifs

Même environnement pour tout le monde (formateur, stagiaires, agents IA, CI), reproductible à l'octet près, et
capable de faire tourner **deux versions en parallèle** (référence et étape en cours) pour les tests différentiels.

### 7.2 Services (`docker-compose.yml`)

| Service | Image | Rôle |
|---|---|---|
| `app-ref` | PHP 7.2 + Apache (ou Nginx + FPM), code step-00 | Version de référence, **figée**, toujours disponible (port 8072) |
| `app` | PHP de l'étape courante (argument de build `PHP_VERSION`), code de la branche | Version en cours de migration (port 8080) |
| `db` | Volume SQLite par service, plus un conteneur `sqlite3` d'outillage | Base de chaque application, isolée ; restauration des scénarios du § 6 |
| `tests` | PHP 8.4 + PHPUnit + client HTTP | Projet de tests boîte noire et différentiels ; parle à `app-ref` et `app` par le réseau Docker |
| `tools` | PHP 8.4 + Composer récent, Rector, PHPStan, PHPCompatibility, Infection, `osv-scanner` | Analyses statiques, dépréciations, audit (`composer audit --locked`) |
| `trivy` | Trivy | Scan des images `app-ref` et `app` |

### 7.3 Règles de reproductibilité

- Images **épinglées par digest** (`php:7.2-apache@sha256:…`), extensions PHP explicites (`pdo_sqlite`, `intl`…).
- `php.ini` versionné et identique entre étapes, sauf ce qui n'existe plus : `date.timezone=Europe/Brussels`,
  `precision`, `serialize_precision`, `memory_limit`, `error_reporting=E_ALL`, `display_errors=0`, **log des
  dépréciations activé** (alimente `/deprecation-check`).
- `TZ`, `LANG`/`LC_ALL` fixés ; **libfaketime** installé avec `FAKETIME` piloté par variable d'environnement.
- `composer install --no-scripts` depuis le **`composer.lock`** uniquement, jamais d'`update` implicite.
- Base montée sur un volume dédié par service ; `/env reset` restaure le scénario demandé.
- Aucun secret dans l'image (le mot de passe `gestionnaire/flotte2016` et le `secret` Symfony sont des valeurs de
  test fournies par variables d'environnement, à traiter comme des vulnérabilités de configuration dans le JSON du § 4).
- Healthchecks sur `app` et `app-ref` ; `tests` attend qu'ils soient sains avant de démarrer.
- Un `Dockerfile` par famille de PHP (`docker/app/php72`, `php74`, `php81`, `php84`) pour garder chaque étape
  reconstructible plus tard.

### 7.4 SQLite et le périmètre « db »

La migration garde **SQLite** pour ne pas mélanger deux changements (version et moteur de base). Si un passage à
PostgreSQL ou MySQL est souhaité, il fera l'objet d'une **étape distincte après step-07**, avec son propre service
`db`, ses propres scénarios de seed et une nouvelle passe des tests boîte noire (qui, eux, ne changeront pas).

---

## 8. Arborescence produite

```
flotte/
├── .claude/
│   ├── skills/              orchestrateurs, étapes, outils (§ 1)
│   ├── agents/              § 2
│   ├── hooks/guard.sh       étanchéité par agent
│   └── state/migration/     état reprenable des orchestrateurs (non versionné)
├── docker/                  app/php72…php84, tests, tools, php.ini (§ 7)
├── tests/                   projet de tests boîte noire (composer propre), fixtures/ (§ 6), golden/
└── docs/
    ├── migration/
    │   ├── inventory/       routes.json, commands.json, services.json, forms.json, entities.json, templates.json, config.json
    │   ├── behaviors/       fiches BH-xx
    │   ├── test-plan.md
    │   ├── deprecations.json + deprecations/step-NN.md
    │   ├── accepted-changes.md
    │   └── steps/step-NN.md
    └── security/
        ├── vulnerabilities.json
        └── steps/step-NN.md
```

---

## 9. Mise en œuvre (outillage créé)

L'outillage décrit ici existe sous `flotte/.claude/` (Claude Code doit être lancé depuis `flotte/`) :

- `CLAUDE.md` (prime sur celui du dépôt parent), `.claude/reference/conventions.md` (fait foi pour les chemins,
  scripts, formats et gates), `.claude/reference/steps.json` ;
- 36 skills (§ 1), 8 agents (§ 2) isolés par `.claude/hooks/guard.sh <mode>` ;
- hook de session `.claude/hooks/freeze-guard.sh` (gel de `tests/` pendant une marche) ;
- scripts partagés `.claude/bin/` : `env.sh`, `console.sh`, `composer.sh`, `tools.sh`, `http.sh`, `seed.sh`,
  `tests.sh`, `junit-summary.py`, `json-diff.py`, `track-merge.py` (fusion et décisions d'acceptation), `state.py`.

Reste à produire par les skills elles-mêmes : l'infrastructure Docker (`/env init`), la baseline (`/baseline`),
le projet de tests (`/write-http-tests` crée le squelette).

## 10. Ordre d'exécution conseillé

1. **Outillage** : agents, hooks d'étanchéité, skills outils (`/env`, `/seed`, `/inventory`, `/list-*`,
   `/deprecation-check`, `/vuln-audit`, `/test-run`), puis skills d'étape, puis orchestrateurs.
2. **Environnement** (§ 7) : images step-00, `app-ref`, `tests`, `tools`.
3. **`/baseline`** : `composer.lock` de référence, empreintes (schéma, inventaire, dépréciations, vulnérabilités).
   *Risque : le lock est résolu aujourd'hui et non en 2016 ; les versions transitives peuvent différer de la
   production réelle. À comparer avec le `vendor/` de production s'il est récupérable.*
4. **`/build-test-suite`** jusqu'aux seuils (couverture, score Infection) ; enregistrement des golden masters.
5. **`/upgrade-symfony 6.4`** en mode `--interactive` pour les premières marches (il appelle `/upgrade-php` quand
   nécessaire), puis `--auto` une fois la chaîne rodée.
6. **`/upgrade-php 8.4`** pour la dernière marche (step-07).
7. **Clôture** : bilan des changements de comportement acceptés, bilans sécurité et dépréciations step-00 → step-07,
   préparation de la suite (Symfony 7.4 LTS).

## 11. Points à trancher

- Garder SQLite jusqu'au bout (recommandé) ou prévoir une étape de changement de moteur de base ?
- Doctrine ORM 2.x (migration plus douce) ou 3.x en fin de parcours ?
- Les bugs historiques (tri instable du rapport, filtre `?status=abc`…) doivent-ils être **conservés** en 6.4, ou
  corrigés après la migration, dans des étapes séparées et tracées ?
- Récupérer le `vendor/` ou le lock de production réel pour fiabiliser la baseline ?
- Seuils minimaux exigés : couverture, score Infection, sévérité maximale des vulnérabilités `accepted`, nombre
  maximal d'itérations de correction avant arrêt d'un orchestrateur.
- Niveau d'autonomie par défaut des orchestrateurs : `--interactive` partout, ou `--auto` après les deux premières
  marches ?
