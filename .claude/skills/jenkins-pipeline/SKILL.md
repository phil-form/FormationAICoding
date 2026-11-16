---
name: jenkins-pipeline
description: Met en place ou met à jour la pipeline Jenkins CI/CD du projet (Jenkinsfile déclaratif) - build, tests, SAST, SCA, scan d'image, DAST OWASP ZAP sur une stack éphémère, puis déploiement par scp et exécution distante par ssh.
when_to_use: Création initiale de la CI/CD, ajout d'une étape, changement de serveur de déploiement.
argument-hint: "[create|update] [<précisions>]"
disable-model-invocation: true
---

# Pipeline Jenkins CI/CD

Demande : `$ARGUMENTS`

Lis d'abord `CLAUDE.md`, `tmp/02-DOCKER.md`, `tmp/03-SCRIPTS.md`, et le `Jenkinsfile` existant s'il y en a un.
La structure cible et le détail de chaque étape sont dans [stages.md](stages.md).

## Fichiers produits

| Fichier | Rôle |
|---|---|
| `Jenkinsfile` | Pipeline déclarative |
| `ci/zap/rules.tsv` | Règles ZAP (IGNORE/WARN/FAIL), versionnées et commentées |
| `ci/deploy/remote-deploy.sh` | Script exécuté sur le serveur via ssh (chargement image, restart, healthcheck, rollback) |
| `ci/dependency-check-suppressions.xml` | Suppressions justifiées (partagé avec le skill audit-deps) |
| `docs/ci/JENKINS.md` | Prérequis Jenkins, credentials, plugins, schéma de la pipeline, procédure de rollback |

## Principes non négociables

- Images d'outillage **épinglées** (version exacte, idéalement digest), jamais `latest`.
- **Aucun secret dans le dépôt** : tout passe par les credentials Jenkins (`withCredentials`, `sshagent`).
  Les secrets ne sont jamais affichés (`set +x` autour des commandes sensibles).
- `known_hosts` du serveur fourni par credential (`StrictHostKeyChecking=yes`) — jamais
  `StrictHostKeyChecking=no`.
- Les étapes de sécurité **cassent le build** au-delà d'un seuil (quality gate / CVSS ≥ 7 / alerte ZAP FAIL),
  avec rapports archivés et publiés.
- Le déploiement en production requiert une **validation manuelle** (`input`) et ne se fait que depuis `main`.
- Toute ressource éphémère (stack DAST) est détruite dans `post { always { … } }`.

## Démarche

1. `create` : générer tous les fichiers ci-dessus. `update` : modifier uniquement ce qui est demandé, en
   conservant la structure existante.
2. Vérifier la syntaxe : si un Jenkins est joignable (`JENKINS_URL` + credentials utilisateur fournis), utiliser
   l'endpoint de validation déclarative (`/pipeline-model-converter/validate`) ; sinon le signaler.
3. Lister dans `docs/ci/JENKINS.md` les credentials à créer (id, type, usage) et les plugins requis.

## Réponse finale

Fichiers créés/modifiés, schéma des stages (une ligne chacun), credentials et plugins à configurer côté Jenkins.
