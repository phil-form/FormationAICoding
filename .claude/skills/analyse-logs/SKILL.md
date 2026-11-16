---
name: analyse-logs
description: Lit et analyse les logs de l'application (conteneurs Docker api-dev / api-prod / db, log du dernier run de tests, ou fichier de logs fourni) - erreurs regroupées par signature, chronologie, corrélation des requêtes, cause probable reliée au code, signaux de sécurité, actions recommandées. Lecture seule.
when_to_use: Erreur 500, comportement inattendu, conteneur qui redémarre, lenteur, run de tests en erreur d'infrastructure, investigation post-incident.
argument-hint: "[api-dev|api-prod|db|<fichier>] [--since 1h] [--grep <motif>] [<question>]"
context: fork
agent: log-analyst
background: false
---

# Analyse de logs

Demande : `$ARGUMENTS`

## Sources

| Argument | Source |
|---|---|
| (rien) | Services en cours (`docker compose ps`) : `api-*` et `db`, dernière heure |
| `api-dev` / `api-prod` / `db` | `docker compose logs --no-color --timestamps --since <durée> <service>` |
| `tests` | `.claude/state/test-run.log` (dernier run de `/run-tests`) |
| `<fichier>` | Fichier de logs fourni (texte, `.gz`, JSON par ligne) |

Défaut `--since 1h`. Les logs de **production** du serveur distant ne sont pas accessibles directement :
demander à l'utilisateur de fournir le fichier (export `docker compose logs` depuis le serveur, ou archive Jenkins).

Formats : en dev, logs texte Spring Boot ; en prod, logs JSON structurés → extraire avec `jq`
(`level`, `logger`, `message`, `error.*`, identifiants de trace).

## Démarche

Suivre la méthode de l'agent (délimiter → regrouper → chronologie → corréler → cause → sécurité). Si une question
est posée dans les arguments, centrer l'analyse sur elle.

Repères fréquents dans cette stack :
- `FlywayValidateException` / `Migration checksum mismatch` → migration modifiée après application (le hook
  flyway-guard aurait dû l'empêcher) ou dump restauré incompatible (`restore.sh`).
- `SchemaManagementException` (ddl-auto=validate) → entité et migration désalignées.
- `LazyInitializationException` → accès à une association LAZY hors transaction (open-in-view=false).
- `HikariPool ... Connection is not available` → pool épuisé : transaction longue, fuite, ou DB indisponible.
- `PSQLException: ... duplicate key` remontant en 500 → contrainte d'unicité non gérée en amont (devrait être 409).
- `Connection refused` vers `db` au démarrage → ordre de démarrage / healthcheck du service `db`.
- Testcontainers `Could not find a valid Docker environment` → socket Docker non monté (`docker/test`).

## Rapport (réponse finale, pas de fichier)

```markdown
## Synthèse
<2-4 lignes : ce qui se passe, depuis quand, impact>

## Erreurs par signature
| # | Signature | Occurrences | Première | Dernière | Service |

## Chronologie
## Cause probable
<explication + code concerné (src/main/...:ligne) + confiance + comment confirmer>
## Signaux de sécurité
## Actions recommandées
<ordonnées ; préciser la commande suivante : /triage-failures, /implement-feature --fix, /analyse-feature, restore.sh…>
```

Données sensibles masquées dans tout le rapport.
