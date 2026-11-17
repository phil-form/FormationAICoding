---
name: vuln-audit
description: Audit des vulnérabilités de l'étape courante (dépendances Composer transitives incluses, runtime PHP, image Docker) sans test d'exploitation - sévérité, version qui corrige, exposition évaluée par lecture du code - fusionné dans docs/security/vulnerabilities.json avec suivi des failles résolues et ajoutées d'une étape à l'autre.
when_to_use: À la baseline (step-00), avant et après chaque marche de migration (appelé par les orchestrateurs), avant /step-commit, ou à la demande.
argument-hint: "[--snapshot|--diff] [--no-images]"
context: fork
agent: security-auditor
---

# Audit des vulnérabilités et suivi par étape

Arguments : `$ARGUMENTS`. Conventions : `.claude/reference/conventions.md` (§ 4, § 5.4, § 7). Plan : § 4.

## Règles

- **Aucun test d'exploitation**, aucune requête d'attaque vers l'application : l'exposition se juge par **lecture du
  code** et de la configuration.
- Aucune correction (ni dépendance, ni code, ni image) : tu constates, l'orchestrateur ou l'utilisateur décide.
- `docs/security/vulnerabilities.json` n'est modifié **que** par `.claude/bin/track-merge.py vulnerabilities`.
- Le `composer.lock` est la source : s'il est absent, s'arrêter et renvoyer vers `/baseline`.

## 1. Collecter

1. Étape courante (`docker/step.env`), empreinte SHA-256 du `composer.lock`.
2. **Composer** : `.claude/bin/tools.sh composer audit --locked --format=json` (Composer récent du conteneur
   `tools`, indépendant du PHP de l'étape) — dépendances transitives incluses, paquets abandonnés notés.
3. **OSV** : `.claude/bin/tools.sh osv-scanner --lockfile=composer.lock --format=json` ; recouper avec Composer
   (même faille sous identifiants différents → `aliases`).
4. **Runtime PHP** : version exacte de PHP de l'image `app` (`.claude/bin/console.sh --version` ne suffit pas :
   relever `php -v` via l'image) ; CVE connues de cette version (avis de sécurité php.net, OSV écosystème
   `Packagist`/`PHP`), `kind: php-runtime`. PHP en fin de vie : une entrée explicite « runtime non maintenu ».
5. **Image** (sauf `--no-images`) : scan Trivy de l'image `app` (service `trivy`), `kind: os-image`, sévérités
   CRITICAL/HIGH au minimum.
6. **Configuration** : valeurs de test exposées (`secret` Symfony par défaut, encodeur `plaintext`, compte
   `gestionnaire`), `kind: config`, pour mémoire.

## 2. Qualifier chaque vulnérabilité

- `id` (GHSA de préférence), `aliases` (CVE), `package`, `affected_range`, `fixed_versions`, `severity`, `cvss`,
  `summary`, `url`.
- **Version qui corrige** sur la branche utilisée **et** première version LTS du chemin de migration qui la corrige
  (étape de `steps.json` où elle disparaîtra normalement).
- **Exposition** (`reachable`, `used-not-reachable`, `not-used`, `unknown`) avec justification : composant
  réellement utilisé ? fonctionnalité concernée activée (ex. `security-acl` non utilisé, `http_basic`, profiler
  absent en prod) ? chemin atteignable depuis une route ou une commande de l'inventaire ?

Constats écrits dans `.claude/state/migration/security/<step>-findings.json` (format du plan § 4.2, sans `status`).

## 3. Fusionner

`.claude/bin/track-merge.py vulnerabilities --step <step> --input .claude/state/migration/security/<step>-findings.json`
→ transitions de statut (conventions § 5.4), mise à jour de `steps[]` (versions, empreinte du lock, compteurs).

## 4. Rapport `docs/security/steps/<step>.md`

1. Synthèse : totaux par statut, sévérité et exposition.
2. `--diff` : **résolues**, **ajoutées** (`introduced`), **persistantes**, `accepted` arrivées à échéance.
3. Tableau trié par sévérité × exposition : faille, paquet, version installée, version qui corrige, étape où elle
   disparaîtra, exposition, recommandation.
4. Gate **`G-VULN`** si une faille est `introduced` ou si une acceptation est échue : l'indiquer clairement en tête.

## Rapport final

Compteurs (ouvertes, nouvelles, résolues, acceptées), gate levée ou non, failles critiques/hautes atteignables,
chemins du rapport et du JSON, résumé en 3 lignes réutilisable dans le message de `/step-commit`.
