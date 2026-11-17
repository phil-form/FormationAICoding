# Rapport d'étape — step-01 : Symfony 2.8.52 → 3.4.49 (PHP 7.2)

> Auditeur, 2026-11-17. Constat uniquement : ce rapport ne modifie ni le code, ni les tests, ni les suivis.
> Diffs calculés par `.claude/bin/json-diff.py` (inventaire) et `.claude/bin/junit-summary.py` (tests).

## 1. En-tête

| Élément | Avant (step-00) | Après (step-01) |
|---|---|---|
| PHP | 7.2.34 | 7.2.34 (inchangé) |
| Symfony (`symfony/symfony`) | 2.8.52 | **3.4.49** |
| Twig | 1.42.5 | 1.42.5 (inchangé) |
| `composer.lock` sha256 | `d3c96ce503d57454bddd90e2c3e5c9a502fd03ad96ddb2e1b86282ffdd57093e` | `fc3e1a047d4f2be2559e9d2e25d9a40604e3619955c496ed38790e91c6ff7029` (valeur enregistrée par `track-merge.py` dans `deprecations.json` et `vulnerabilities.json`) |
| `composer.lock` content-hash | `64fa0d0371d12a599f933fa992e03fcc` | `bac014b3470511f9320da529effdc49e` |

- Commit de départ (`last_green`) : `7af2a10` (baseline step-00) ; tag de référence `flotte-migration/step-00` sur `74ad4ac`.
  `HEAD` = `5ac272e` (ne touche que `tmp/prompts.md`).
- **Code de la marche non commité** (`git status`) : à valider puis commiter via `/step-commit` (mode marche).
- Orchestrateur : `/upgrade-symfony 6.4 --interactive` (plan : step-01 → step-06 avec `upgrade-php` 7.4 et 8.1),
  phase `report`, 2 itérations de correction sur 3 autorisées, tests gelés (`frozen: true`).

## 2. Dépendances

Contrainte directe modifiée (`composer.json`) : `symfony/symfony` `2.8.52` → **`3.4.*`** (les autres contraintes
directes restent épinglées : `doctrine/orm` 2.5.14, `sensio/framework-extra-bundle` 3.0.29, `symfony/security-acl`
3.0.4, `twig/twig` 1.42.5…).

| Paquet | Mouvement | Version |
|---|---|---|
| `symfony/symfony` | mis à jour (direct) | v2.8.52 → v3.4.49 |
| `fig/link-util` | ajouté (transitif) | 1.1.2 |
| `psr/cache` | ajouté (transitif) | 1.0.1 |
| `psr/container` | ajouté (transitif) | 1.1.1 |
| `psr/link` | ajouté (transitif) | 1.0.0 |
| `psr/simple-cache` | ajouté (transitif) | 1.0.1 |
| `symfony/polyfill-php54`, `symfony/polyfill-php55` | retirés (transitifs) | v1.20.0 |
| `symfony/polyfill-util` | retiré (transitif) | v1.43.0 |

Composants nouveaux inclus dans `symfony/symfony` 3.4 : Cache, Dotenv, Inflector, Lock, WebLink, WebServerBundle,
Workflow ; retirés : Locale, SwiftmailerBridge. `symfony/symfony` déclare désormais `conflict monolog/monolog >=2`.

Point d'attention : la contrainte passe d'une version exacte à un intervalle (`3.4.*`) alors que les autres
dépendances directes restent épinglées. Sans effet tant que l'installation se fait depuis le lock (conventions § 3),
mais à harmoniser si l'épinglage exact est la règle du projet.

## 3. Tests

### 3.1 Résultat final (itération 2)

| Suite | `app` (step-01) ✔ / ✘ / ↷ | `app-ref` (step-00) ✔ / ✘ / ↷ |
|---|---|---|
| http | 172 / 0 / 0 | — |
| cli | 37 / 0 / 0 | — |
| edge | 49 / 0 / 0 | — |
| diff | 146 / 0 / 0 | — |
| **Total** | **404 / 0 / 0** | **404 / 0 / 0** |

Source : `.claude/state/migration/test-results/junit-{http,cli,edge,diff}.xml` et `junit-*.ref.xml`
(`junit-summary.py`, aucun test non tagué `BH`). Pas de suite `db` ni `unit` dans ce run.
Couverture et MSI : **non mesurées** à step-01 (`coverage/summary.json` absent).

### 3.2 Déroulé de la marche (depuis `state.json`)

| Heure (UTC) | Phase | Résultat |
|---|---|---|
| 12:19 | prereq | environnement sain, 404/404 sur `app` (code step-00), gel de `tests/` activé |
| 12:19 | snapshot | empreintes de la baseline réutilisées (code identique à `7af2a10`) |
| 12:22 → 12:33 | fix-deprecations (2.8) | lot 1 types de formulaire (4 fichiers), lot 2 `ChoiceType` (`VehicleType`), lot 3 sécurité (`isGranted()`) ; 404/404 après chaque lot ; plus aucune dépréciation runtime ; G-DEPREC (step-00) levée |
| 12:38 | upgrade | `symfony/symfony` 2.8.52 → 3.4.49 (migrator) |
| 12:40 | verify, itération 1 | **299 / 404 rouges** (http 165, cli 4, edge 7, diff 123) → `/failure-triage` : 7 familles |
| 12:48 → 12:50 | G-BEHAVIOR | utilisateur : ACC-001 et ACC-002 acceptés ; consignation dans `accepted-changes.md` ; migrator corrige B, C, D, G |
| 12:56 → 13:15 | dégel (utilisateur) | `test-writer --fix` : registre `AcceptedChanges`, familles A, E, F ; regel |
| 13:19 | verify, itération 2 | **404 / 404 verts** sur `app-ref` et `app` |
| 13:26 → 13:28 | contrôles | vuln-audit (1 fixed / 0 introduced), deprecation-check (9 fixed / 3 introduced → **G-DEPREC**), inventaire (pas de G-INVENTORY) |

### 3.3 Verdicts du tri (itération 1, `.claude/state/migration/triage/step-01.md`)

| Famille | Fiches | Tests | Verdict | Destinataire | Issue |
|---|---|---:|---|---|---|
| A — `Cache-Control` / `Expires` | BH-001…337, BH-460 | 282 | BEHAVIOR-CHANGE | utilisateur (G-BEHAVIOR) | **ACC-001** accepté ; tests alignés |
| B — kilométrage décimal à virgule refusé | BH-318, BH-164 | 4 | REGRESSION | migrator | corrigé : `LegacyIntegerToLocalizedStringTransformer` |
| C — `Allow` sans `HEAD` sur 405 | BH-026 (+ BH-056, 137, 177, 221, 328 masqués) | 1 | REGRESSION | migrator | corrigé : `LegacyAllowHeaderListener` |
| D — `--month=` vide accepté | BH-415 | 2 | REGRESSION | migrator | corrigé : `FleetReportCommand` |
| E — habillage du bloc d'exception console | BH-416 | 3 | TEST | test-writer | corrigé : extraction de la seule ligne de message |
| F — `list` / `help` | BH-425, BH-459 | 4 | BEHAVIOR-CHANGE | utilisateur (G-BEHAVIOR) | **ACC-002** accepté ; tests alignés |
| G — nouvelles entrées au journal PHP | BH-418, 419, 458 (+ toutes les erreurs 404/405/500, BH-416 masqués) | 3 | REGRESSION | migrator | corrigé : `logger` = `NullLogger` |
| ENV | — | 0 | — | — | — |

Le tri signalait que 282 tests s'arrêtaient en A2 et que leurs assertions A3 (corps), A4 (base) et A5 (journal)
n'avaient pas été évaluées. L'itération 2 étant entièrement verte, ces assertions ont été évaluées et passent :
aucun écart masqué résiduel.

### 3.4 Contrôle des tests modifiés pendant le dégel (lecture seule)

Fichiers modifiés : `tests/src/Support/{AcceptedChanges (nouveau), CaseTestCase, ConsoleCommands, ConsoleDiffTestCase,
ConsoleRun, DiffTestCase, FormDiffTestCase, HttpClient, HttpResponse}.php`, `tests/src/Cli/{FleetDemoLoadTest,
FleetReportTest}.php`, `tests/src/Diff/{ApiVehiclesDiffTest.php, README.md}`, `tests/src/Http/ApiVehiclesTest.php`,
`docker-compose.yml` (`APP_STEP`, `REF_STEP` du conteneur `tests`), `tmp/tests-application.md`. **Aucun golden ni
aucune fixture modifiés.**

| Changement | Rattachement | Constat |
|---|---|---|
| A2 `Cache-Control` / `Expires` (http, edge) via `AcceptedChanges::assertCacheHeaders` | ACC-001 | valeur **exacte** attendue selon l'étape de la cible et l'usage de la session ; `Expires` attendu présent ou absent (valeur seule ignorée, comme le prévoit l'ACC). Pas d'assouplissement |
| Diff : `comparableCacheHeaders` | ACC-001 | seule une valeur conforme à l'ACC est ramenée à son équivalent step-00 ; toute autre valeur fait échouer. `Expires` désormais comparé (plus strict qu'avant) |
| `FormDiffTestCase` : liste d'en-têtes propre supprimée au profit de celle de `DiffTestCase` | refactorisation | listes identiques (`location`, `content-type`, `x-fleet-version`, `cache-control`, `allow`, `www-authenticate`) : neutre |
| `list` / `help` : goldens complets consultés seulement avant step-01 ; partie `fleet:*` vérifiée à toutes les étapes (nom, description, synopsis, options propres, défaut de `--month` = `2026-01`, aide, code de retour) | ACC-002 | conforme au périmètre de l'ACC. La regex du défaut de `--month` n'exige plus `-m,` en tête, mais l'assertion `helpContract` vérifie la ligne complète `-m, --month=MONTH` : pas de perte |
| `ConsoleRun::exceptionMessage` : seule la ligne de message de l'encadré, titre `[Classe]` (2.8) et localisation `In <fichier> line <n>:` (3.4) ignorés | BH-416, verdict TEST | conforme au § 6.1 du gabarit (habillage non contractuel). S'applique aussi à BH-415, 418, 419, 458, dont les attentes sont inchangées. A5 de BH-416 (journal vide) toujours vérifiée |

**Conclusion : aucun assouplissement au-delà d'ACC-001, ACC-002 et du verdict TEST de BH-416.** Deux points de
vigilance, sans remise en cause du verdict :

1. **Critère « session utilisée » d'ACC-001** (`AcceptedChanges::sessionUsed`) : « la réponse pose `PHPSESSID`, ou la
   requête l'envoyait, **sauf 405** ». L'exception 405 a été établie par observation de `app` step-01 (rejet par le
   routage avant toute lecture de session), pas par le texte de l'ACC. Elle est cohérente avec l'ACC (« requête qui a
   démarré ou utilisé la session ») et ne masque rien d'autre que `Cache-Control` / `Expires`, mais c'est une règle
   empirique à re-vérifier à chaque marche (ordre routage / session modifié en 4.x, 5.x, 6.x).
2. Les tests portent désormais une logique par étape (`APP_STEP` lu depuis `docker/step.env`). Si `step.env` et le
   code servi divergent, les attentes seraient fausses : la cohérence repose sur `/upgrade-step`.

## 4. Inventaire (`json-diff.py`, step-00 → step-01)

| Type | Clé | + | − | ~ | G-INVENTORY |
|---|---|---:|---:|---:|---|
| routes | `name` | 0 | 0 | 2 | non — seuls les numéros de ligne (`ReservationController.php:17→19`, `:46→48`) |
| commands | `name` | 0 | 0 | 0 | non |
| forms | `block_prefix` | 0 | 0 | 3 | non — `type_raw` en `::class`, `getName()` → `getBlockPrefix()` (préfixes `reservation` / `vehicle` observés inchangés), `isSubmitted() && isValid()`, transformateur de kilométrage hérité, `choices` de `vehicle[status]` via `array_flip()` |
| entities | `table` | 0 | 0 | 0 | non (DDL identique) |
| templates | `path` | 0 | 0 | 0 | non |
| services | `id` | 4 | 1 | 10 | hors gate — **− `security.context`** (supprimé en 3.0, plus aucun consommateur) ; + `security.authorization_checker`, `app.listener.legacy_allow_header`, `app.session.handler.native`, `logger` ; `public: true → false` pour les services `app.*` (cf. DEP-0048) |
| config | `key` | 2 | 0 | 2 | hors gate — `framework.session.handler_id` `null → app.session.handler.native` ; `security.firewalls.main.anonymous.secret` non résolu ; + `use_strict_mode` (sans effet constaté) ; + `logout_on_user_change: false` (DEP-0049) |

Éléments disparus ou modifiés : tous correspondent soit à un changement prévu par la marche, soit à du code de
préservation (§ 7) ; aucun ne touche une interface. Le rendu HTML de `vehicle[status]` n'a pas été observé par
l'inventaire, mais il est couvert par les goldens BH-303 / BH-323, verts.

## 5. Dépréciations (`docs/migration/deprecations/step-01.md`, `deprecations.json`)

| Statut | Nombre | Identifiants |
|---|---:|---|
| Résolues (`fixed_in_step = step-01`) | 9 | DEP-0001 à DEP-0008, DEP-0010 |
| Nouvelles (`introduced`) | 3 | **DEP-0048** services privés via `get()` (839 occurrences) ; **DEP-0049** `logout_on_user_change` ; **DEP-0050** `GetResponseForExceptionEvent` dans `LegacyAllowHeaderListener` (code ajouté par la marche) |
| Persistantes | 38 | dont DEP-0009 désormais confirmée au runtime |
| **Total ouvert** | **41** | |

Fusion réelle effectuée (`track-merge.py`, entrée `step-01` avec le sha256 du lock, `id_seq` 47 → 50).

**Bloquantes pour step-02 (6)** : DEP-0048, DEP-0049, DEP-0009 (commandes auto-enregistrées : disparaissent en 4.0),
DEP-0029 (alias `AppBundle:`), DEP-0012 et DEP-0018 (reclassées step-04 → step-02 : dépendent de
`app/Resources/views`, déplacé par Flex). Ensuite : 15 bloquent step-04, 4 step-05, 7 step-06, 9 non bloquantes.

**Gate `G-DEPREC` ouverte** (alerte du 13:27) : 3 `introduced` à arbitrer par l'utilisateur.

## 6. Vulnérabilités (`docs/security/steps/step-01.md`, `vulnerabilities.json`)

- Résolue : **GHSA-5pv8-ppvj-4h68 / CVE-2021-21424** (énumération d'utilisateurs, `symfony/symfony`, medium 5.3,
  `used-not-reachable`), corrigée par 3.4.49. Sans effet observable (le 401 Basic était déjà identique).
- Ajoutées : **0** ; réapparitions : 0 ; acceptations échues : aucune (aucune entrée `accepted`).
- Persistantes : **110** ouvertes.

| Sévérité | composer | php-runtime | config | os-image | Total |
|---|---:|---:|---:|---:|---:|
| critical | 1 | 2 | 0 | 35 | 38 |
| high | 10 | 1 | 2 | 12 | 25 |
| medium | 14 | 7 | 2 | 0 | 23 |
| low | 11 | 0 | 1 | 0 | 12 |
| non notée | 12 | 0 | 0 | 0 | 12 |
| **Total** | 48 | 10 | 5 | 47 | **110** |

Exposition : 10 `reachable`, 31 `used-not-reachable`, 68 `not-used`, 1 `unknown`. Gate `G-VULN` : **non levée**.
Prochaine disparition prévue : GHSA-3gv2-29qc-v67m (fixation CSRF, reachable) et CVE-2022-39261 (Twig) à step-02.

**Point d'attention commit** : la dérogation durable des commits (CLAUDE.md) vise strictement `symfony/symfony`
**2.8.52**. Les avis désormais rattachés à **3.4.49** sortent de son périmètre (« autre version ») : leur traitement
au `/step-commit` de step-01 relève d'une décision de l'utilisateur.

## 7. Changements de comportement acceptés et code de préservation

### 7.1 Changements acceptés (`docs/migration/accepted-changes.md`)

| ACC | Fiches | Résumé | Décision |
|---|---|---|---|
| ACC-001 | BH-001…337, BH-460 | `Cache-Control: no-cache` → `no-cache, private` (sans session) ou `max-age=0, must-revalidate, private` + `Expires` (avec session) | utilisateur, 2026-11-17, G-BEHAVIOR |
| ACC-002 | BH-425, BH-459 | en-tête de version et options globales (`--shell`, `--process-isolation`) de `list` / `help` hors contrat | utilisateur, 2026-11-17, G-BEHAVIOR |

Option non retenue, à rappeler : le refus de `1500,7` (famille B) aurait corrigé un bug historique ; l'utilisateur ne
l'a pas demandé, le comportement 2.8 est donc préservé.

### 7.2 Code ajouté pour préserver le comportement 2.8, à revoir aux marches suivantes

| Code | Rôle | À revoir |
|---|---|---|
| `app.session.handler.native` (`\SessionHandler`, `framework.session.handler_id`) | évite l'encapsulation `StrictSessionHandler` de 3.4, qui supprimait le cookie `PHPSESSID` des sessions vides | step-02 (config Flex `config/packages/framework.yaml`) puis step-04 / step-06 : la valeur par défaut du gestionnaire change ; à réexaminer si `session.use_strict_mode=1` est activé un jour (protection contre la fixation) |
| `logger` = `Psr\Log\NullLogger` | 2.8 n'avait aucun journaliseur ; 3.4 écrit sinon les exceptions sur stderr (famille G) | à chaque marche : une recette Flex ou l'ajout de MonologBundle réintroduirait la journalisation (A5 rouge). Faiblesse OWASP A09 conservée volontairement : candidat à une décision hors migration |
| `AppBundle\EventListener\LegacyAllowHeaderListener` (`kernel.exception`, priorité 64) | rajoute `HEAD` après `GET` dans `Allow` et le message des 405 | **DEP-0050** : `GetResponseForExceptionEvent` / `getException()` / `setException()` → `ExceptionEvent` / `getThrowable()` après le composer update de step-02 (pas avant : absents en 3.4) ; revérifier le comportement du matcher à chaque marche (la règle pourrait devenir inutile, ou doublonner `HEAD`) |
| `AppBundle\Form\DataTransformer\LegacyIntegerToLocalizedStringTransformer` (champs `mileage`, `endKm`) | arrondi vers le bas d'un kilométrage décimal au lieu du rejet de 3.x | appelle la méthode du grand-parent `NumberToLocalizedStringTransformer::reverseTransform()` : constructeur modifié en 4.2 (`$scale`), comportement interne à revérifier à step-02, step-04, step-06 (fiches BH-162 à BH-168, BH-317 à BH-321) |
| `FleetReportCommand` : `--month` vide → `Console\Exception\RuntimeException('The "--month" option requires a value.')` | Console 3.x transmet `''` au lieu de lever l'erreur de syntaxe | revérifier BH-415 à chaque marche Console (format du message, flux stderr, code 1) ; la commande devra aussi devenir un service (DEP-0009) |
| `AdminController::isGranted('ROLE_ADMIN')` (au lieu de `security.context`) | lot 3 | garder la double vérification jusqu'à step-04 (GHSA-3rg7-wf37-54rm, PATH_INFO, reachable atténuée) |
| `getBlockPrefix()` explicite dans `VehicleType` / `ReservationType` | conserve les noms HTML `vehicle[…]` / `reservation[…]` | à conserver lors du passage `AppBundle` → `App` (le préfixe deviné changerait) |

## 8. Gates et décisions (`state.json`)

| Gate | Statut | Détail |
|---|---|---|
| G-DEPREC (step-00 → lots 1-3) | levée 12:33 | lots 1 à 3 appliqués, aucune dépréciation runtime avant montée |
| G-BEHAVIOR | tranchée 12:48 | ACC-001 et ACC-002 acceptés par l'utilisateur, consignés par l'auditeur 12:50 |
| G-FROZEN / gel | levé 12:56, rétabli 13:15 | dégel par l'utilisateur pour `test-writer --fix` (familles A, E, F) |
| G-TESTS | non levée | vert à l'itération 2 / 3 |
| G-INVENTORY | non levée | aucune interface disparue ou modifiée |
| G-VULN | non levée | 0 introduced, 1 fixed |
| **G-DEPREC (step-01)** | **ouverte** | DEP-0048, DEP-0049, DEP-0050 `introduced` non arbitrées |

## 9. Points d'attention pour step-02 (Symfony 4.4, PHP 7.2)

Notes de `steps.json` : structure Flex (`config/`, `public/`, `templates/`), `AppBundle` → `App`, autowiring, Twig 2.

1. **Dette de dépréciations** (plan de lots du rapport de dépréciations, § 4) :
   - à faire **sous 3.4, avant la montée**, suite gelée : lot 4 services privés (DEP-0048, `public: true` sur
     `app.reservation_manager` et `app.plate_formatter`), lot 5 console (DEP-0009, commandes en services non
     paresseuses pour ne pas changer `list`/`help`), lot 6 sécurité (DEP-0049), lot 7 alias Doctrine (DEP-0029),
     lot 8 templates (DEP-0018 dès step-01, DEP-0012 avec le déplacement Flex) ;
   - lot 9 (facultatif) : routage Sensio (DEP-0011, Rector) ;
   - lot 10, **après** le composer update de step-02 : DEP-0050, DEP-0019, DEP-0033, puis DEP-0014 (seulement après
     suppression des `$this->get('app.*')`).
2. **Dépendance bloquante** : `sensio/framework-extra-bundle` 3.0.29 interdit `symfony/symfony` 4.4 → passer en 5.x
   (ajout d'un saut de version de dépendance à signaler) ; `doctrine/doctrine-bundle` 1.6.13 et `symfony/security-acl`
   3.0.4 acceptent 4.x. Twig 1 → 2 émettra DEP-0033, 0035, 0036. Le passage aux composants Flex fera apparaître une
   `G-VULN` de renommage (`symfony/symfony` → composants) attendue.
3. **Code de préservation** à porter dans la structure Flex sans changement de comportement (§ 7.2) : gestionnaire de
   session natif, `NullLogger` (attention aux recettes Flex qui ajoutent Monolog), écouteur `Allow`, transformateur de
   kilométrage, préfixes de formulaires.
4. **ACC-001** : l'option `AbstractSessionListener::NO_AUTO_CACHE_CONTROL_HEADER` devient disponible en 4.1. L'ACC est
   accepté : ne pas « restaurer » `no-cache` sans nouvelle décision (ce serait un nouveau changement de comportement).
5. Le critère « session utilisée » des tests (exception 405) et l'extraction du message d'exception console sont à
   reconfirmer au premier run de step-02.

## 10. Risques résiduels

- **Couverture et MSI non mesurées** : la complétude des 404 tests vis-à-vis du code migré (dont les 5 ajouts de
  préservation) n'est pas chiffrée ; recommandé avant de valider step-02 (`/coverage`, `/mutation-check`).
- Exposition sécurité inchangée : 10 failles `reachable` (Apache de l'image Debian 10, PHP EOL, PATH_INFO atténuée,
  CSRF fixation jusqu'à step-02, configuration CFG-002/003/004) ; faiblesse de journalisation conservée par
  `NullLogger`.
- `composer.json` : `symfony/symfony` en `3.4.*` (intervalle) au lieu d'une version exacte.
- Code de la marche non commité : le rapport, les tests adaptés et le code doivent être commités séparément selon
  les règles du projet (tests en commit `test(flotte): …` dédié, marche via `/step-commit` mode marche).

## Décision G-DEPREC (après rédaction du rapport)

Le 2026-11-17, l'utilisateur (formateur) a accepté les trois dépréciations introduites par Symfony 3.4, avec échéance,
consignées par `.claude/bin/track-merge.py deprecations --accept` dans `docs/migration/deprecations.json` :

| Dépréciation | Échéance | Traitement prévu |
|---|---|---|
| DEP-0048 — services privés récupérés par `get()` | step-02 | `/fix-deprecations` au début de step-02 |
| DEP-0049 — `logout_on_user_change` non activé | step-02 | arbitrage et correction au début de step-02 |
| DEP-0050 — `GetResponseForExceptionEvent` (`LegacyAllowHeaderListener`) | step-04 | Rector après le composer update de step-02 |

La gate G-DEPREC est donc refermée ; aucune gate n'est ouverte en fin de step-01.
