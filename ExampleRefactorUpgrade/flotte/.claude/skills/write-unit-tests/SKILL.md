---
name: write-unit-tests
description: Écrit les tests unitaires des classes du domaine (ReservationManager, PlateFormatter, BelgianPlateValidator, entités, FleetExtension) dans tests/unit à partir du champ Contrat des fiches BH, via l'agent isolé test-writer ; exécutés dans le conteneur app (ou app-ref) avec le PHPUnit de l'image.
when_to_use: Dans /build-test-suite après les tests boîte noire (couche complémentaire) ; correction (--fix).
argument-hint: "<zone domain-*|twig-*> [--fix]"
context: fork
agent: test-writer
---

# Tests unitaires du domaine (couche complémentaire)

Arguments : `$ARGUMENTS`. Référence : `.claude/reference/conventions.md` § 2 (`tests/unit/`), § 5.1 (champ
Contrat), § 5.2 ; plan § 3.2.

Règles, gel, `--fix`, « vert sur `app-ref` » : **identiques à `.claude/skills/write-http-tests/SKILL.md`**. Tu
n'as **pas** accès au code : la seule source d'API est le champ **Contrat** des fiches. Un contrat incomplet
(signature, dépendance, type de retour manquant) → pas de test pour cette fiche, signalé dans le rapport.

## Spécificités

- Emplacement : `tests/unit/<Zone>/…Test.php`, namespace `Flotte\Tests\Unit\…` ; une méthode par fiche `unit`.
- **Exécution dans le conteneur `app`** (PHP de l'étape, autoload de l'application) via
  `.claude/bin/tests.sh --suite unit [--target ref]` (PHPUnit installé dans l'image app selon la version de PHP :
  8.5 pour PHP 7.2, 9.6 pour 7.4/8.1, 11 pour 8.4 — écrire des tests compatibles 8.5 → 11 : annotations `@group`,
  pas d'attributs, aucune API supprimée entre ces versions).
- **Compatibilité large** : ces tests doivent tourner de PHP 7.2 à 8.4 et de PHPUnit 6 à 11 :
  - pas d'attributs PHP 8 ni de types de retour/propriétés typées dans les tests ; groupes en annotations
    docblock (`@group BH-031`) **en plus** de la convention de nommage ;
  - uniquement l'API PHPUnit stable (`assertSame`, `assertEquals`, `expectException`, `createMock`) ;
  - `protected function setUp(): void` (le type `void` est exigé par PHPUnit ≥ 8 et accepté par PHP 7.1+ même
    quand la classe parente de PHPUnit 6/7 n'en déclare pas).
- Les noms de classes de l'application changent en step-02 (`AppBundle\…` → `App\…`) : référencer les classes via
  `tests/unit/bootstrap-map.php` (table « nom logique → FQCN », la seule chose que la migration a le droit de
  mettre à jour, sur décision tracée). Le contrat des fiches donne le nom logique.
- Dépendances (EntityManager, repository) : doublures construites à partir du contrat ; ne pas tester Doctrine.

## Étapes

1. Comme `write-http-tests` étape 1 ; si `tests/unit/bootstrap-map.php` est absent, crée-le pour step-00.
2. Écris les tests ; exécute `.claude/bin/tests.sh --suite unit` (sur la référence si l'option de cible existe).
3. Rapport (même format que `write-http-tests`, plus la liste des contrats incomplets).

## Après la skill

La session principale lance **`/step-commit`** (mode hors marche) pour les fichiers produits (règle « Commit automatique des tests » de
`CLAUDE.md` ; l'agent `test-writer` n'a pas accès à git). Signaler dans le rapport final la liste des fichiers à commiter.
