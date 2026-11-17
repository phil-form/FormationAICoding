---
name: mutation-check
description: Mesure la solidité réelle de la suite par mutation testing - Infection sur les tests unitaires, et mutations ciblées du code de l'application contrôlées par la suite boîte noire - puis traduit les mutants survivants en lacunes exprimées en comportements BH-xxx.
when_to_use: Après /coverage, quand la suite est verte sur step-00 ; dans la boucle de /build-test-suite jusqu'au seuil ; quand on doute qu'un test vérifie réellement ce qu'il annonce.
argument-hint: "[--unit|--blackbox|--all] [--target <classe ou dossier>] [--threshold <msi>]"
context: fork
agent: auditor
---

# Mutation testing

Arguments : `$ARGUMENTS`. Conventions : `.claude/reference/conventions.md` (§ 4, § 5, § 6). Plan : § 3.5.

## Règles

- Les mutations sont **temporaires** et faites uniquement par les outils (Infection, ou script de mutation de la
  skill) sur une copie de travail ou avec restauration garantie : à la fin, `git status` sur `src/` et `app/` doit
  être identique au début. Toute trace résiduelle est une anomalie bloquante à signaler immédiatement.
- Uniquement sur une suite **verte** (sinon arrêt : `/test-run` puis `/failure-triage`).
- Tu n'écris aucun test : les lacunes sont exprimées en `BH-xxx` et en conditions fonctionnelles.

## 1. Mutation des tests unitaires (`--unit`)

1. `.claude/bin/tools.sh infection --test-framework=phpunit` configuré pour exécuter `tests/unit/` (configuration
   `infection.json5` générée dans `.claude/state/migration/mutation/`), avec `--min-msi` = seuil, sortie JSON.
2. Cible par défaut : services et validateurs du domaine (`ReservationManager`, `PlateFormatter`,
   `BelgianPlateValidator`, extension Twig, entités).

## 2. Mutation boîte noire (`--blackbox`)

Infection ne sait pas piloter une suite HTTP externe : on procède par **mutations ciblées** :
1. Choisir les points chauds (classes à forte logique, lignes couvertes par la suite d'après `/coverage`).
2. Pour chaque mutant (opérateurs : comparaisons `<`/`<=`, `==`/`!=`, constantes, arrondis, retour de booléens,
   suppression d'appel, inversion de condition) : appliquer, vider le cache, lancer
   `.claude/bin/tests.sh --suite <suites concernées>`, noter tué / survivant, **restaurer**.
3. Limiter à un budget raisonnable (défaut 40 mutants) et prioriser la logique métier et les pièges du plan § 0.

## 3. Analyser les survivants

Pour chaque survivant : comportement non vérifié (en termes fonctionnels), `BH-xxx` qui aurait dû le détecter,
scénario de données ou cas limite manquant, ou mutant **équivalent** (justifier).

## 4. Écrire

`docs/migration/audit/mutation-<step>.md` : score (MSI) par classe et global, seuil, survivants regroupés,
lacunes `GAP-MUT-xx` (zone, condition, scénario, `BH` liés) consommables par `/test-plan --gaps`, mutants
équivalents. Détail brut dans `.claude/state/migration/mutation/`.

## Rapport final

MSI global / par classe, seuil atteint ou non, nombre de survivants utiles, confirmation que le code est restauré,
chemin du rapport, prochaine action.
