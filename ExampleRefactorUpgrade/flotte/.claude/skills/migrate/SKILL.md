---
name: migrate
description: Orchestrateur maître de la migration PHP 7.2 / Symfony 2.8 vers PHP 8.4 / Symfony 6.4 - baseline, construction de la suite de tests, puis alternance de /upgrade-symfony et /upgrade-php selon steps.json, et bilan final.
when_to_use: Uniquement sur demande explicite de l'utilisateur, pour dérouler (ou reprendre) toute la migration.
argument-hint: "[--auto|--interactive] [--until step-NN] [--resume] [--dry-run]"
disable-model-invocation: true
allowed-tools: Bash(.claude/bin/state.py:*), Bash(.claude/bin/env.sh:*), Bash(git status:*), Bash(git log:*), Bash(git tag:*)
---

# Orchestrateur maître : migration complète

Arguments : `$ARGUMENTS`. Mode par défaut : `--interactive`.

Références : `conventions.md`, `steps.json`, plan (§ 1.1, § 9 ordre d'exécution).

## Règles

- Session principale ; délègue tout via l'outil **Skill**. Ne réalise elle-même aucune modification de code.
- Les gates `G-*` des sous-orchestrateurs remontent ici et arrêtent la migration jusqu'à décision de l'utilisateur.
- Recommandation : `--interactive` pour les deux premières marches, puis proposer `--auto` à l'utilisateur.

## Déroulé

1. **État** — `state.py get` ; `--resume` reprend au point enregistré (y compris dans un sous-orchestrateur, en le
   relançant avec `--resume`). Sinon `state.py set orchestrator migrate`.
2. **Baseline** — si le tag `flotte-migration/step-00` n'existe pas (`git tag -l`) : skill `baseline`.
3. **Suite de tests** — si `state.py get suite.status` ≠ `ready` : skill `build-test-suite` (`--until-thresholds`).
   Seuils non atteints → demander à l'utilisateur s'il accepte de continuer.
4. **Plan** — `--dry-run` : invoquer `upgrade-symfony --dry-run` puis afficher la séquence complète issue de
   `steps.json` (marches symfony et php entrelacées) et s'arrêter.
5. **Migration** — parcourir les étapes restantes de `steps.json` dans l'ordre (jusqu'à `--until`) :
   - `kind: symfony` → skill `upgrade-symfony` (`<symfony> --until <step> <mode>`) ;
   - `kind: php` → skill `upgrade-php` (`<php> --until <step> <mode>`) ;
   - après chaque marche validée, vérifier le tag/commit produit par `step-commit`.
   - Après step-02 (première marche en `--interactive` réussie), proposer de passer en `--auto`.
6. **Bilan final** — skill `step-report` (`final`) : changements de comportement acceptés
   (`docs/migration/accepted-changes.md`), bilan sécurité step-00 → dernière étape
   (`docs/security/vulnerabilities.json`), dépréciations restantes, préparation de la suite (Symfony 7.4 LTS).

## Rapport final

Étapes réalisées, commits et tags, gates et décisions, état de la cible (atteinte ou non), commande de reprise.
