---
name: trace-check
description: Contrôle de traçabilité d'une feature - chaque règle et critère d'acceptation de l'analyse a-t-il des tests qui le vérifient réellement et du code qui l'implémente ? Détecte critères non testés, tests orphelins, tests qui ne vérifient pas ce qu'ils annoncent, règles non implémentées.
when_to_use: Après write-tests et implement-feature, avant de considérer une feature terminée ou avant une revue de code.
argument-hint: <docs/analyse/features/NNN-slug.md>
context: fork
agent: auditor
background: false
---

# Traçabilité analyse ↔ tests ↔ code

Analyse : `$ARGUMENTS`

## 1. Matrice mécanique

```bash
.claude/skills/trace-check/scripts/trace.sh $ARGUMENTS
```

Le script relie les identifiants de l'analyse (`**AC-xx**`, `**AC-SEC-xx**`, `**RG-xx**`, `**SEC-xx**`) aux méthodes
de test `acxx_…` / `acsecxx_…` / `rgxx_…` des classes `@Tag("feature-NNN")`, et au dernier run. Code retour 1 si un
critère d'acceptation n'a aucun test.

## 2. Contrôle sémantique (ce que le script ne voit pas)

Pour chaque ligne de la matrice :

- **Le test vérifie-t-il vraiment le critère ?** Lire le test : l'attente correspond-elle au *Then* de l'AC (code HTTP
  **et** corps **et** état en base si l'AC le dit) ? Repérer : assertions absentes ou trop faibles (`isNotNull()`
  seul), test qui ne vérifie que le mock, nom qui annonce un critère que le test ne vérifie pas.
- **Le code implémente-t-il la règle ?** Pour chaque `RG-xx` et `SEC-xx`, localiser l'implémentation
  (`classe#méthode`) ; signaler les règles introuvables.
- **Code non demandé** : comportement présent dans `src/main/` pour cette feature mais absent de l'analyse
  (endpoint, champ, règle) — dérive à remonter.

## 3. Rapport

`.claude/state/audit/trace-NNN-<AAAA-MM-JJ>.md` :

```markdown
# Traçabilité — NNN-slug — <date>
| ID | Tests | Vérifie vraiment ? | Implémentation | Dernier run | Statut |
|---|---|---|---|---|---|
| AC-01 | CourseServiceTest#ac01_… | ✔ | CourseServiceImpl#create | ✔ | OK |
| AC-04 | — | — | CourseController#list | — | ❌ non testé |

## Problèmes
- Critères non testés · tests faibles · règles non implémentées · tests orphelins · code non demandé
```

Si des tests manquent ou sont faibles, écrire aussi `.claude/state/feedback-tests.md` (règle d'étanchéité : en
termes d'analyse uniquement — « AC-04 n'est couvert par aucun test », « le test d'AC-02 ne vérifie pas l'état en
base exigé par l'AC »). Si des règles ne sont pas implémentées, écrire `.claude/state/feedback-code.md` de même.

## Réponse finale

Taux de couverture des critères (`AC` testés / total), liste des problèmes par type, prochaines commandes
(`/write-tests … --fix`, `/implement-feature … --fix`).
