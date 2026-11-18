# Couche `http` — tests boîte noire HTTP

Pages, formulaires, CSRF, authentification, codes de statut, redirections, HTML normalisé, JSON octet par octet.
Référence : plan § 3.1. Inventaire utile : `routes.json`, `forms.json`.

## Tests

Écris `tests/src/Http/<Zone>Test.php` : pour chaque fiche `http` :
- restauration du scénario de la fiche ;
- requête exacte de la fiche (méthode, chemin, paramètres, session, CSRF, Basic auth) ;
- assertions : statut, en-têtes observables (`Location`, `Content-Type`), contenu normalisé (sélecteurs DOM
  précis pour le HTML ; JSON **octet par octet** pour l'API), message flash après redirection, état de la base
  via `Db` pour les écritures ;
- pour une page entière dont la fiche demande la forme exacte : `Golden::assertMatchesGolden`.

## Exécution

`.claude/bin/tests.sh --suite http --filter <Zone>Test --target ref`.
