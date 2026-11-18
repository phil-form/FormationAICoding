# Couche `unit` — tests unitaires du domaine (couche complémentaire)

Classes du domaine (`ReservationManager`, `PlateFormatter`, `BelgianPlateValidator`, entités, `FleetExtension`).
Cible : une zone `domain-*` ou `twig-*`. Référence : conventions § 2 (`tests/unit/`), § 5.1 (champ Contrat) ;
plan § 3.2.

## Écarts aux règles communes

- Tu n'as **pas** accès au code : la seule source d'API est le champ **Contrat** des fiches. Un contrat incomplet
  (signature, dépendance, type de retour manquant) → pas de test pour cette fiche, signalé dans le rapport.
- Écriture dans `tests/unit/` (et seulement là) ; le squelette commun ne s'applique pas, sauf
  `tests/unit/bootstrap-map.php` : s'il est absent, crée-le pour step-00 (étape 1).
- Les tests référencent les classes de l'application par leur **nom logique** (voir ci-dessous), jamais en dur.

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

## Exécution

`.claude/bin/tests.sh --suite unit --target ref` (sur la référence si l'option de cible existe).

## Rapport (complément)

Liste des contrats incomplets.
