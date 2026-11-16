---
name: audit-deps
description: Audit de sécurité des dépendances (Maven, transitives incluses) et des images Docker du projet - CVE connues, sévérité, version corrigée, chemin d'introduction, recommandation de mise à jour. Produit un rapport dans docs/audit/.
when_to_use: Avant une release, après ajout/mise à jour d'une dépendance, ou périodiquement.
argument-hint: "[--images] [--fix]"
context: fork
agent: general-purpose
background: false
---

# Audit des failles dans les dépendances

Options : `$ARGUMENTS`
- `--images` : scanne aussi les images Docker (`postgres:18-alpine`, image construite depuis `docker/prod/Dockerfile`).
- `--fix` : propose les modifications du `pom.xml` (sans les appliquer sans confirmation explicite).

Aucun outil n'est à installer sur la machine : tout passe par Maven ou par Docker.

## 1. Inventaire

- `./mvnw -B -q dependency:tree -DoutputFile=target/audit/dependency-tree.txt` — pour savoir si une
  dépendance vulnérable est **directe** ou **transitive** (et par qui elle est tirée).
- Noter la version de Spring Boot : la plupart des versions transitives sont pilotées par son BOM.

## 2. Scans (au moins deux sources, pour recouper)

1. **OWASP Dependency-Check** (plugin Maven `org.owasp:dependency-check-maven`, dernière version) :
   `./mvnw -B org.owasp:dependency-check-maven:check -Dformats=JSON,HTML -DoutputDirectory=target/audit`
   — nécessite une clé NVD (`NVD_API_KEY` dans l'environnement → `-DnvdApiKey=$NVD_API_KEY`). Sans clé,
   le signaler et s'appuyer sur les autres sources.
2. **Trivy** en conteneur (version épinglée, pas `latest`, vérifier la dernière release officielle) :
   `docker run --rm -v "$PWD":/src -v trivy-cache:/root/.cache aquasec/trivy:<version> fs --scanners vuln --format json -o /src/target/audit/trivy-fs.json /src`
3. **OSV-Scanner** en conteneur si Trivy est indisponible : `ghcr.io/google/osv-scanner` sur `pom.xml`.
4. `--images` : `trivy image` sur chaque image (`--severity HIGH,CRITICAL --ignore-unfixed` pour la synthèse,
   rapport complet en JSON).

## 3. Analyse

Pour chaque vulnérabilité, dédupliquée entre sources (même CVE/GHSA + même composant) :
- composant et version, directe/transitive (chemin depuis `dependency-tree.txt`) ;
- identifiant (CVE / GHSA), sévérité CVSS, présence au catalogue CISA KEV si connue ;
- version corrigée minimale ;
- **exploitabilité dans ce projet** : la fonctionnalité vulnérable est-elle utilisée ? (justifier, sinon « à vérifier ») ;
- remède recommandé, par ordre de préférence :
  1. montée de version de Spring Boot (patch) si le BOM embarque le correctif ;
  2. surcharge de la propriété de version du BOM (`<xxx.version>` dans `<properties>`) ;
  3. mise à jour de la dépendance directe ;
  4. exclusion / remplacement ;
  5. suppression documentée (faux positif ou non exploitable) — dans un fichier de suppression
     Dependency-Check, avec justification et date de revue.

## 4. Rapport

Écrire `docs/audit/deps-<AAAA-MM-JJ>.md` :

```markdown
# Audit dépendances — <date>
Spring Boot <version> · Java <version> · outils : Dependency-Check <v>, Trivy <v>

## Synthèse
| Critique | Haute | Moyenne | Basse |
|---|---|---|---|

## Vulnérabilités à traiter (CRITICAL/HIGH)
| Composant | Version | Directe ? | CVE | CVSS | Corrigée en | Exploitable ? | Action |
|---|---|---|---|---|---|---|---|

## Autres vulnérabilités
## Images Docker (si --images)
## Faux positifs / suppressions
## Actions recommandées (ordonnées)
```

Les rapports bruts restent dans `target/audit/` (non versionné).

## 5. `--fix`

Présenter le diff proposé du `pom.xml` et **demander confirmation** avant de l'appliquer. Après application :
`./mvnw -B -q -DskipTests verify` pour vérifier que le projet compile, puis relancer le scan concerné.

## Réponse finale

Synthèse par sévérité, top 5 des actions, chemin du rapport.
