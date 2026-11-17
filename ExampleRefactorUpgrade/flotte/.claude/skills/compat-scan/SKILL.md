---
name: compat-scan
description: Analyse la compatibilité du code avec une version de PHP visée (PHPCompatibility, PHPStan à la version cible, revue des changements de sémantique silencieux comme les comparaisons chaîne/nombre ou le tri stable) et produit un rapport relié aux comportements BH-xxx à risque, sans rien corriger.
when_to_use: Avant chaque montée de PHP (appelé par /upgrade-php), ou pour évaluer l'effort d'une migration PHP.
argument-hint: "<php-version> (ex. 7.4, 8.1, 8.4)"
context: fork
agent: deprecation-analyst
---

# Compatibilité PHP

Version visée : `$ARGUMENTS`. Conventions : `.claude/reference/conventions.md` (§ 4, § 5.4). Plan : § 0 (pièges).

## Règles

- **Lecture seule** : rien n'est corrigé ici (correction : `/fix-deprecations --php <v>`).
- Les erreurs de syntaxe ou fonctions supprimées sont faciles ; le vrai risque est le **changement de sémantique
  silencieux** (le code tourne mais rend un autre résultat). Chacun doit être relié à un `BH-xxx` qui le fige, ou
  signalé comme non couvert.

## 1. Analyse statique

1. `.claude/bin/tools.sh phpcs --standard=PHPCompatibility --runtime-set testVersion <v>- --report=json src app web`
   (dossiers ajustés à la structure de l'étape : `public/`, `config/` après step-02).
2. `.claude/bin/tools.sh phpstan analyse --error-format=json` avec `phpVersion` = `<v>` (niveau adapté, baseline
   ignorée) : fonctions supprimées, signatures, types internes devenus stricts.
3. **Rector (simulation)** : `.claude/bin/rector.sh <step-NN>` de la marche PHP visée (step-03 → 7.4, step-05 → 8.1,
   step-07 → 8.4 ; `rector/step-NN.php`, liste blanche de règles de compatibilité). Diff dans
   `.claude/state/migration/rector/step-NN-dry-run.diff` : chaque règle qui s'applique = correction automatique possible
   (`autofix: "rector:<Règle complète>"`). Jamais `--apply` (lecture seule). Les changements de **comportement** de
   PHP 8 ne sont pas couverts par Rector : ils relèvent du § 2 ci-dessous. Voir `rector/README.md`.

## 2. Changements de sémantique (revue de code ciblée)

Rechercher dans `src/`, `app/` et les templates, pour la version visée :

| Changement | Version | Recherche |
|---|---|---|
| Comparaison chaîne non numérique / nombre (`"abc" == 0` devient faux) | 8.0 | `==`, `!=`, `in_array` sans strict, `switch` sur des entrées utilisateur |
| Tri stable ; comparateur renvoyant un booléen déprécié | 8.0 | `usort`, `uasort`, `uksort` |
| `null` passé à un paramètre non nullable de fonction interne | 8.1 | `number_format`, `strlen`, `trim`, `htmlspecialchars`… sur valeurs possiblement nulles |
| Propriétés dynamiques dépréciées | 8.2 | affectations de propriétés non déclarées |
| Paramètres implicitement nullables (`Type $x = null`) | 8.4 | signatures |
| Arrondis / affichage des flottants (`serialize_precision`, `round`) | 7.1+ / 8.4 | `json_encode` de flottants, `round`, `number_format` |
| Dates (`createFromFormat` avec débordement, `DateTime` mutable) | toutes | `createFromFormat`, `modify` |
| Fonctions retirées (`each`, `create_function`, `money_format`…) | 7.2 → 8.0 | appels |
| Exceptions au lieu d'avertissements (`TypeError`, `ValueError`) | 8.0 | arithmétique sur chaînes, accès tableau sur scalaires |

Pour chaque occurrence : fichier/ligne, changement, impact observable (entrée → sortie avant / après), `BH-xxx` qui le
fige (lire `docs/migration/behaviors/`) ou **non couvert**.

## 3. Écrire

1. `docs/migration/compat/php-<v>.md` : synthèse, erreurs bloquantes, changements de sémantique (tableau ci-dessus
   instancié), occurrences non couvertes par un `BH` → lacunes `GAP-COMPAT-xx` pour `/test-plan --gaps`,
   corrections automatiques possibles.
2. Constats au format des dépréciations (source `runtime-php`/`phpstan`/`rector`, `removed_in: "php <v>"`,
   `blocking_for` = étape de montée vers `<v>`) dans `.claude/state/migration/deprecations/<step>-compat-<v>.json`,
   puis `.claude/bin/track-merge.py deprecations --step <step> --input <ce fichier>`.

## Rapport final

Bloquants, changements de sémantique (couverts / non couverts), lacunes de tests, chemins des rapports ; gate
`G-DEPREC` si des bloquants subsistent, et recommandation : écrire d'abord les tests manquants, puis corriger.
