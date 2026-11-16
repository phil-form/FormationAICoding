---
name: write-tests
description: Écrit les tests unitaires et d'intégration d'une feature à partir de sa seule analyse préparatoire, via l'agent isolé test-writer (aucun accès au code de production).
when_to_use: Après analyse-feature et avant implement-feature (TDD), ou pour compléter la couverture d'une feature à partir de son analyse.
argument-hint: <docs/analyse/features/NNN-slug.md> [--fix]
context: fork
agent: test-writer
background: false
---

# Écriture des tests depuis l'analyse

Analyse cible : `$ARGUMENTS`

Si aucun fichier n'est donné, liste `docs/analyse/features/` et choisis la feature au statut « validée »
la plus récente ; si plusieurs sont possibles, arrête-toi et liste-les.

1. Lis l'analyse cible en entier, puis `CLAUDE.md` et `tmp/01-STRUCTURE.md`.
2. Lis `src/test/` pour réutiliser l'existant (`TestcontainersConfig`, fixtures, builders de données).
3. N'écris rien et rends la liste des points bloquants si :
   - l'analyse est au statut « brouillon » ou contient des questions ouvertes **bloquantes** ;
   - la revue `docs/analyse/reviews/NNN-slug.review.md` est absente ou a pour verdict « À corriger »
     (proposer `/review-analyse`) ;
   - un critère est gelé dans `docs/analyse/reviews/NNN-slug.triage.md` (ne pas toucher à ses tests).
4. **Mode `--fix`** : lis `.claude/state/feedback-tests.md` et corrige / complète **uniquement** les tests des
   critères listés, en revenant au texte de l'analyse. S'il contient « Aucun retour. », arrête-toi.
5. Sinon, écris les tests selon tes règles d'agent : un test minimum par `AC-xx` et `AC-SEC-xx`, nommage
   `acxx_...` / `acsecxx_...`, `@Tag("feature-NNN")` sur chaque classe, contrat technique respecté à la lettre.
6. Rends ton rapport : fichiers, matrice `AC → test`, lacunes de l'analyse.
