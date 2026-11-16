---
name: run-tests
description: Lance la suite de tests (unitaires et/ou intégration, local ou conteneur docker/test) avec suivi d'avancement en direct - barre de progression, % fait/restant, ETA, total réussis/ratés/ignorés - dans la status line Claude Code et dans le terminal. Produit un retour anonymisé par critère d'acceptation pour l'agent code-writer.
when_to_use: Pour exécuter les tests et suivre leur avancement, après implement-feature ou avant un commit.
argument-hint: "[--unit|--it] [--docker] [-- <args maven>]"
allowed-tools: Bash(.claude/skills/run-tests/scripts/run-tests.sh:*)
---

# Lancement des tests avec suivi d'avancement

Arguments : `$ARGUMENTS`

## Exécution

Lance, **en arrière-plan** (`run_in_background: true`) pour que l'utilisateur suive la progression dans la status line :

```bash
.claude/skills/run-tests/scripts/run-tests.sh $ARGUMENTS
```

- `--unit` : tests unitaires (`./mvnw test`) · `--it` : intégration seule · défaut : tout (`./mvnw verify`)
- `--docker` : exécution dans le conteneur `docker/test` (`docker compose --profile test run api-test`)

Pendant l'exécution :
- la **status line** affiche `🧪 ████████░░░░ 62% 13/21 · ✔ 87 ✘ 2 ↷ 1 · ETA 40s` (rafraîchie toutes les 2 s,
  source `.claude/state/tests.json`) ;
- dans un terminal, le script affiche sa propre barre ; hors TTY il imprime une ligne par classe terminée
  (`[ 62%] 13/21 CourseServiceTest — tests:8 ok:7 ko:1 skip:0`).

Ne sonde pas la sortie en boucle : attends la notification de fin du processus.

## À la fin

Rends un résumé court :
- statut (succès / échecs / erreur de build), durée, totaux réussis / ratés / ignorés ;
- tests en échec groupés par critère d'acceptation (`AC-xx`, lisibles dans `.claude/state/test-feedback.md`) ;
- en cas d'erreur de build : les premières lignes `[ERROR]` pertinentes (log : `.claude/state/test-run.log`).

Si des critères échouent, propose `/triage-failures <analyse>` : l'auditeur déterminera pour chaque échec si le
code, le test ou l'analyse est en cause et préparera le retour pour le bon agent. Si le build échoue avant les tests
(compilation, Docker, Testcontainers), propose `/analyse-logs tests`.

Exécution ciblée d'une feature : `run-tests.sh -- -Dgroups=feature-NNN`.
