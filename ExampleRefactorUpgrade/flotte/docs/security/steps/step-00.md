# Audit de sécurité — step-00 (baseline PHP 7.2 / Symfony 2.8)

> Date : 2026-11-17 · Mode : `--snapshot` (référence initiale, aucune comparaison possible)
> Lecture seule : aucun test d'exploitation, aucune requête vers `app` / `app-ref`, aucune modification de dépendance.
> Exposition jugée par lecture du code (`src/AppBundle`, `app/config`, `web/`, `docker/app`).

**Gate `G-VULN` : non levée.** Étape de référence : toutes les failles constatées entrent au statut `open`
(conventions § 5.4), aucune `introduced`, aucune acceptation existante.

## 1. Contexte de l'étape

| Élément | Valeur |
|---|---|
| Étape | `step-00` (`docker/step.env`) |
| PHP exécuté | **7.2.34** (`env.sh php-version`) ; `composer.json` fige `platform.php = 7.2.24` |
| Symfony / Twig | `symfony/symfony` 2.8.52, `twig/twig` 1.42.5 |
| `composer.lock` | SHA-256 `d3c96ce503d57454bddd90e2c3e5c9a502fd03ad96ddb2e1b86282ffdd57093e` (identique au tag `flotte-migration/step-00`) |
| Image | `flotte-mig/app:php72` (`php:7.2-apache@sha256:4dc0f011…`, **Debian 10.7**), partagée par `app` et `app-ref` |

### Sources

| Source | Résultat |
|---|---|
| `tools.sh composer audit --locked` (Composer récent) | 49 avis sur 2 paquets (33 `symfony/symfony`, 16 `twig/twig`) ; 5 paquets abandonnés |
| `tools.sh osv-scanner --lockfile=composer.lock` 1.9.2 | 36 avis GHSA (22 + 14), tous recoupés avec Composer ; 13 avis connus seulement de Composer (FriendsOfPHP, dont le lot du 2026-11-04) |
| Runtime PHP 7.2.34 | Fin de vie depuis le 30/11/2020 ; 9 CVE postérieures retenues (sélection ci-dessous) |
| `tools.sh trivy image` 0.58.1 (CRITICAL, HIGH) | ≈ 90 lignes CRITICAL (35 CVE distinctes × paquet source) et ≈ 650 lignes HIGH (dont ≈ 380 en-têtes noyau) |
| Configuration | 5 constats `CFG-001` à `CFG-005` |

Règles d'identifiant (à réappliquer à chaque marche pour garder des clés stables) : GHSA si un outil le donne,
sinon CVE, sinon identifiant interne (`PHP-RUNTIME-EOL`, `CFG-xxx`, `OS-EOL-debian10`, `TRIVY-HIGH-debian10-<source>`).
Les CVE HIGH de l'image sont **agrégées par paquet source** (alias = liste des CVE) ; les CRITICAL sont suivies une à une.
Le paquet porte la distribution (`debian10/…`) : un changement d'image de base produira de nouvelles clés.

## 2. Synthèse

**111 entrées, toutes `open`.** Nouvelles : 0 (baseline) · Corrigées : 0 · Acceptées : 0 · Réapparues : 0.

| Sévérité | composer | php-runtime | config | os-image | **Total** |
|---|---|---|---|---|---|
| critical | 1 | 2 | 0 | 35 | **38** |
| high | 10 | 1 | 2 | 12 | **25** |
| medium | 15 | 7 | 2 | 0 | **24** |
| low | 11 | 0 | 1 | 0 | **12** |
| non notée (`unknown`) | 12 | 0 | 0 | 0 | **12** |
| **Total** | 49 | 10 | 5 | 47 | **111** |

| Exposition | composer | php-runtime | config | os-image | **Total** |
|---|---|---|---|---|---|
| `reachable` | 2 | 2 | 3 | 3 | **10** |
| `used-not-reachable` | 14 | 0 | 1 | 17 | **32** |
| `not-used` | 33 | 8 | 0 | 27 | **68** |
| `unknown` | 0 | 0 | 1 | 0 | **1** |

Points saillants de la lecture du code :
- L'application n'utilise **ni** sandbox Twig, ni serializer, ni mailer/Mime, ni cache PDO, ni x509, ni form_login,
  ni ExpressionLanguage, ni fragments, ni HttpCache, ni Monolog : la majorité des avis Symfony/Twig 2026 visent des
  composants **absents de l'exécution** (`not-used`).
- Le composant Yaml ne lit que `app/config/*.yml` ; UrlGenerator ne reçoit que des id `\d+` ; aucun nom de template
  n'est dynamique → `used-not-reachable`.
- La protection de `/admin` est doublée : `access_control ^/admin` **et** `isGranted('ROLE_ADMIN')` dans
  `AdminController::newVehicleAction`, ce qui atténue CVE-2025-64500.
- `security-acl` 3.0.4 est installé mais non enregistré dans `AppKernel` (aucun avis le concernant à ce jour).

## 3. Écarts avec l'étape précédente (`--diff`)

Sans objet : step-00 est la première entrée de `docs/security/vulnerabilities.json`.

## 4. Failles atteignables (`reachable`) et critiques/hautes

| Faille | Paquet | Installé | Corrige | Disparaît à | Sévérité | Exposition | Recommandation |
|---|---|---|---|---|---|---|---|
| CVE-2022-22720 (smuggling sur erreur de corps) | debian10/apache2 | 2.4.38-3+deb10u4 | 2.4.38-3+deb10u8 | step-03 (nouvelle image) | critical 9.8 | reachable | Environnement local publié sur 127.0.0.1 : accepter jusqu'à step-03 ou reconstruire l'image avec `apt-get upgrade` (changement d'environnement à valider) |
| PHP-RUNTIME-EOL | php | 7.2.34 | 8.2+ (8.4 cible) | **step-07** | high | reachable | Inhérent au chemin : 7.4 et 8.1 sont aussi hors support ; seule step-07 corrige |
| OS-EOL-debian10 | image de base | Debian 10.7 | image supportée | step-07 (rescanner step-03 et step-05) | high | reachable | Idem : bases php:7.4/8.1 à vérifier à leur marche |
| GHSA-3rg7-wf37-54rm / CVE-2025-64500 (PATH_INFO) | symfony/symfony | 2.8.52 | 5.4.50 | step-04 | high 7.3 | reachable (atténuée) | Conserver la double vérification ROLE_ADMIN du contrôleur pendant toute la migration |
| CFG-003 compte gestionnaire/flotte2016 | app/config/security.yml | step-00 | identifiants hors dépôt, hachés | hors marche | high | reachable | Décision utilisateur ; ne pas corriger pendant une marche (bug pour bug) |
| TRIVY-HIGH-debian10-apache2 (17 CVE, dont 2 du cœur) | debian10/apache2 | 2.4.38-3+deb10u4 | 2.4.59-1~deb10u1 | step-03 | high | reachable | Voir CVE-2022-22720 |
| GHSA-3gv2-29qc-v67m / CVE-2022-24895 (fixation CSRF) | symfony/symfony | 2.8.52 | 4.4.50 | step-02 | medium 6.3 | reachable | Corrigée par la montée en 4.4 |
| CVE-2024-8925 (multipart PHP) | php | 7.2.34 | 8.1.30 | step-05 | medium 5.3 | reachable | Disparaît avec PHP 8.1 récent |
| CFG-002 encodeur plaintext | app/config/security.yml | step-00 | password hasher | step-06 (à arbitrer) | medium | reachable | Changement de comportement à consigner dans `accepted-changes.md` |
| CFG-004 X-Powered-By / signature / X-Fleet-Version | docker/app/php.ini | step-00 | `expose_php=Off` | hors marche | low | reachable | Environnement ; `X-Fleet-Version` est un comportement applicatif à préserver |

### Critiques et hautes non atteignables (extrait trié)

| Faille | Paquet | Installé | Corrige | Disparaît à | Sévérité | Exposition |
|---|---|---|---|---|---|---|
| GHSA-7p85-w9px-jpjp / CVE-2026-46633 (`{% use %}` injection de code) | twig/twig | 1.42.5 | 3.26.0 | step-05 | critical 9.8 | used-not-reachable (aucun nom de template dynamique) |
| CVE-2022-37454 (SHA-3) | php | 7.2.34 | 7.4.33 | step-03 | critical 9.8 | not-used |
| CVE-2021-21708 (filter float UAF) | php | 7.2.34 | 7.4.28 | step-03 | critical 9.8 | not-used |
| 34 CVE CRITICAL de l'image (apache2 ×10, glibc ×4, expat ×7, gnutls ×2, zlib ×2, sqlite3, openssl, openldap, lz4, libtasn1, dpkg, libbsd, db5.3, linux-libc-dev) | debian10/* | Debian 10.7 | deb10u* (archive) | step-03 | critical | 11 used-not-reachable (expat ×7, sqlite3, zlib, 2 fonctions du cœur Apache), 23 not-used |
| GHSA-2v3c-qxhr-7vpv / CVE-2026-109413 (Yaml récursion) | symfony/symfony | 2.8.52 | 5.4.54 | step-04 | high 8.7 | used-not-reachable |
| GHSA-ph86-p8f6-f9r2 / CVE-2026-45063 (x509 DN) | symfony/symfony | 2.8.52 | 5.4.52 | step-04 | high 8.7 | not-used |
| GHSA-qq5c-677p-737q / CVE-2024-51736 (Process Windows) | symfony/symfony | 2.8.52 | 5.4.46 | step-04 | high 8.6 | not-used |
| GHSA-m7v2-7gxm-vc2v / CVE-2026-45077 (MonologBridge) | symfony/symfony | 2.8.52 | 5.4.52 | step-04 | high 8.1 | not-used |
| GHSA-qpmx-3rfj-7rhv / CVE-2026-45067 (Mime CRLF) | symfony/symfony | 2.8.52 | 5.4.52 | step-04 | high 7.1 | not-used |
| GHSA-6h46-9jf5-q59x / CVE-2026-48489 (failure_forward) | symfony/symfony | 2.8.52 | 5.4.53 | step-04 | high | not-used |
| GHSA-529h-vh3j-85hq / CVE-2026-49981 (sandbox) | twig/twig | 1.42.5 | 3.27.0 | step-05 | high 8.7 | not-used |
| GHSA-pr2w-4gpj-cpq4 / CVE-2026-47732 (sandbox __toString) | twig/twig | 1.42.5 | 3.26.0 | step-05 | high | not-used |
| GHSA-52m2-vc4m-jj33 / CVE-2022-39261 (loader hors répertoire) | twig/twig | 1.42.5 | 1.44.7 / 2.15.3 | step-02 | high 7.5 | used-not-reachable |
| CFG-001 secret Symfony par défaut | parameters.yml.dist | step-00 | secret hors dépôt | hors marche (step-02 : `APP_SECRET`) | high | used-not-reachable (pas de remember_me ni de `_fragment`) |
| 10 agrégats HIGH de l'image (glibc, expat, openssl, libxml2, sqlite3, zlib, libs TLS, libs clientes, outils système, noyau) | debian10/* | Debian 10.7 | deb10u* | step-03 | high | 6 used-not-reachable, 4 not-used |

Le détail de chaque entrée (plage affectée, versions qui corrigent, justification) est dans
`docs/security/vulnerabilities.json`.

## 5. Calendrier de disparition prévu (chemin `steps.json`)

| Marche | Ce qui doit disparaître | Restera ouvert |
|---|---|---|
| step-01 (SF 3.4) | rien : toutes les plages couvrent `>=3.0.0,<4.0.0` | tout |
| step-02 (SF 4.4, Twig 2) | CVE-2021-21424, CVE-2022-24894, CVE-2022-24895, CVE-2023-46734 (4.4.51) ; Twig CVE-2022-39261, CVE-2024-45411 (2.16) | avis Symfony 2024-2026, Twig 2026 |
| step-03 (PHP 7.4, nouvelle image) | CVE PHP corrigées en 7.4.x (21705, 21707, 21708, 31628, 31629, 37454) ; **toutes** les entrées `debian10/*` | PHP-RUNTIME-EOL (7.4 EOL), CVE PHP corrigées en 8.1 |
| step-04 (SF 5.4, Twig 3) | tous les avis `symfony/symfony` (5.4.56 requis pour le lot du 2026-11-04) ; Twig CVE-2024-51754/51755 (3.11.2) | Twig 2026 (3.26/3.27 exigent PHP ≥ 8.1) |
| step-05 (PHP 8.1) | Twig 2026 (3.26.0 / 3.27.0) ; CVE-2024-2756, CVE-2024-3096, CVE-2024-8925 | PHP-RUNTIME-EOL (8.1 EOL 2025-12) |
| step-07 (PHP 8.4) | PHP-RUNTIME-EOL, OS-EOL-debian10 (si l'image 8.4 est saine) | config `CFG-*` (hors marche) |

### Alertes `G-VULN` attendues (à anticiper, pas de faille réelle)

- **step-02** : le passage à Flex remplace `symfony/symfony` par des composants (`symfony/http-foundation`,
  `symfony/yaml`, `symfony/routing`…). La clé de suivi étant `id + package`, les avis encore présents passeront
  `fixed` sous `symfony/symfony` et **`introduced`** sous les noms de composants. Ce sera un changement de nom, pas
  une régression : à arbitrer en conséquence.
- **step-03** et **step-05** : chaque nouvelle image de base apporte ses propres CVE OS, qui entreront en
  `introduced`. Rescanner et qualifier avant d'accepter.

## 6. Autres constats (non suivis comme failles)

- Paquets **abandonnés** : `doctrine/annotations`, `doctrine/cache`, `doctrine/common`, `doctrine/doctrine-cache-bundle`,
  `sensio/framework-extra-bundle` (remplacé par les attributs natifs, step-04/06).
- Twig **1.44.8** (compatible PHP 7.2.5+) corrigerait CVE-2022-39261 et CVE-2024-45411 dès step-00. Hors chemin de
  migration, changement de dépendance → décision de l'utilisateur uniquement.
- `ReservationController` (`/vehicules/{id}/reserver`, `/reservations/{id}/retour`) accepte des POST anonymes :
  c'est le comportement fonctionnel de 2016, conservé bug pour bug, et non une faille de dépendance.
- La dérogation durable des commits (CLAUDE.md, 2026-11-17) couvre CFG-001 et les CVE `symfony/symfony` 2.8.52 /
  `twig/twig` 1.42.5. Elle **ne couvre pas** les entrées `php-runtime`, `os-image`, ni CFG-002 à CFG-005.

## 7. Fichiers

- Suivi versionné : `docs/security/vulnerabilities.json` (fusion par `.claude/bin/track-merge.py`)
- Constats bruts : `.claude/state/migration/security/step-00-findings.json`
- Sorties OSV : `.claude/state/migration/security/osv-step-00.md`, `osv-step-00.json`
