---
name: implement-feature
description: Implémente le code de production d'une feature à partir de son analyse préparatoire, via l'agent isolé code-writer qui n'a aucun accès aux tests.
when_to_use: Après analyse-feature (et write-tests en TDD) ; ou pour corriger le code après un run-tests en échec, à partir du retour anonymisé.
argument-hint: <docs/analyse/features/NNN-slug.md> [--fix]
context: fork
agent: code-writer
background: false
---

# Implémentation d'une feature depuis l'analyse

Arguments : `$ARGUMENTS`

1. Lis l'analyse cible en entier, puis `CLAUDE.md` et `tmp/01-STRUCTURE.md`.
2. N'implémente rien et rends la liste des points bloquants si :
   - l'analyse est au statut « brouillon » ou contient des questions ouvertes **bloquantes** ;
   - la revue `docs/analyse/reviews/NNN-slug.review.md` est absente ou a pour verdict « À corriger »
     (proposer `/review-analyse`) ;
   - en `--fix`, un critère listé est gelé dans `docs/analyse/reviews/NNN-slug.triage.md`.
3. Lis l'existant concerné dans `src/main/` (socle `framework/`, entités liées, dernière migration Flyway).
4. **Mode `--fix`** : lis `.claude/state/feedback-code.md` (produit par `/triage-failures` ou `/trace-check`).
   S'il est absent, propose de lancer `/triage-failures` d'abord ; s'il contient « Aucun retour. », arrête-toi.
   Pour chaque point, relis le critère dans l'analyse et corrige le code pour s'y conformer. Ne cherche pas à
   deviner le contenu des tests.
5. Implémente dans l'ordre : migration Flyway → entité → repository → DTO → mapper → service (interface + impl)
   → exceptions spécifiques → controller.
6. Compile : `./mvnw -q -Dmaven.test.skip=true compile`, corrige jusqu'à compilation propre.
7. Rends ton rapport : fichiers, traçabilité `RG/AC/SEC → code`, choix sur les ambiguïtés, résultat de compilation.
