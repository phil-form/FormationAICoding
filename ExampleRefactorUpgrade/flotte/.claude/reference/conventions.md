# Conventions de l'outillage de migration (flotte)

Référence partagée par **toutes** les skills et tous les agents de `flotte/.claude/`. Plan d'ensemble :
`tmp/plan-migration-php84-sf64.md`. En cas d'écart entre une skill et ce fichier, **ce fichier fait foi**.

Claude Code doit être lancé **depuis `flotte/`** (`CLAUDE_PROJECT_DIR` = `flotte/`).

## 1. Étapes

Définies dans `.claude/reference/steps.json` (source de vérité, lue par les orchestrateurs) :

| Step | PHP | Symfony | Nature |
|---|---|---|---|
| step-00 | 7.2 | 2.8 | baseline (référence figée) |
| step-01 | 7.2 | 3.4 | symfony |
| step-02 | 7.2 | 4.4 | symfony (structure Flex, `AppBundle` → `App`) |
| step-03 | 7.4 | 4.4 | php |
| step-04 | 7.4 | 5.4 | symfony |
| step-05 | 8.1 | 5.4 | php |
| step-06 | 8.1 | 6.4 | symfony |
| step-07 | 8.4 | 6.4 | php (cible) |

Étape courante : `docker/step.env` (`STEP`, `PHP_VERSION`, `SYMFONY_VERSION`, `FAKETIME`).
Tag git d'une étape validée : `flotte-migration/step-NN` (le dépôt git est le dépôt parent `Formation/`).

## 2. Arborescence

```
flotte/
├── docker-compose.yml            projet compose "flotte-mig"
├── docker/
│   ├── step.env                  étape courante (versionné)
│   ├── app/Dockerfile.tpl, app/render.sh → app/php72|php74|php81|php84/Dockerfile (générés)
│   ├── app/php.ini               commun à toutes les étapes ; app/prepend/ (auto_prepend, /__health) ; app/bin/
│   ├── IMAGES.md                 digests des images de base
├── rector/                       configurations Rector par marche (common.php, step-01…07.php, targets.json, README.md)
│   ├── tests/Dockerfile          PHP 8.4 + PHPUnit 11
│   └── tools/Dockerfile          PHP 8.4 + Composer récent, Rector, PHPStan, PHPCompatibility, Infection, osv-scanner
├── .ref/                         copie du code step-00 (git archive du tag), servie par app-ref — non versionné
├── tests/                        projet de tests boîte noire (composer.json propre)
│   ├── composer.json, phpunit.xml.dist
│   ├── src/Http/  src/Cli/  src/Db/  src/Edge/  src/Diff/   suites exécutées dans le conteneur "tests"
│   ├── src/Support/              client HTTP, normaliseurs HTML/JSON, accès SQLite, horloge
│   ├── unit/                     tests unitaires du domaine, exécutés dans le conteneur "app" ou "app-ref" (PHPUnit de l'image)
│   ├── src/Diff/Journeys/*.yaml  parcours rejoués sur app et app-ref ; src/Diff/accepted.yaml (écarts acceptés)
│   ├── unit/                     + unit/phpunit.xml.dist, unit/bootstrap-map.php (AppBundle\… → App\…)
│   ├── fixtures/<scenario>.yaml  + fixtures/README.md + fixtures/db/<scenario>.sqlite (+ .sha256)
│   ├── fixtures/schema.sql       schéma SQLite de référence (capturé par /baseline)
│   ├── golden/<suite>/…          sorties de référence (golden masters) + golden/MANIFEST.md
│   └── bin/seed.php              chargeur de scénarios (PDO direct)
├── docs/
│   ├── migration/
│   │   ├── inventory/step-NN/<type>.json     routes, commands, services, forms, entities, templates, config
│   │   │                                     (+ summary.md, diff.md)
│   │   ├── behaviors/<zone>.md               fiches BH-xxx
│   │   ├── test-plan.md
│   │   ├── deprecations.json  deprecations/step-NN.md
│   │   ├── compat/php-X.Y.md
│   │   ├── accepted-changes.md
│   │   ├── audit/                            coverage-step-NN.md, mutation-step-NN.md
│   │   └── steps/step-NN.md, steps/final.md  rapports d'étape et bilan final
│   └── security/
│       ├── vulnerabilities.json
│       └── steps/step-NN.md
└── .claude/
    ├── reference/                conventions.md (ce fichier), steps.json, schémas
    ├── bin/                      scripts partagés (§ 4)
    ├── hooks/                    guard.sh (par agent), freeze-guard.sh (session)
    ├── agents/  skills/
    └── state/migration/          état local, non versionné (§ 6) :
                                  state.json, feedback-code.md, feedback-tests.md, triage/step-NN.md,
                                  test-results/ (junit-*.xml, summary.json), coverage/ (enabled, raw/, clover.xml,
                                  summary.json), mutation/ (summary.json), deprecations/<step>-findings.json,
                                  deprecations/<step>-compat-<php>.json, security/<step>-findings.json, http/ (cookies)
```

## 3. Services Docker (`docker-compose.yml`)

Environnement de **développement local uniquement** (jamais en production). Toujours piloté par
`.claude/bin/env.sh`, qui charge `docker/step.env` et exporte `HOST_UID`, `HOST_GID` (fichiers créés au nom de
l'utilisateur hôte) et `DOCKER_GID` (accès au socket pour les tests CLI). `tests`, `tools` et `trivy` sont dans le
profil compose `tools` : ils ne démarrent qu'à la demande (`run --rm`).

| Service | Port hôte → conteneur | Code monté (`/srv/app`) | Base SQLite | Rôle |
|---|---|---|---|---|
| `app` | `127.0.0.1:8080` → 8080 | `./` (rw) | volume `db-app` → `/var/app-data/flotte.sqlite` | étape en cours (image `flotte-mig/app:${PHP_FAMILY}`) |
| `app-ref` | `127.0.0.1:8072` → 8080 | `./.ref` (ro, jamais créé par Docker) + `./tests` (ro) sur `/srv/app/tests` ; `app/cache`, `app/logs` en tmpfs | volume `db-ref` → `/var/app-data/flotte.sqlite` | référence step-00 figée (image `flotte-mig/app:php72`) |
| `tests` | — | `./tests` → `/srv/tests` (rw) | `db-app` → `/data/app`, `db-ref` → `/data/ref` | suites boîte noire, PHP 8.4 |
| `tools` | — | `./` (rw) | journaux PHP en lecture (`/logs/app`, `/logs/ref`) | analyse statique, Composer récent, audit |
| `trivy` | — | — | — | scan des images (`aquasec/trivy`) |

Conteneur `tests` : `APP_URL=http://app:8080`, `REF_URL=http://app-ref:8080`, `TARGET=app|ref`,
`GOLDEN_RECORD=0|1`, `APP_DB=/data/app/flotte.sqlite`, `REF_DB=/data/ref/flotte.sqlite`, socket Docker + CLI Docker
pour les tests CLI : `CONSOLE_APP_CMD="docker exec -i flotte-mig-app sf-console"`,
`CONSOLE_REF_CMD="docker exec -i flotte-mig-app-ref sf-console"`.

Noms de conteneurs fixes : `flotte-mig-app`, `flotte-mig-app-ref`. `app`, `app-ref` et `tests` montent
`./.claude/state/migration/test-results` sur `/results` (JUnit). Tous les conteneurs tournent sous l'uid de
l'utilisateur hôte (Apache non root, port interne 8080).

Images `app` — une par famille de PHP, générées par `docker/app/render.sh` depuis `docker/app/Dockerfile.tpl`
(`docker/app/<famille>/Dockerfile`, ne pas éditer à la main) : base `php:<x.y>-apache` (repli automatique sur
archive.debian.org pour les Debian archivées), `pdo_sqlite`, `intl`, PCOV, libfaketime, Composer (2.2 LTS pour
PHP 7.2, 2.x ensuite), PHPUnit autonome (8.5 pour 7.2, 9.6 pour 7.4 et 8.1, 11 pour 8.4) via `/usr/local/bin/phpunit`,
`/usr/local/bin/sf-console` (`bin/console` si présent, sinon `app/console`), `/__health` (JSON : PHP, date vue,
extensions — indépendant de l'application). `auto_prepend_file` = `docker/app/prepend/prepend.php`, qui charge
`deprecation-logger.php` (journalise `E_DEPRECATED` / `E_USER_DEPRECATED` dans `/var/log/php/deprecations.log`,
une ligne JSON par occurrence, sans effet sur l'application) et `coverage-prepend.php` (n'agit que si
`.claude/state/migration/coverage/enabled` existe : couverture PCOV de la requête dans
`.claude/state/migration/coverage/raw/`).

Base : `DATABASE_PATH=/var/app-data/flotte.sqlite` (et `DATABASE_URL` équivalent pour les étapes Flex). En Symfony
2.x/3.x, `app/config/parameters.yml` (non versionné) est généré depuis le `.dist` avec ce chemin
(`flotte-entrypoint` pour `app`, `env.sh ref-sync` pour `app-ref`).

Horloge : `FAKETIME=@2026-01-15 10:00:00` (`docker/step.env`) : chaque processus démarre à cette date puis l'horloge
avance normalement — une horloge totalement figée bloque PHP (`uniqid()` boucle en attendant que l'heure change).
Elle est appliquée par `LD_PRELOAD` à **Apache**, à
`sf-console` et à `phpunit` uniquement — pas à Composer ni aux téléchargements (certificats TLS). `TZ=Europe/Brussels`,
`LC_ALL=C.UTF-8`, `php.ini` commun `docker/app/php.ini` (`serialize_precision=-1`, `precision=14`,
`error_reporting=E_ALL`, `display_errors=Off`, `log_errors=On`).
Images de base épinglées par digest (`docker/IMAGES.md`). Dépendances installées depuis `composer.lock` uniquement.

## 4. Scripts partagés (`.claude/bin/`)

Seuls points d'entrée autorisés pour les agents (cf. `hooks/guard.sh`). Tous renvoient un code ≠ 0 en cas d'échec et
écrivent leurs sorties volumineuses dans `.claude/state/migration/`.

| Script | Usage |
|---|---|
| `env.sh` | `env.sh up\|down\|build\|status\|reset [--ref] [--scenario <s>]` · `env.sh php-version [--ref]` · `env.sh ref-sync <tag>` (régénère `.ref/`, réservé à `/baseline`). La création de l'infrastructure est faite par la skill `/env init` (session principale), pas par ce script |
| `console.sh` | `console.sh [--ref] <args…>` — console Symfony dans `app` ou `app-ref` (détecte `bin/console` ou `app/console`) |
| `composer.sh` | `composer.sh [--tools] <args…>` — Composer de l'image `app` (ou récent dans `tools` avec `--tools`) |
| `tools.sh` | `tools.sh <rector\|phpstan\|phpcs\|infection\|osv-scanner\|composer> <args…>` — dans le conteneur `tools` |
| `http.sh` | `http.sh [--ref] [--session <nom>] <METHOD> <path> [-d clé=valeur]… [--csrf-from <path>]` — requête vers app/app-ref, affiche statut, en-têtes et corps |
| `seed.sh` | `seed.sh <scenario\|all> [--target app\|ref\|both] [--verify] [--rebuild]` |
| `tests.sh` | `tests.sh [--suite http\|cli\|db\|edge\|diff\|unit\|all] [--target app\|ref] [--filter <motif>] [--group BH-xxx] [--coverage] [--record-golden]` → JUnit `.claude/state/migration/test-results/junit-<suite>.xml` + `summary.json`. `--record-golden` refusé si gelé ; `--coverage` refusé sur `ref` |
| `php-logs.sh` | `php-logs.sh [--ref] [error\|deprecations] [--from <octet>] [--summary]` — lecture seule des journaux PHP d'`app` / `app-ref` (`--summary` : regroupement par signature) |
| `rector.sh` | `rector.sh <step-NN> [--apply] [--only <Règle>]… [--filter <motif>] [--list]` — Rector de la marche (`rector/step-NN.php`, versions visées `rector/targets.json`), simulation par défaut (diff dans `.claude/state/migration/rector/`) ; `--apply` réservé au migrator. Voir `rector/README.md` |
| `test-progress.py` | appelé par `tests.sh` : sortie `--teamcity` de PHPUnit (8.5 → 11) → `.claude/state/migration/test-results/progress.json` (status line, ligne 🧪) + barre dans le terminal |
| `test-tracking.py` | appelé en fin de `tests.sh` : met à jour le bloc « Suivi d'avancement » du document de fiches `tmp/tests-application.md` (par groupe et par route ; fiche décrite, tests `bhNNN_*`, goldens, résultat `app-ref` / `app`, variante `diff`, statut ⬜ ✍️ 🔴 🟡 ✅ ⏸). Résultats JUnit séparés par cible : `junit-<suite>.xml` (app), `junit-<suite>.ref.xml` (app-ref) |
| `junit-summary.py` | `junit-summary.py <junit.xml…> [--json]` — totaux, échecs regroupés par `BH-xxx` |
| `json-diff.py` | `json-diff.py <ancien.json> <nouveau.json> --key <champ> [--fields a,b]` — ajouts / suppressions / modifications |
| `track-merge.py` | `track-merge.py <vulnerabilities\|deprecations> --step step-NN --input <constats.json>` — fusion avec transitions de statut (§ 5) |
| `state.py` | `state.py init --orchestrator <o> --target <v> [--mode m] [--max-fix N]` · `get [clé.pointée]` · `set <clé.pointée> <valeur>` · `push-event <texte>` · `alert <G-CODE> <texte>` · `alerts-clear` · `freeze on\|off`. Les agents n'ont droit qu'à `get`, `push-event`, `alert`. `freeze off` n'est accepté par le hook que si la suite est verte et qu'aucune alerte n'est ouverte ; sinon l'utilisateur le lance lui-même (`! .claude/bin/state.py freeze off`) |

## 5. Formats

### 5.1 Fiches de comportement `BH-xxx` (`docs/migration/behaviors/<zone>.md`)

Numérotation globale sur trois chiffres. Chaque fiche :

```markdown
### BH-012 — Filtre de liste avec statut non numérique
- Zone : vehicles-list · Interface : HTTP `GET /vehicules?status=abc`
- Scénario de données : nominal
- Entrée : …
- Sortie observée (step-00) : statut, extrait normalisé, effets en base
- Particularité : bug historique / comportement dépendant de la version PHP (oui/non + explication)
- Couche de test : http | cli | db | edge | diff | unit
- Contrat (unit uniquement) : classe, signature publique, dépendances à fournir
```

### 5.2 Tests

- Une classe par zone et par suite ; une méthode par `BH` : `bh012_status_filter_with_non_numeric_value`.
- Attribut PHPUnit `#[Group('BH-012')]` sur chaque méthode, `#[Group('zone-vehicles-list')]` sur la classe.
- Les tests **ne référencent aucune classe de l'application** (sauf `tests/unit/`).
- Chaque test part d'un scénario restauré (`fixtures/db/<scenario>.sqlite`), jamais de l'état laissé par un autre.

### 5.3 Inventaire (`docs/migration/inventory/step-NN/<type>.json`)

Tableau d'objets triés par clé, avec une clé stable :
`routes` → `name` ; `commands` → `name` ; `services` → `id` ; `forms` → `block_prefix` (nom du formulaire : `reservation`, `vehicle`, `form`) ; `entities` → `table` ;
`templates` → `path` (chemin logique, ex. `Vehicle/show`) ; `config` → `key` (chemin pointé).
Le chemin physique change entre étapes (`app/Resources/views` → `templates/`) et le namespace aussi (`AppBundle\…` →
`App\…` en step-02) : la clé doit rester logique ; le nom de classe est un champ comparé, jamais la clé.

### 5.4 Suivi `vulnerabilities.json` / `deprecations.json`

Structure décrite dans le plan (§ 4.2 et § 5.3). Fusion **uniquement** par `track-merge.py` :

| Avant \ Constaté à l'étape | présent | absent |
|---|---|---|
| (inconnu) | `introduced` (ou `open` si step-00) | — |
| `open` / `introduced` | `open` | `fixed` (+ `fixed_in_step`) |
| `fixed` | `introduced` (réapparition, alerte) | `fixed` |
| `accepted` | `accepted` (alerte si `until_step` dépassé) | `fixed` |
| `not_applicable` | `not_applicable` | `fixed` |

Clés : vulnérabilités `id` + `package` ; dépréciations `signature` normalisée.
Vulnérabilités, champ `kind` : `composer | php-runtime | os-image | config` (`config` : secret Symfony par défaut,
encodeur `plaintext`, compte de test en clair — identifiant interne `CFG-xxx`, `package` = fichier concerné).

### 5.5 Retours entre agents

- `.claude/state/migration/feedback-tests.md` : pour `test-writer`, en termes de `BH-xxx` uniquement.
- `.claude/state/migration/feedback-code.md` : pour `migrator`, en termes de `BH-xxx` et de dépréciations uniquement.
- Jamais de code de test recopié vers `migrator`, ni de code de production vers `test-writer`.

### 5.6 Document de fiches de tests (`tmp/tests-application.md`) — mise à jour après chaque implémentation

Un **document unique** pour toute l'application : `tmp/tests-application.md` — § 1 gabarit commun, § 2 à § 6 une
section par groupe (`VehicleController`, `ReservationController`, `ApiController`, `AdminController`, commandes
console : sous-sections « ### N.m `route` » de fiches, « Ce qui change », exemple complet, points à trancher), puis les
sections uniques « Journal d'implémentation » et « Blocages ». Après **chaque** implémentation de tests (skills
`write-bh-tests`, `golden-master`), il est mis à jour **dans toutes ses parties**, dans cet ordre :

| Partie | Mise à jour | Par qui |
|---|---|---|
| **Bloc « Suivi d'avancement »** (`<!-- tracking:start -->` … `<!-- tracking:end -->`) | régénéré, jamais édité à la main | `.claude/bin/test-tracking.py` (automatique en fin de `tests.sh`, à relancer après l'écriture) |
| **Lignes de fiches** (tableaux `\| BH-NNN \| …`) | si la référence a montré une valeur différente de la fiche, ou si une valeur était « à confirmer » / « déduite » : corriger la cellule **avec la valeur observée sur `app-ref`** et ajouter `(observé à l'implémentation)` ; **ne jamais** retirer une fiche ni changer son numéro ; ne jamais ajuster une fiche pour faire passer un test sans l'avoir observé sur la référence | `test-writer` |
| **Section « Journal d'implémentation »** (unique, avant « Blocages ») | une ligne par fiche implémentée : date, `BH-NNN`, `Classe::méthode` (une ou plusieurs), suite(s), golden(s), résultat sur `app-ref`, écart corrigé dans la fiche (oui/non) | `test-writer` |
| **Section « Blocages »** | `- BH-NNN : ⏸ <raison>` pour toute fiche non implémentable (dépend d'un point à trancher, comportement non reproductible, environnement) ; retirer la ligne quand le blocage est levé | `test-writer` |
| **Sous-section « Points à trancher » du groupe** | **ne pas trancher** : ajouter seulement `→ bloque BH-NNN` en fin de ligne si un point empêche une fiche ; la décision revient à l'utilisateur | `test-writer` |
| **Exemple complet** du groupe | aligner les valeurs sur celles du test si elles ont changé | `test-writer` |

Le document modifié est commité **avec** les tests qu'il décrit (commit automatique `/step-commit`, mode hors marche).

## 6. État des orchestrateurs (`.claude/state/migration/state.json`)

```json
{
  "orchestrator": "upgrade-symfony",
  "target": "6.4",
  "mode": "interactive",
  "current_step": "step-01",
  "phase": "fix-deprecations",
  "fix_iterations": 1,
  "max_fix_iterations": 3,
  "frozen": true,
  "last_green": { "step": "step-00", "commit": "…" },
  "alerts": [ { "at": "…", "gate": "G-VULN", "text": "…", "step": "step-01" } ],
  "events": [ { "at": "…", "text": "…" } ]
}
```

Clés libres ajoutées par les skills (via `state.py set`, clés pointées) : `scenario` (dernier scénario chargé),
`last_test_run`, `baseline.lock_sha256`, `suite.status`, `sub_orchestrator` (appel imbriqué
`upgrade-symfony` → `upgrade-php`).

`frozen: true` (posé par `state.py freeze on`) active `hooks/freeze-guard.sh` : **aucune** écriture dans `tests/`
(fixtures et golden compris) n'est possible dans la session, quel que soit l'agent. Seul l'utilisateur peut lever le
gel (`state.py freeze off`), après décision consignée dans `accepted-changes.md`.

## 7. Gates (arrêt obligatoire, même en mode `--auto`)

| Code | Condition |
|---|---|
| `G-TESTS` | suite rouge après `max_fix_iterations` itérations de correction |
| `G-BEHAVIOR` | `/failure-triage` propose un changement de comportement accepté |
| `G-VULN` | vulnérabilité `introduced` (ou `accepted` échue) |
| `G-DEPREC` | dépréciation bloquante pour la version visée, ou `introduced` non arbitrée |
| `G-INVENTORY` | route, commande, formulaire, entité ou template disparu ou modifié |
| `G-FROZEN` | tentative de modification d'une zone gelée |
| `G-ENV` | environnement indisponible (conteneur non sain, image non construite) |

Une gate levée : l'orchestrateur écrit l'alerte dans `state.json`, produit un résumé et **demande** à l'utilisateur
(`AskUserQuestion`) : corriger, accepter (avec raison), reporter, arrêter.

## 8. Agents et skills

| Agent | Mode guard | Skills |
|---|---|---|
| `cartographer` | `carto` | `/inventory` (types : `.claude/skills/inventory/types/`) |
| `characterizer` | `characterize` | `/characterize`, `/test-plan` |
| `test-writer` | `tests` | `/write-bh-tests` (couches : `.claude/skills/write-bh-tests/layers/`), `/golden-master` |
| `migrator` | `migrate` | `/upgrade-step` (marches : `.claude/skills/upgrade-step/kinds/`), `/fix-deprecations` |
| `deprecation-analyst` | `deprec` | `/deprecation-check`, `/compat-scan` |
| `security-auditor` | `security` | `/vuln-audit` |
| `auditor` | `auditor` | `/failure-triage`, `/coverage`, `/mutation-check`, `/step-report` |
| `log-analyst` | `logs` | (analyse d'incident) |

Les orchestrateurs (`/migrate`, `/upgrade-symfony`, `/upgrade-php`, `/build-test-suite`) et les skills
d'environnement (`/env`, `/seed`, `/baseline`, `/test-run`, `/step-commit`) s'exécutent dans la **session
principale** : un sous-agent ne peut pas lancer d'autres sous-agents.

## 9. Gabarit de skill

```yaml
---
name: <nom>
description: <ce que fait la skill, une phrase>
when_to_use: <quand l'utiliser>
argument-hint: "<arguments>"
context: fork            # seulement si la skill délègue à un agent
agent: <agent>           # idem
allowed-tools: Bash(.claude/bin/<script>:*)   # pour les skills de session principale
---
```

Corps en français : arguments, règles, étapes numérotées, sorties produites, rapport final. Chaque skill rappelle
les chemins qu'elle lit et écrit, et renvoie à ce fichier plutôt que de répéter les conventions.
