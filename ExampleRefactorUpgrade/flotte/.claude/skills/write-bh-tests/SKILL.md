---
name: write-bh-tests
description: Écrit les tests de caractérisation d'une couche (http, cli, db, edge, diff, unit) à partir des seules fiches BH, de l'inventaire et du plan de tests, via l'agent isolé test-writer ; crée le squelette du projet de tests s'il n'existe pas ; met à jour le document de fiches tmp/tests-application.md.
when_to_use: Dans /build-test-suite (http et cli après /characterize, puis db, edge, unit, enfin diff) ; pour compléter une couche ou une zone après /test-plan --gaps ou un nouveau piège signalé par /compat-scan ou /deprecation-check ; en correction après /failure-triage (--fix).
argument-hint: "<http|cli|db|edge|diff|unit> [<zone>|<piège>|schema|all] [--fix]"
context: fork
agent: test-writer
---

# Tests de caractérisation (fiches BH)

Arguments : `$ARGUMENTS` — **couche** (obligatoire), puis cible de la couche, puis `--fix` = mode correction.

| Couche | Cible | Fichier de couche | Suite | Fiches |
|---|---|---|---|---|
| `http` | `<zone>` (`docs/migration/behaviors/<zone>.md`) | `layers/http.md` | `http` | couche `http` |
| `cli` | `<zone>` (`cli-*`) | `layers/cli.md` | `cli` | couche `cli` |
| `db` | `<zone>` ou `schema` | `layers/db.md` | `db` | couche `db` |
| `edge` | `<piège>` (défaut : tous) | `layers/edge.md` | `edge` | couche `edge` |
| `diff` | `<zone>` ou `all` | `layers/diff.md` | `diff` | toutes les fiches de la zone + couche `diff` |
| `unit` | `<zone>` (`domain-*`, `twig-*`) | `layers/unit.md` | `unit` | couche `unit` (champ Contrat) |

Couche absente ou inconnue : arrête-toi et rappelle le tableau ci-dessus. **Lis** ensuite
`.claude/skills/write-bh-tests/layers/<couche>.md` : il complète (ou, quand il le dit, remplace) les règles et
étapes communes ci-dessous.

Référence : `.claude/reference/conventions.md` § 2, § 3, § 5.1, § 5.2, § 5.5, § 5.6 ; plan
`tmp/plan-migration-php84-sf64.md` § 3 et § 6.

## Règles communes

- Sources **uniquement** : `docs/migration/behaviors/`, `docs/migration/inventory/`, `docs/migration/test-plan.md`,
  `tests/`, `README.md`, `CLAUDE.md`, `tmp/`, `.claude/reference/`, `.claude/skills/write-bh-tests/`,
  `.claude/state/migration/feedback-tests.md`.
  **Aucun accès** à `src/`, `app/`, `web/` (bloqué par le hook) : l'application ne se voit que par HTTP et par ses
  commandes métier.
- Écriture **uniquement** dans `tests/` (`tests/unit/` réservé à la couche `unit`).
- Si `.claude/state/migration/state.json` a `"frozen": true` : n'écris rien, rends « zone gelée (G-FROZEN) ».
- Un test minimum par fiche `BH-xxx` de la couche ; nommage et groupes selon § 5.2
  (`bh012_status_filter_with_non_numeric_value`, `#[Group('BH-012')]`, `#[Group('zone-<zone>')]`).
- Aucun test ne référence une classe de l'application (sauf couche `unit`, via le contrat) ; chaque test restaure
  son scénario (`Fixtures::restore()`).
- Les tests doivent être **verts sur `app-ref`** : c'est la définition même d'un test de caractérisation. Un test
  rouge sur `app-ref` est un test faux (ou une fiche fausse → le signaler, ne pas « arranger » l'assertion).

## Squelette du projet de tests (si `tests/phpunit.xml.dist` est absent)

Crée, une seule fois, quelle que soit la couche demandée, ce qui manque — `composer.json`, `bin/seed.php`,
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
    ├── Console.php        exécution d'une commande console de la cible (cf. couche `cli`)
    ├── Golden.php         assertMatchesGolden(<nom>, <contenu normalisé>) ; écrit seulement si
    │                      GOLDEN_RECORD=1 (piloté par /golden-master)
    └── CaseTestCase.php   classe de base : restauration du scénario déclaré par #[Scenario('…')], cible
```

Le projet de tests vise **PHP 8.4** (conteneur `tests`), indépendamment du PHP de l'application.

## Étapes communes

1. Vérifie l'état gelé ; lis les fiches de la cible, l'inventaire utile à la couche, le `test-plan.md`.
   **Mode `--fix`** : lis `.claude/state/migration/feedback-tests.md` ; ne traite que les `BH` listés ; s'il contient
   « Aucun retour. », arrête-toi.
2. Squelette si nécessaire (ci-dessus).
3. Écris les tests selon le fichier de couche.
4. Exécute-les sur la référence : `.claude/bin/tests.sh --suite <suite> [--filter <Classe>] --target ref`. Corrige
   **le test** tant qu'il ne reflète pas la fiche ; si la fiche contredit l'observation, arrête-toi sur ce test et
   signale-le.
5. Mets à jour le document de fiches (ci-dessous), puis rends ton rapport.

## Mise à jour du document de fiches (obligatoire après chaque implémentation)

Document concerné : **`tmp/tests-application.md`** (document unique ; section du groupe testé : § 2
`VehicleController`, § 3 `ReservationController`, § 4 `ApiController`, § 5 `AdminController`, § 6 commandes). Le
mettre à jour **dans toutes ses parties** (`.claude/reference/conventions.md` § 5.6) :

1. **Lignes de fiches** : pour chaque fiche implémentée dont la valeur observée sur `app-ref` diffère de la fiche, ou
   était « à confirmer » / « déduite », corriger la cellule avec la **valeur observée** et ajouter
   `(observé à l'implémentation)`. Jamais de fiche supprimée ni renumérotée ; jamais de valeur ajustée sans
   observation sur la référence.
2. **Journal d'implémentation** (section unique en fin de document, avant « Blocages ») : une ligne par fiche —
   `| date | BH-NNN | Classe::méthode | suite(s) | golden(s) | app-ref ✔/✘ | écart corrigé oui/non |`.
3. **Blocages** : `- BH-NNN : ⏸ <raison>` pour chaque fiche non implémentée faute de décision, de reproductibilité ou
   d'environnement ; retirer la ligne d'un blocage levé.
4. **Points à trancher** (sous-section du groupe) : ne rien décider ; ajouter `→ bloque BH-NNN` à la ligne concernée si nécessaire.
5. **Exemple complet** du document : aligner ses valeurs sur le test si elles ont changé.
6. **Bloc de suivi** : lancer `.claude/bin/tests.sh --suite <suite> --target ref` (qui régénère le bloc), puis
   `.claude/bin/test-tracking.py` après les dernières modifications.

Rapport final : lister les fiches dont la ligne a été corrigée (avant → après) et les blocages ajoutés.

## Rapport final

- Couche, cible ; fichiers créés / modifiés ; matrice `BH-xxx → Classe::méthode`.
- Résultat sur `app-ref` (vert / rouge par BH).
- Fiches ambiguës ou contredites par l'observation (pour `/characterize`).
- Compléments propres à la couche (voir son fichier).
- Document de fiches : lignes corrigées (avant → après), blocages ajoutés.
- Liste des fichiers à commiter.

## Après la skill

La session principale lance **`/step-commit`** (mode hors marche) pour les fichiers produits **et le document de
fiches mis à jour** (règle « Commit automatique des tests » de `CLAUDE.md` ; l'agent `test-writer` n'a pas accès à
git).
