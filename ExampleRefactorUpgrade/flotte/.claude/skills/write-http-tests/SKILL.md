---
name: write-http-tests
description: Écrit les tests boîte noire HTTP (pages, formulaires, CSRF, authentification, codes de statut, redirections, HTML normalisé, JSON octet par octet) d'une zone à partir de ses seules fiches BH, via l'agent isolé test-writer ; crée le squelette du projet de tests s'il n'existe pas.
when_to_use: Dans /build-test-suite après /characterize ; pour compléter une zone après /test-plan --gaps ; en correction après /failure-triage (--fix).
argument-hint: "<zone> [--fix]"
context: fork
agent: test-writer
---

# Tests HTTP boîte noire

Arguments : `$ARGUMENTS` — zone (`docs/migration/behaviors/<zone>.md`) ; `--fix` = mode correction.

Référence : `.claude/reference/conventions.md` § 2, § 3, § 5.1, § 5.2, § 5.5 ; plan § 3.1 et § 6.

## Règles

- Sources **uniquement** : `docs/migration/behaviors/`, `docs/migration/inventory/`, `docs/migration/test-plan.md`,
  `tests/`, `README.md`, `CLAUDE.md`, `tmp/`, `.claude/reference/`, `.claude/state/migration/feedback-tests.md`.
  **Aucun accès** à `src/`, `app/`, `web/` (bloqué par le hook) : l'application ne se voit que par HTTP.
- Écriture **uniquement** dans `tests/` (hors `tests/unit/` pour cette skill).
- Si `.claude/state/migration/state.json` a `"frozen": true` : n'écris rien, rends « zone gelée (G-FROZEN) ».
- Un test minimum par fiche `BH-xxx` de couche `http` ; nommage et groupes selon § 5.2
  (`bh012_status_filter_with_non_numeric_value`, `#[Group('BH-012')]`, `#[Group('zone-<zone>')]`).
- Aucun test ne référence une classe de l'application ; chaque test restaure son scénario (`Fixtures::restore()`).
- Les tests doivent être **verts sur `app-ref`** : c'est la définition même d'un test de caractérisation. Un test
  rouge sur `app-ref` est un test faux (ou une fiche fausse → le signaler, ne pas « arranger » l'assertion).

## Squelette du projet de tests (si `tests/phpunit.xml.dist` est absent)

Crée, une seule fois (les autres `write-*` renvoient ici), ce qui manque — `composer.json`, `bin/seed.php`,
`fixtures/` (scénarios, `schema.sql`) existent déjà, ne pas les réécrire :

```
tests/
├── composer.json          require-dev : phpunit/phpunit ^11, symfony/http-client, symfony/dom-crawler,
│                          symfony/css-selector, symfony/yaml ; "php": "^8.4" ; autoload-dev
│                          "Flotte\\Tests\\": "src/"
├── phpunit.xml.dist       suites http, cli, db, edge, diff (src/Http, src/Cli, src/Db, src/Edge, src/Diff) ;
│                          failOnWarning, failOnRisky, executionOrder="defects" désactivé (ordre déterministe) ;
│                          variables APP_URL, REF_URL, APP_DB, REF_DB, FIXTURES_DIR, GOLDEN_DIR, TARGET
├── bin/seed.php           chargeur de scénarios (PDO SQLite), utilisé par .claude/bin/seed.sh
├── fixtures/README.md     scénarios (plan § 6.2) et fiches BH qu'ils alimentent
├── golden/                sorties de référence (/golden-master)
└── src/Support/
    ├── Target.php         cible "app" | "ref" (variable TARGET, défaut app) → URL et chemin de base
    ├── HttpClient.php     client sans suivi automatique des redirections, cookies par session, lecture
    │                      du jeton CSRF depuis un formulaire, Basic auth
    ├── Fixtures.php       restore(<scenario>, <target>) : copie fixtures/db/<scenario>.sqlite vers la base
    │                      de la cible, vérifie le .sha256
    ├── Db.php             lecture SQLite de la cible (assertions sur l'état), dump canonique trié
    ├── Normalizer.php     HTML : supprime jetons CSRF, en-tête/version, espaces non significatifs,
    │                      attributs volatils ; JSON : ré-encodage canonique OPTIONNEL (par défaut comparaison
    │                      octet par octet) ; CLI : fins de ligne, largeur de tableau
    ├── Console.php        exécution d'une commande console de la cible (cf. write-cli-tests)
    ├── Golden.php         assertMatchesGolden(<nom>, <contenu normalisé>) ; écrit seulement si
    │                      GOLDEN_RECORD=1 (piloté par /golden-master)
    └── CaseTestCase.php   classe de base : restauration du scénario déclaré par #[Scenario('…')], cible
```

Le projet de tests vise **PHP 8.4** (conteneur `tests`), indépendamment du PHP de l'application.

## Étapes

1. Vérifie l'état gelé ; lis la fiche de zone, l'inventaire (`routes.json`, `forms.json`), le `test-plan.md`.
   **Mode `--fix`** : lis `.claude/state/migration/feedback-tests.md` ; ne traite que les `BH` listés ; s'il contient
   « Aucun retour. », arrête-toi.
2. Squelette si nécessaire (ci-dessus).
3. Écris `tests/src/Http/<Zone>Test.php` : pour chaque fiche `http` :
   - restauration du scénario de la fiche ;
   - requête exacte de la fiche (méthode, chemin, paramètres, session, CSRF, Basic auth) ;
   - assertions : statut, en-têtes observables (`Location`, `Content-Type`), contenu normalisé (sélecteurs DOM
     précis pour le HTML ; JSON **octet par octet** pour l'API), message flash après redirection, état de la base
     via `Db` pour les écritures ;
   - pour une page entière dont la fiche demande la forme exacte : `Golden::assertMatchesGolden`.
4. Exécute la zone sur la référence : `.claude/bin/tests.sh --suite http --filter <Zone>Test` avec `TARGET=ref`
   (option `--target ref` si le script la propose, sinon variable d'environnement de la suite). Corrige **le test**
   tant qu'il ne reflète pas la fiche ; si la fiche contredit l'observation, arrête-toi sur ce test et signale-le.
5. Rends ton rapport.

## Rapport final

- Fichiers créés / modifiés ; matrice `BH-xxx → classe::méthode`.
- Résultat sur `app-ref` (vert / rouge par BH).
- Fiches ambiguës ou contredites par l'observation (pour `/characterize`).

## Après la skill

La session principale lance **`/step-commit`** (mode hors marche) pour les fichiers produits (règle « Commit automatique des tests » de
`CLAUDE.md` ; l'agent `test-writer` n'a pas accès à git). Signaler dans le rapport final la liste des fichiers à commiter.
