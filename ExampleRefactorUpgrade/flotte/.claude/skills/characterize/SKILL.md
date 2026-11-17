---
name: characterize
description: Observe et décrit le comportement réel d'une zone de l'application (route, commande, service) sur la version de référence - entrées, sorties, effets en base, cas limites, bugs historiques - sous forme de fiches BH-xxx dans docs/migration/behaviors/<zone>.md, sans juger ni corriger.
when_to_use: Après /inventory sur step-00, dans /build-test-suite (une zone à la fois, zones indépendantes en parallèle) ; ou pour compléter une zone après /test-plan --gaps.
argument-hint: "<zone> [--gaps <liste>]"
context: fork
agent: characterizer
---

# Caractérisation d'une zone

Arguments : `$ARGUMENTS` — zone (ex. `vehicles-list`, `vehicle-show`, `reservation-new`, `reservation-return`,
`api-vehicles`, `admin-vehicle-new`, `security`, `cli-report`, `cli-demo-load`, `domain-reservation-manager`,
`domain-plate-formatter`, `domain-belgian-plate`, `domain-entities`, `twig-fleet-extension`). Avec
`--gaps`, seulement les lacunes listées (venant de `docs/migration/test-plan.md`).

Référence : `.claude/reference/conventions.md` § 4 (scripts), § 5.1 (format des fiches), plan
`tmp/plan-migration-php84-sf64.md` § 0 (pièges).

## Règles

- **Décrire, pas juger** : un bug est une fiche comme une autre (« Particularité : bug historique »). Ne jamais
  proposer de correction dans une fiche.
- **Observé, pas déduit** : toute sortie notée est **observée** sur `app-ref` (`http.sh --ref`, `console.sh --ref`)
  avec un scénario de données restauré (`seed.sh <scenario> --target ref`). Ce qui n'est que lu dans le code est
  marqué « déduit, non observé » et devient un point à observer.
- **Numérotation globale** : lis toutes les fiches de `docs/migration/behaviors/` et pars du plus grand `BH-xxx`
  existant + 1. Ne renumérote jamais une fiche existante ; une fiche obsolète est marquée « retirée » avec la raison.
- Une fiche = un comportement vérifiable par **un** test (entrée unique, sortie précise).
- Horloge : l'environnement est figé (`FAKETIME`) ; noter la date figée utilisée dans l'en-tête du fichier.
- Aucune donnée sensible recopiée (mot de passe de démonstration : écrire « identifiants de test »).

## Étapes

1. Lis l'inventaire de step-00 (`docs/migration/inventory/step-00/*.json`) : éléments de la zone (routes, formulaires,
   commandes, entités, templates, configuration observable).
2. Lis le code legacy de la zone (contrôleurs, services, entités, repositories, templates, configuration).
3. Dresse la liste des comportements candidats :
   - parcours nominal par méthode HTTP / option de commande ;
   - chaque branche du code (conditions, exceptions attrapées, `return`) ;
   - chaque erreur : 404 (id inconnu, id non numérique), 405, 403/401, formulaire invalide champ par champ, CSRF
     absent ou faux, message flash, redirection ;
   - effets en base (création, mise à jour, rien) ;
   - **pièges du plan § 0** concernant la zone (comparaisons lâches, `usort` booléen, `createFromFormat`, arrondis,
     `isValid()` sans `isSubmitted()`, `{% for … if %}`, `serialize_precision`, `date()` par défaut…) ;
   - valeurs limites : vide, 0, négatif, très grand, non numérique, accents, espaces, formats de date ambigus.
4. Pour chaque candidat : restaure le scénario adapté (`seed.sh <scenario> --target ref`), exécute la requête ou la
   commande sur `app-ref`, relève statut, extrait de sortie **normalisé** (sans jeton CSRF ni date variable), effets
   en base. Pour les formulaires : `http.sh --ref --session <z> --csrf-from <url du formulaire> POST …`.
5. Zones `domain-*` / `twig-*` : remplis le champ **Contrat** (classe, signature publique exacte, dépendances à
   fournir, valeurs de retour), indispensable au `test-writer` qui ne voit pas le code.
6. Écris ou complète `docs/migration/behaviors/<zone>.md` :
   - en-tête : zone, éléments d'inventaire couverts, scénarios utilisés, date figée, étape observée (`step-00`) ;
   - fiches au format § 5.1 ;
   - section « Points non observés » (ce qui n'a pas pu être exécuté, et pourquoi).
7. Rends ton rapport.

## Rapport final

- Fiches créées (`BH-xxx` → titre, couche de test), fiches marquées « bug historique » ou « dépend de PHP ».
- Éléments d'inventaire de la zone sans fiche.
- Points non observés, scénarios de données manquants (à ajouter dans `tests/fixtures/`).
