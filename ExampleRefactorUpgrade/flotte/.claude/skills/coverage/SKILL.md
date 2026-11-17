---
name: coverage
description: Mesure la couverture du code de l'application (PCOV dans le conteneur app) pendant l'exécution de la suite boîte noire, produit un rapport par classe et traduit les lignes non couvertes en lacunes exprimées en zones et comportements pour /test-plan --gaps.
when_to_use: Dans /build-test-suite pour atteindre les seuils sur step-00 ; après l'ajout de tests ; avant de lancer /mutation-check.
argument-hint: "[--suite all|http|cli|…] [--threshold <pourcentage>]"
context: fork
agent: auditor
---

# Couverture de la suite boîte noire

Arguments : `$ARGUMENTS`. Conventions : `.claude/reference/conventions.md` (§ 4, § 5). Plan : § 3.5.

## Règles

- Lecture seule sur le code et les tests : tu **mesures** et tu **traduis**, tu n'écris aucun test.
- La couverture mesurée est celle de l'**application exercée** par la suite boîte noire (PCOV activé dans `app`),
  pas celle des classes de tests.
- Les lacunes transmises sont formulées en termes de **zones, interfaces et comportements** (route, commande, cas
  métier, branche fonctionnelle), jamais en extraits de code destinés à `test-writer`.

## 1. Mesurer

1. `.claude/bin/tests.sh --suite <all par défaut> --coverage` : active PCOV dans `app`, collecte la couverture de
   `src/` (et `app/` pour le noyau) pendant la suite, exporte `.claude/state/migration/coverage/clover.xml`
   (+ rapport texte).
2. Si `unit` fait partie de la suite, distinguer les deux sources (boîte noire / unitaire) : le seuil porte sur la
   **boîte noire**.

## 2. Analyser

1. Tableau par classe : lignes, lignes couvertes, %, méthodes non couvertes.
2. Pour chaque bloc non couvert, lire le code et l'inventaire (`docs/migration/inventory/<step>/`) et identifier :
   - la zone et l'interface qui permettraient de l'atteindre (route, paramètre, commande, option) ;
   - la condition fonctionnelle (ex. « retour d'une réservation déjà clôturée », « mois sans données ») ;
   - le scénario de données nécessaire (`edge`, `report-months`…) ;
   - les `BH-xxx` existants proches, ou « aucun » ;
   - si le code est **mort** (inatteignable depuis l'extérieur) : le signaler comme tel, ce n'est pas une lacune de test.
3. Comparer au seuil (`--threshold`, défaut 95 % des lignes de `src/` sur step-00).

## 3. Écrire

`docs/migration/audit/coverage-<step>.md` :
- synthèse (global, par classe, seuil atteint ou non) ;
- **lacunes** numérotées `GAP-COV-xx` : zone, interface, condition fonctionnelle, scénario, `BH` liés — format
  directement consommable par `/test-plan --gaps` ;
- code mort repéré.

## Rapport final

Pourcentage global et par classe, seuil atteint ou non, nombre de lacunes, chemin du rapport, prochaine action
(`/test-plan --gaps` ou `/mutation-check`).
