# Stages de la pipeline

```
Checkout → Build → Tests → SAST ─┐
                    └→ SCA ──────┴→ Image (build + scan) → DAST (stack éphémère) → Publish → Deploy staging → [Approbation] → Deploy prod
```

## Options globales

```groovy
options { timestamps(); timeout(time: 60, unit: 'MINUTES'); disableConcurrentBuilds(); buildDiscarder(logRotator(numToKeepStr: '20')) }
environment { IMAGE = "formation-api"; TAG = "${env.GIT_COMMIT.take(8)}" }
```

Agent : nœud avec Docker. Les étapes Maven tournent dans `maven:3.9-eclipse-temurin-25` (agent docker avec
cache `~/.m2` monté) ; Testcontainers a besoin du socket Docker (`-v /var/run/docker.sock:/var/run/docker.sock`).

## 1. Checkout

`checkout scm`. Récupérer `GIT_COMMIT` court pour le tag d'image.

## 2. Build

`./mvnw -B -DskipTests package` — archive le jar.

## 3. Tests

`./mvnw -B verify` (Surefire + Failsafe/Testcontainers + JaCoCo).
Publication : `junit '**/target/*-reports/*.xml'`, couverture via plugin Coverage (`recordCoverage`).
Le stage génère aussi la spec OpenAPI (`springdoc-openapi-maven-plugin` pendant `integration-test`) →
`target/openapi.json`, archivée et réutilisée par la DAST.

### 3b. Mutation testing (build nocturne ou `main` uniquement)

`./mvnw -B org.pitest:pitest-maven:mutationCoverage` (config du pom, voir skill mutation-test), rapport HTML
publié, build `UNSTABLE` sous le seuil (`mutationThreshold`, 70 % par défaut). Trop lent pour chaque commit :
`when { anyOf { branch 'main'; triggeredBy 'TimerTrigger' } }`.

## 4. SAST (en parallèle avec SCA)

- **SpotBugs + FindSecBugs** via Maven (`spotbugs:check`), seuil sur les catégories `SECURITY`.
- **Semgrep** en conteneur (version épinglée), règles `p/java`, `p/owasp-top-ten`, sortie SARIF archivée ;
  échec si findings de sévérité `ERROR`.
- **SonarQube** si un serveur est disponible (`withSonarQubeEnv` + `waitForQualityGate abortPipeline: true`) —
  optionnel, à confirmer avec l'utilisateur.
- Publication des résultats : plugin Warnings NG (`recordIssues`) pour SpotBugs et SARIF.

## 5. SCA (en parallèle avec SAST)

- OWASP Dependency-Check (`-DfailBuildOnCVSS=7`, clé NVD via credential `nvd-api-key`,
  suppressions `ci/dependency-check-suppressions.xml`, cache de la base NVD persistant entre builds).
- Rapport HTML publié (`publishHTML`).

## 6. Image

- `docker build -f docker/prod/Dockerfile -t $IMAGE:$TAG .`
- `trivy image --exit-code 1 --severity HIGH,CRITICAL --ignore-unfixed $IMAGE:$TAG` (Trivy en conteneur épinglé),
  rapport JSON + table archivés.

## 7. DAST — OWASP ZAP sur stack éphémère

- Lancer une stack isolée : `docker compose -p ci-$BUILD_NUMBER --profile prod up -d` avec l'image fraîchement
  construite et une `.env` de CI (credential « secret file » `formation-env-ci`), DB vierge (Flyway migre au démarrage).
- Attendre `/actuator/health` = `UP` (timeout).
- `zap-api-scan.py` (image `zaproxy/zap-stable` épinglée) dans le réseau de la stack, cible = `target/openapi.json`
  (format `openapi`), règles `ci/zap/rules.tsv`, rapports HTML/JSON archivés.
- Si l'API exige une authentification : obtenir un token de test via un compte seedé pour la CI et le passer à ZAP
  (`-z "-config replacer..."`), documenté dans `docs/ci/JENKINS.md`.
- Échec du build sur toute règle en `FAIL`.
- `post { always { sh 'docker compose -p ci-$BUILD_NUMBER down -v' } }`.

## 8. Publish

`docker save $IMAGE:$TAG | gzip > formation-api-$TAG.tar.gz` (ou push vers un registry privé si disponible —
dans ce cas, scp ne transfère que les fichiers de déploiement).

## 9. Deploy (staging puis prod)

Uniquement sur `main` (`when { branch 'main' }`). Prod précédée de `input message: 'Déployer en production ?'`
(avec `submitter` restreint).

```groovy
sshagent(credentials: ['deploy-ssh-staging']) {
  withCredentials([file(credentialsId: 'known-hosts', variable: 'KNOWN_HOSTS'),
                   file(credentialsId: 'formation-env-staging', variable: 'ENV_FILE')]) {
    sh '''
      set +x
      SSH_OPTS="-o UserKnownHostsFile=$KNOWN_HOSTS -o StrictHostKeyChecking=yes"
      scp $SSH_OPTS formation-api-$TAG.tar.gz docker-compose.yml start.sh ci/deploy/remote-deploy.sh \
          $DEPLOY_USER@$DEPLOY_HOST:$DEPLOY_DIR/releases/$TAG/
      scp $SSH_OPTS "$ENV_FILE" $DEPLOY_USER@$DEPLOY_HOST:$DEPLOY_DIR/releases/$TAG/.env
      ssh $SSH_OPTS $DEPLOY_USER@$DEPLOY_HOST "bash $DEPLOY_DIR/releases/$TAG/remote-deploy.sh $TAG"
    '''
  }
}
```

Les certificats TLS ne sont **jamais** transférés par la pipeline : ils sont gérés sur le serveur par certbot + deploy hook
(`docs/ops/DEPLOIEMENT-PROD.md`). HAProxy reste en place pendant le déploiement ; le healthcheck vérifie l'API
localement puis via HAProxy (`https://$API_DOMAIN/...`).

`ci/deploy/remote-deploy.sh <tag>` (sur le serveur) :
1. `docker load` de l'archive ;
2. backup de la base avant migration (`pg_dump -Fc` → `init_db/` du serveur, rotation des N derniers) ;
3. bascule du lien `current` → `releases/<tag>`, `./start.sh --prod` ;
4. healthcheck (timeout) ; en cas d'échec : retour au tag précédent (`current` → release précédente) et code de sortie ≠ 0 ;
5. purge des anciennes releases/images (garder les 3 dernières).

## post

- `always` : archiver les rapports, nettoyer le workspace (`cleanWs`).
- `failure` / `fixed` : notification (mail / Slack / Teams selon ce qui est disponible — à demander).

## Credentials attendus (à documenter dans docs/ci/JENKINS.md)

| Id | Type | Usage |
|---|---|---|
| `nvd-api-key` | Secret text | Dependency-Check |
| `formation-env-ci` | Secret file | `.env` de la stack DAST |
| `formation-env-staging` / `formation-env-prod` | Secret file | `.env` déployé |
| `deploy-ssh-staging` / `deploy-ssh-prod` | SSH Username with private key | scp / ssh |
| `known-hosts` | Secret file | Empreintes des serveurs |
| `sonar-token` (optionnel) | Secret text | SonarQube |

## Plugins Jenkins requis

Pipeline, Docker Pipeline, SSH Agent, Credentials Binding, JUnit, Coverage, Warnings NG, HTML Publisher,
Workspace Cleanup, Timestamper, (SonarQube Scanner).
