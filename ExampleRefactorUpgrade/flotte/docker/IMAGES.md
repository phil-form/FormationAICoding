# Images de base épinglées

Environnement de **développement local** de la migration flotte (jamais en production). Toutes les images de base
sont épinglées par **digest** (index multi-architecture) : un tag qui bouge sur le registre ne change rien aux
constructions. Résolution : `docker buildx imagetools inspect <image>:<tag>`, le 2026-11-17.

| Image (tag) | Digest | Usage |
|---|---|---|
| `php:7.2-apache` | `sha256:4dc0f0115acf8c2f0df69295ae822e49f5ad5fe849725847f15aa0e5802b55f8` | app php72, app-ref (step-00 → step-02) |
| `php:7.4-apache` | `sha256:c9d7e608f73832673479770d66aacc8100011ec751d1905ff63fae3fe2e0ca6d` | app php74 (step-03, step-04) |
| `php:8.1-apache` | `sha256:72f0c0134922985022d55a39b0390d35dc7a10e961aa6c44fa35b4c3102f4268` | app php81 (step-05, step-06) |
| `php:8.4-apache` | `sha256:884f64b47174de2c0e813270fe7b24a18605729fcebe201302d98c38805530d0` | app php84 (step-07, cible) |
| `composer:2.2` | `sha256:3b28552911103b67d3bab8dd126f3f8a3e716f788e68418763cf3259ae8d84b2` | Composer 2.2 LTS de l'image php72 |
| `composer:2` | `sha256:0c8e663f14c94d07b3c45b175c51f5a14718508f21fe4dd7e54f09488dd0ed71` | Composer des images php74/81/84, tests, tools |
| `php:8.4-cli` | `sha256:d63e8b80cd4c6cfd01cfa7c910522e299cdc7ca1a2b6d6563db48df3c17e6c55` | conteneurs tests et tools |
| `docker:27-cli` | `sha256:851f91d241214e7c6db86513b270d58776379aacc5eb9c4a87e5b47115e3065c` | CLI Docker du conteneur tests (tests CLI) |
| `aquasec/trivy:0.58.1` | `sha256:ab70a02200597efa04748f210f793936eb647cbcdb0ea69cc30b226d6f5a22c7` | service trivy (scan d'images) |

## Mettre à jour un digest

1. Résoudre le nouveau digest : `docker buildx imagetools inspect <image>:<tag> --format '{{json .Manifest.Digest}}'`.
2. Le reporter ici, dans `docker/app/render.sh` (images `app`, puis relancer le script), `docker/tests/Dockerfile`,
   `docker/tools/Dockerfile` ou `docker-compose.yml`.
3. `.claude/bin/env.sh build`, puis toute la suite de tests sur `app-ref` et `app` : un changement d'image de base est
   un changement d'environnement, à valider comme une marche (`/step-commit`, scan d'images par `/vuln-audit`).

Les outils installés dans l'image `tools` (Rector, PHPStan, PHP_CodeSniffer, Infection, phpcov) sont résolus à la
construction ; leurs versions exactes sont listées dans l'image (`/opt/tools/VERSIONS.txt`). osv-scanner est figé par
`OSV_SCANNER_VERSION`. PHPUnit des images `app` : phar `phpunit-<majeure>.phar` (8.5, 9.6, 11).
