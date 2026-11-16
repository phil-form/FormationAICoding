---
name: triage-failures
description: Arbitre les tests en échec d'une feature - pour chaque échec, détermine si le code, le test, l'analyse ou l'infrastructure est en cause, puis renvoie un retour formulé en termes d'analyse au bon agent (code-writer, test-writer ou analyste) sans jamais faire fuiter le code d'un côté vers l'autre.
when_to_use: Après un run-tests en échec, avant toute correction (implement-feature --fix ou write-tests --fix).
argument-hint: <docs/analyse/features/NNN-slug.md>
context: fork
agent: auditor
background: false
---

# Triage des tests en échec

Analyse de la feature : `$ARGUMENTS`

## Entrées

- `.claude/state/test-feedback.md` (critères en échec + message) et `.claude/state/test-run.log` (log complet).
- L'analyse de la feature et sa dernière revue (`docs/analyse/reviews/NNN-slug.review.md`).
- Les tests de la feature (`@Tag("feature-NNN")` dans `src/test/`) et le code concerné dans `src/main/`.

Si `test-feedback.md` est absent ou plus ancien que la dernière modification de `src/`, demande de relancer
`/run-tests` d'abord.

## Pour chaque test en échec

1. Relis le critère (`AC-xx` / `AC-SEC-xx`), sa règle (`RG-xx`) et le contrat technique concerné : c'est la **référence**.
2. Lis le test : son entrée, son attente, ses mocks / données. Lis le code exécuté.
3. Si nécessaire, rejoue le test seul (commandes dans tes instructions d'agent) pour obtenir la trace complète.
4. Rends un verdict — **un seul** par échec :

| Verdict | Quand | Destination |
|---|---|---|
| `CODE` | Le test est conforme à l'analyse, le code ne l'est pas | `feedback-code.md` |
| `TEST` | Le test exige autre chose que l'analyse (attente, donnée, setup, mock incohérent avec le contrat) | `feedback-tests.md` |
| `ANALYSE` | L'analyse est ambiguë, incomplète ou contradictoire : les deux lectures sont défendables | `docs/analyse/reviews/NNN-slug.triage.md` |
| `INFRA` | Environnement : Docker, Testcontainers, port, réseau, dépendance, configuration de test | rapport humain |

En cas de doute entre `CODE` et `TEST`, le verdict est `ANALYSE` : ne jamais trancher une ambiguïté à la place
de l'analyste.

## Sorties

1. **`.claude/state/audit/triage-<AAAA-MM-JJ-HHMM>.md`** — rapport complet pour l'humain : tableau
   `test → verdict → justification`, extraits de code/test utiles, commandes rejouées.
2. **`.claude/state/feedback-code.md`** — remplacé à chaque triage ; uniquement les verdicts `CODE` :
   ```markdown
   # Retour pour code-writer — <date> — NNN-slug
   - **AC-03** (§4, §5.7) — attendu : … Observé : …
   ```
3. **`.claude/state/feedback-tests.md`** — remplacé à chaque triage ; uniquement les verdicts `TEST`, même format :
   ce que l'analyse dit vraiment et en quoi l'attente actuelle s'en écarte, sans rien révéler du code de production.
4. **`docs/analyse/reviews/NNN-slug.triage.md`** — verdicts `ANALYSE` : question précise, les deux lectures
   possibles, la décision attendue. Ces critères sont gelés jusqu'à mise à jour de l'analyse.
5. Un fichier sans entrée contient « Aucun retour. » (pour que l'agent concerné ne relise pas un ancien retour).

**Avant de terminer**, relis `feedback-code.md` et `feedback-tests.md` en appliquant la règle d'étanchéité de
tes instructions d'agent.

## Réponse finale

Tableau `AC → verdict`, puis les prochaines commandes dans l'ordre :
`/analyse-feature …` (si ANALYSE), `/write-tests <analyse> --fix` (si TEST), `/implement-feature <analyse> --fix`
(si CODE), puis `/run-tests`.
