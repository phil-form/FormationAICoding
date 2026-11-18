---
name: upgrade-step
description: Réalise UNE seule marche de migration via l'agent migrator - montée Symfony (contraintes Composer, recettes Flex, sets Rector Symfony/Doctrine/Twig, configuration) ou montée PHP (image Docker de l'étape, config.platform.php, extensions, php.ini, sets Rector PHP) selon le kind de la marche dans steps.json - sans jamais toucher aux tests.
when_to_use: Appelée par /upgrade-symfony ou /upgrade-php à la phase « upgrade » d'une marche, une fois les dépréciations bloquantes éliminées (et après /compat-scan pour une marche PHP) ; ou en --fix après un tri d'échecs.
argument-hint: "<symfony|php> <version, ex. 3.4 ou 8.1> [--fix]"
context: fork
agent: migrator
---

# Montée d'une marche (Symfony ou PHP)

Arguments : `$ARGUMENTS` — **type** de marche (`symfony` ou `php`), **version** visée ; `--fix` = mode correction.

Références : `.claude/reference/conventions.md` (§ 2, § 3), `.claude/reference/steps.json` (notes de l'étape),
`tmp/plan-migration-php84-sf64.md` § 0 (pièges), `docs/migration/deprecations/step-NN.md`, `rector/README.md`.

## Choix de la marche

1. Lis `docker/step.env` (étape installée) et `steps.json`. La marche est l'**entrée qui suit immédiatement**
   l'étape installée.
2. Son `kind` doit être le type demandé et sa version (`symfony` ou `php`) la version demandée. Type omis : prendre
   celui de la marche suivante. Sinon (saut de version, mauvais type) : s'arrêter et le signaler.
3. **Lis** `.claude/skills/upgrade-step/kinds/<kind>.md` : règles propres, étapes et rapport de ce type de marche.

## Règles communes

- **Zones interdites** : `tests/`, `docs/`, `.claude/`, `.ref/`. Tu ne lis pas les sorties de tests brutes ; en
  correction, seul `.claude/state/migration/feedback-code.md` fait foi.
- **Une seule marche** à la fois, jamais de saut de LTS ni de palier PHP.
- **Comportement à préserver à l'identique**, bugs compris : ne corrige aucun bug, ne « modernise » rien qui ne soit
  nécessaire à la montée. Toute adaptation qui pourrait changer un résultat observable (ordre, format, arrondi,
  message, code HTTP) doit être signalée dans le rapport.
- Outils **uniquement** via les scripts : Composer `.claude/bin/composer.sh`, **Rector `.claude/bin/rector.sh
  <step-NN>`** (configurations `rector/`, liste blanche de règles de compatibilité, jamais de jeu de modernisation),
  PHPStan `.claude/bin/tools.sh`, console `.claude/bin/console.sh`. Jamais d'exécution des tests :
  l'orchestrateur les lance (et reconstruit l'environnement après une marche PHP).

## Mode `--fix`

1. Lire `.claude/state/migration/feedback-code.md` (retours en termes de `BH-xxx` et de dépréciations).
   S'il contient « Aucun retour. », s'arrêter.
2. Corriger **uniquement** les points listés, en relisant la fiche `BH` citée si elle t'est fournie dans le retour.
3. Passer directement au rapport (celui du fichier de type).
