---
name: mutation-test
description: Mesure la qualité réelle des tests par mutation testing (PIT) - introduit des défauts dans le code et vérifie que les tests les détectent. Score par classe, mutants survivants traduits en lacunes de test exprimées en termes d'analyse pour l'agent test-writer.
when_to_use: Après qu'une feature passe ses tests ; avant une release ; quand on doute de la solidité de tests écrits à l'aveugle.
argument-hint: "[<docs/analyse/features/NNN-slug.md> | <package ou classe>] [--threshold 70]"
context: fork
agent: auditor
background: false
---

# Mutation testing (PIT)

Arguments : `$ARGUMENTS`

## 1. Prérequis (pom.xml)

Vérifier dans `pom.xml` la présence du plugin `org.pitest:pitest-maven` avec la dépendance de plugin
`org.pitest:pitest-junit5-plugin` (dernières versions stables). S'ils manquent, **ne pas modifier le pom**
(tu n'as pas le droit d'écrire dans `src`/`pom.xml`) : rendre le bloc XML à ajouter et s'arrêter. Configuration attendue :

```xml
<configuration>
  <targetClasses><param>be.bstorm.formation.*</param></targetClasses>
  <excludedClasses>
    <param>be.bstorm.formation.*Application</param>
    <param>be.bstorm.formation.dto.*</param>
    <param>be.bstorm.formation.entity.*</param>
    <param>be.bstorm.formation.framework.config.*</param>
    <param>be.bstorm.formation.mapper.*Impl</param> <!-- code généré par MapStruct -->
  </excludedClasses>
  <targetTests><param>be.bstorm.formation.*Test</param></targetTests> <!-- unitaires : les *IT sont trop lents -->
  <outputFormats><param>XML</param><param>HTML</param></outputFormats>
  <timestampedReports>false</timestampedReports>
  <withHistory>true</withHistory> <!-- analyse incrémentale -->
  <threads>4</threads>
</configuration>
```

## 2. Exécution

- Feature (`NNN-slug.md`) : cibler les classes de production de la feature (lues dans le contrat technique §5) :
  `./mvnw -B test-compile org.pitest:pitest-maven:mutationCoverage -DtargetClasses=<liste séparée par des virgules>`
- Package / classe : `-DtargetClasses=<valeur>`
- Rien : tout le périmètre configuré.

Puis : `.claude/skills/mutation-test/scripts/pit-summary.py target/pit-reports/mutations.xml --threshold <seuil>`
(seuil par défaut 70).

## 3. Interprétation

Pour chaque mutant **SURVIVED** ou **NO_COVERAGE** :
1. Lire le code muté et identifier la **règle / le critère de l'analyse** concerné (ex. condition niée ligne 27 de
   `create` → vérification d'unicité → `RG-02` / `AC-03`).
2. Classer :
   - **Lacune de test** — un critère de l'analyse aurait dû détecter le défaut → retour pour test-writer ;
   - **Mutant équivalent** — le changement ne modifie pas le comportement observable → ignorer, le justifier ;
   - **Code hors analyse** — logique qu'aucun critère ne décrit → soit l'analyse est incomplète
     (`docs/analyse/reviews/NNN-slug.triage.md`), soit c'est du code non demandé.

## 4. Sorties

- `docs/audit/mutation-<AAAA-MM-JJ>[-NNN].md` : synthèse du script + classification de chaque mutant non détecté.
- `.claude/state/feedback-tests.md` (règle d'étanchéité : **aucun** détail de code, de ligne ni de mutation) :
  > **AC-03** — aucun test ne vérifie qu'un nom existant avec une casse différente est refusé (RG-02).

## Réponse finale

Score global et par classe vs seuil, nombre de lacunes / équivalents / hors analyse, prochaine commande
(`/write-tests <analyse> --fix`).
