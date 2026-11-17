---
name: fix-deprecations
description: Corrige les dépréciations par lots cohérents (une famille par lot) via l'agent migrator, à partir de l'inventaire produit par /deprecation-check, en priorité celles bloquantes pour la version Symfony ou PHP visée, sans changer le comportement observable.
when_to_use: Avant une montée de version (appelée par /upgrade-symfony ou /upgrade-php), quand /deprecation-check signale des dépréciations bloquantes ; ou en --fix après un tri d'échecs.
argument-hint: "[--target sf-X.Y] [--php X.Y] [--family <famille>] [--fix]"
context: fork
agent: migrator
---

# Correction des dépréciations

Arguments : `$ARGUMENTS`.

Sources (lecture) : `docs/migration/deprecations.json`, `docs/migration/deprecations/step-NN.md` (plan de lots),
`docs/migration/compat/php-X.Y.md` (si `--php`), `conventions.md`, plan § 0 et § 5.

## Règles

- **Zones interdites** : `tests/`, `docs/`, `.claude/`, `.ref/`. Tu ne modifies pas les rapports de dépréciations :
  c'est `/deprecation-check` qui constatera la résolution.
- **Un lot = une famille** (ex. « `security.context` → `security.authorization_checker` », « types de formulaire
  par FQCN », « `isValid()` sans `isSubmitted()` », « `{% for … if %}` », « annotations Doctrine → attributs »).
  Pas de lot mélangé. Lots de 1 à ~15 occurrences.
- Ordre : bloquantes pour `--target` / `--php` d'abord, puis par nombre d'occurrences décroissant.
- **Comportement identique** : la correction remplace l'API dépréciée par son équivalent sans changer le résultat.
  Exemple : `if ($form->isValid())` devient `if ($form->isSubmitted() && $form->isValid())` — c'est équivalent en
  2.8 (un formulaire non soumis n'est pas valide) ; à l'inverse, remplacer `!=` par `!==` changerait le résultat et
  est interdit.
- Correction automatique (champ `autofix` = `rector:…`) via `.claude/bin/tools.sh rector process` avec la règle
  précise ; sinon correction manuelle.
- Jamais d'exécution des tests : l'orchestrateur les lance après chaque lot.

## Mode `--fix`

Lire `.claude/state/migration/feedback-code.md` ; revenir sur le lot en cause ; corriger uniquement les points
listés ; rapport.

## Étapes

1. Lire le plan de lots de `deprecations/step-NN.md` ; filtrer par `--target` / `--php` / `--family`.
2. Choisir **un** lot (ou celui demandé). S'il n'y a plus de dépréciation bloquante : rapport « rien à corriger ».
3. Appliquer la correction (Rector ciblé ou manuelle), occurrence par occurrence, en relisant chaque endroit.
4. Vérifier : `.claude/bin/console.sh cache:clear`, `.claude/bin/tools.sh phpstan analyse` sur les fichiers modifiés,
   `.claude/bin/console.sh lint:twig` pour les templates (si disponible).
5. S'arrêter après **un** lot : l'orchestrateur teste, puis relance la skill pour le lot suivant.

## Rapport final (ta réponse)

- Lot traité : famille, identifiants `DEP-xxxx`, occurrences (fichier:ligne).
- Moyen : règle Rector ou manuel.
- Équivalence de comportement : justification par famille ; **risques** éventuels.
- Lots restants (bloquants / non bloquants).
