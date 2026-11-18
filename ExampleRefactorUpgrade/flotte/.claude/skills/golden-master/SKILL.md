---
name: golden-master
description: Enregistre (step-00, ou changement accepté explicitement) ou vérifie les sorties de référence des tests - pages HTML normalisées, JSON, sorties CLI, schéma et dumps de base - dans tests/golden/, via l'agent isolé test-writer.
when_to_use: --record dans /build-test-suite une fois la suite verte sur step-00 ; --verify à chaque marche (inclus dans /test-run) ; --accept BH-xxx uniquement après une décision consignée dans docs/migration/accepted-changes.md.
argument-hint: "--record | --verify | --accept BH-xxx[,BH-yyy]"
context: fork
agent: test-writer
---

# Golden masters

Arguments : `$ARGUMENTS`. Référence : `.claude/reference/conventions.md` § 2 (`tests/golden/`), § 6 (gel), § 7
(`G-FROZEN`, `G-BEHAVIOR`) ; plan § 3.

## Règles

- **`--record`** n'est permis que si **toutes** ces conditions sont vraies, sinon refuse et explique :
  - `docker/step.env` indique `STEP=step-00` ;
  - `.claude/state/migration/state.json` n'a pas `"frozen": true` ;
  - `tests/golden/` est vide **ou** l'utilisateur a demandé explicitement un ré-enregistrement complet de step-00.
- **`--accept BH-xxx`** : ré-enregistre **seulement** les golden des fiches listées, et seulement si
  `docs/migration/accepted-changes.md` contient pour chacune une entrée « acceptée » (BH, étape, raison, date,
  décideur) **et** que l'état n'est pas gelé. Sinon refuse (`G-FROZEN` / `G-BEHAVIOR`).
- **`--verify`** : lecture seule, jamais d'écriture dans `tests/golden/`.
- Un golden est toujours **normalisé** avant écriture (`Support/Normalizer.php`) ; jamais de jeton CSRF, de date
  réelle, de chemin absolu ni de donnée sensible.
- Le nom d'un golden porte la fiche : `tests/golden/<suite>/<zone>/<bh>[-<variante>].<ext>`.

## Étapes

1. Contrôle des conditions ci-dessus.
2. `--record` / `--accept` :
   1. `.claude/bin/seed.sh all --verify` (scénarios intègres) ;
   2. `GOLDEN_RECORD=1` + `.claude/bin/tests.sh --suite all` (ou `--group BH-xxx` pour chaque fiche acceptée) ;
   3. relance **sans** `GOLDEN_RECORD` : tout doit être vert (le golden enregistré est reproductible) ; deux
      enregistrements successifs doivent produire des fichiers identiques — sinon une normalisation manque,
      signale-le et n'accepte pas l'enregistrement ;
   4. écris `tests/golden/MANIFEST.md` : étape d'enregistrement, date figée, scénarios, nombre de fichiers par suite,
      et pour `--accept` la liste BH → décision.
3. `--verify` : `.claude/bin/tests.sh --suite all` ; liste les golden en écart (fichier, BH, résumé du diff) et les
   golden **orphelins** (aucun test ne les utilise).
4. Rends ton rapport.

## Rapport final

- Mode, fichiers enregistrés / vérifiés, écarts (BH, fichier), orphelins, non-reproductibilités détectées.

## Mise à jour du document de fiches (obligatoire après chaque implémentation)

Mettre à jour **`tmp/tests-application.md`** dans toutes ses parties, exactement comme le décrit la section
« Mise à jour du document de fiches » de `.claude/skills/write-bh-tests/SKILL.md` (conventions § 5.6) : lignes de
fiches, journal d'implémentation (colonne golden(s) renseignée), blocages, points à trancher, exemple complet, bloc
de suivi. Rapport final : fiches dont la ligne a été corrigée (avant → après) et blocages ajoutés.

## Après la skill

La session principale lance **`/step-commit`** (mode hors marche) pour les fichiers produits **et le document de fiches mis à jour** (règle « Commit automatique des tests » de
`CLAUDE.md` ; l'agent `test-writer` n'a pas accès à git). Signaler dans le rapport final la liste des fichiers à commiter.
