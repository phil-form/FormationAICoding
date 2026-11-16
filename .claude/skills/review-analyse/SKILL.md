---
name: review-analyse
description: Relecture critique d'une analyse préparatoire de feature (complétude du contrat technique, testabilité des critères, cohérence avec l'existant, ambiguïtés, sécurité) par un agent relecteur indépendant. Produit docs/analyse/reviews/NNN-slug.review.md avec un verdict.
when_to_use: Après analyse-feature et avant write-tests / implement-feature ; après toute modification d'une analyse.
argument-hint: <docs/analyse/features/NNN-slug.md>
context: fork
agent: analysis-reviewer
background: false
---

# Relecture d'une analyse préparatoire

Analyse cible : `$ARGUMENTS`

## Lectures

1. L'analyse cible, en entier.
2. Sa source client (`docs/analyse/client/...`, champ « Source » de l'en-tête).
3. Les autres analyses de `docs/analyse/features/` (entités, routes et identifiants partagés).
4. La revue précédente `docs/analyse/reviews/NNN-slug.review.md` et un éventuel `NNN-slug.triage.md` :
   vérifier que leurs points ont été traités.
5. `CLAUDE.md`, `tmp/01-STRUCTURE.md`, et dans `src/main/` les entités / migrations existantes concernées.

## Grille de contrôle

**Contrat technique (§5) — le plus important**
- Chaque endpoint : méthode, route exacte, DTO d'entrée, DTO de sortie, codes HTTP de succès **et** d'erreur.
- Chaque DTO : tous les champs, types Java exacts, contraintes de validation, nullabilité, format (dates, montants, énumérations).
- Chaque erreur : exception, code HTTP, `title` / `type` du `ProblemDetail`, structure des erreurs de validation.
- Service, repository, mapper : packages, noms et signatures complets ; exceptions levées par méthode.
- Pagination / tri : paramètres, valeurs par défaut, taille max, champs triables.
- Aucune contradiction avec `CLAUDE.md` (architecture par couche, `framework/`, Lombok, MapStruct, `/api/v1`).

**Critères d'acceptation (§4)**
- Chaque `RG-xx` couverte par au moins un `AC-xx` ; chaque `AC-xx` rattaché à une règle.
- Chaque `AC` observable depuis l'API : entrée précise → sortie précise (code, corps, état en base). Pas de
  « doit fonctionner correctement », « message d'erreur approprié ».
- Cas limites présents : vide, null, longueur max, doublon (casse, espaces), ressource absente, concurrence
  (`@Version`), droits insuffisants.
- Données d'exemple cohérentes avec les contraintes de validation.

**Cohérence**
- Noms d'entités, de tables, de routes alignés sur les analyses et le code existants.
- Modèle de données (§6) cohérent avec les entités (§5.3) : types, nullabilité, unicité, index.
- Identifiants stables, aucune numérotation en double.

**Sécurité**
- La détection « Sécurité : oui/non » est-elle juste (voir critères du skill analyse-feature) ?
- Si oui : `SEC-xx` présentes, chacune avec au moins un `AC-SEC-xx` testable ; contrôle d'accès par ressource
  (BOLA) traité dès qu'un id est dans une route.

**Questions ouvertes**
- Chaque hypothèse par défaut est explicite ; les questions qui bloquent un `AC` sont marquées bloquantes.

## Rapport

Écrire `docs/analyse/reviews/NNN-slug.review.md` (remplacer la version précédente, garder l'historique en bas) :

```markdown
# Revue — NNN-slug
Date : <AAAA-MM-JJ> · Analyse relue : <statut + date de dernière modification>
**Verdict : OK | À corriger** (À corriger dès qu'il reste un point Bloquant ou Majeur)

| # | Sévérité | Section | Problème | Correction proposée |
|---|---|---|---|---|

## Points des revues précédentes
| # | Statut (traité / non traité) |

## Historique
```

## Réponse finale

Verdict, nombre de points par sévérité, les bloquants en une ligne chacun. Si « À corriger », proposer
`/analyse-feature` pour mettre à jour l'analyse (elle traitera la revue), puis une nouvelle relecture.
