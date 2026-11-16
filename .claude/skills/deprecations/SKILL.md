---
name: deprecations
description: Après les tests, analyse les logs (run de tests, compilation -Xlint:deprecation,removal, plugins Maven, JVM, Spring, Hibernate, Mockito, bibliothèques) pour inventorier les dépréciations, puis produit un plan de correction priorisé dans docs/audit/ — sans modifier le code ; l'utilisateur décide de la suite.
when_to_use: Après /run-tests (ou ./start.sh --test), avant une montée de version (Java, Spring Boot, dépendances), ou quand l'utilisateur demande de traquer les dépréciations / avertissements.
argument-hint: "[--log <fichier>] [--no-compile]"
allowed-tools: Bash(.claude/skills/deprecations/scripts/collect.sh:*), Bash(.claude/skills/run-tests/scripts/run-tests.sh:*)
---

# Dépréciations : inventaire et plan de correction

Arguments : `$ARGUMENTS` (transmis au script de collecte).

## Règles

- **Aucune modification** de code, de dépendance ni de configuration pendant cette skill : inventaire → analyse →
  plan → **décision de l'utilisateur**.
- Une dépréciation est une **panne future** : priorité donnée à celles marquées « for removal » / « will be removed »
  et à celles qui bloqueront la prochaine montée de version (JDK, Spring Boot, Hibernate).

## 1. Disposer d'un log de tests récent

Le log de référence est `.claude/state/test-run.log` (produit par `/run-tests`). S'il est absent ou plus ancien que la
dernière modification de `src/` ou `pom.xml`, lancer d'abord (en arrière-plan, attendre la fin) :

```bash
.claude/skills/run-tests/scripts/run-tests.sh --docker
```

## 2. Collecter

```bash
.claude/skills/deprecations/scripts/collect.sh $ARGUMENTS
```

- Recompile une **copie** du projet avec `-Xlint:deprecation,removal` (main + tests) : messages précis
  (fichier, API dépréciée) sans toucher à `target/`.
- Extrait du log de tests : JVM (`sun.misc.Unsafe`, agents chargés dynamiquement, accès natif), Mockito, Spring
  (propriétés), Hibernate (`HHH9000…`), bibliothèques, plugins Maven.
- Code retour : 0 rien · 10 dépréciations · 2 erreur (log absent, compilation impossible → l'expliquer).
- Inventaire : `.claude/state/audit/deprecations/latest/findings.md` (+ `findings.tsv`).

## 3. Analyser chaque dépréciation

Pour chaque ligne de l'inventaire :

1. **Origine** : notre code (fichier:ligne), configuration (`application*.yml`, `pom.xml`, Dockerfile), dépendance
   (laquelle, transitive ou directe — `./mvnw dependency:tree` si besoin), JVM / outillage de test.
2. **Échéance** : « for removal » ? version qui supprimera l'API (Javadoc `@Deprecated(since, forRemoval)`, notes de
   version du projet amont — vérifier en ligne plutôt que de mémoire) ; impact sur la prochaine montée de version.
3. **Remplacement** : API / propriété / option de substitution **documentée** (lien vers la source), ou montée de
   version d'une dépendance qui corrige l'avertissement. Lire le code concerné avant de proposer.
4. **Effort et risque** : S / M / L ; risque de régression ; tests qui couvrent la zone.
5. **Vérification** : comment prouver la correction (avertissement absent du prochain `collect.sh`, tests verts).

Regrouper les dépréciations de même cause (ex. plusieurs messages JVM provoqués par la même bibliothèque).

## 4. Rédiger le plan

Écrire `docs/audit/<AAAA-MM-JJ>-plan-deprecations.md` :

```markdown
# Plan de correction — dépréciations du <date>
Source : `.claude/state/audit/deprecations/<horodatage>/findings.md` · <n> dépréciations distinctes
**Aucune correction appliquée.**

## Synthèse
| # | Dépréciation | Origine | Échéance | Correction proposée | Effort | Priorité |

## Détail par dépréciation
### 1. <titre>
- Constat (message exact, fichier / dépendance, occurrences)
- Échéance et impact
- Correction proposée (+ référence)
- Vérification

## Ordre d'exécution proposé
## Décisions attendues (le cas échéant : alternatives entre lesquelles choisir)
```

## 5. Décision de l'utilisateur

Présenter la synthèse (tableau court) et demander la suite avec l'outil de question :

- **Exécuter le plan** (en entier ou les priorités hautes) — puis relancer `/run-tests` et cette skill pour vérifier ;
- **Approfondir** une dépréciation (alternatives, risque) ;
- **Reporter** (le plan reste dans `docs/audit/`, à reprendre avant la prochaine montée de version) ;
- **Trancher** les décisions ouvertes du plan.

## Réponse finale

Nombre de dépréciations par origine, chemin du plan, décisions attendues.
