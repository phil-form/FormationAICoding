# Audit de sécurité — step-01 (PHP 7.2 / Symfony 3.4)

> Date : 2026-11-17 · Mode : `--diff` (comparaison avec step-00)
> Lecture seule : aucun test d'exploitation, aucune requête vers `app` / `app-ref`, aucun test lancé, aucune
> modification de dépendance. Exposition jugée par lecture du code (`src/AppBundle`, `app/config`, `web/`, `vendor/`).

**Gate `G-VULN` : non levée.** Aucune faille `introduced`, aucune réapparition, aucune acceptation (donc aucune
acceptation échue). Simulation `track-merge.py --dry-run` : `new 0 · fixed 1 · open 110 · accepted 0 · reappeared 0`,
aucune alerte.

## 1. Contexte de l'étape

| Élément | step-00 | step-01 |
|---|---|---|
| PHP exécuté (`env.sh php-version`) | 7.2.34 | **7.2.34** (inchangé) |
| `symfony/symfony` | 2.8.52 | **3.4.49** |
| `twig/twig` | 1.42.5 | 1.42.5 (inchangé) |
| Autres paquets du lock | — | inchangés pour l'audit (Composer et OSV ne signalent que `symfony/symfony` et `twig/twig`) |
| Image `app` | `flotte-mig/app:php72` (Debian 10.7) | identique (`docker/app/php72/Dockerfile` non modifié) : constats Trivy de step-00 repris |

### Sources

| Source | Résultat step-01 | step-00 |
|---|---|---|
| `tools.sh composer audit --locked` (Composer récent) | **48 avis** sur 2 paquets (32 `symfony/symfony`, 16 `twig/twig`) ; 5 paquets abandonnés (inchangés) | 49 (33 + 16) |
| `tools.sh osv-scanner --lockfile=composer.lock` | **35 avis GHSA** (21 `symfony/symfony` + 14 `twig/twig`), tous recoupés avec Composer ; 13 avis connus seulement de Composer (FriendsOfPHP) | 36 (22 + 14) |
| Runtime PHP 7.2.34 | inchangé : 9 CVE + `PHP-RUNTIME-EOL` repris tels quels | idem |
| Image (Trivy) | non rescannée : image et digest de base inchangés ; 47 entrées `os-image` reprises | 47 |
| Configuration | `CFG-001` à `CFG-005` inchangés ; nouveaux réglages de step-01 examinés (§ 6), sans nouvelle faille | 5 |

Sortie OSV brute : `.claude/state/migration/security/step-01-osv.json`.

## 2. Synthèse (110 entrées suivies, toutes `open` sauf 1 `fixed`)

| Sévérité | composer | php-runtime | config | os-image | **Total** |
|---|---|---|---|---|---|
| critical | 1 | 2 | 0 | 35 | **38** |
| high | 10 | 1 | 2 | 12 | **25** |
| medium | 14 | 7 | 2 | 0 | **23** |
| low | 11 | 0 | 1 | 0 | **12** |
| non notée (`unknown`) | 12 | 0 | 0 | 0 | **12** |
| **Total ouvertes** | 48 | 10 | 5 | 47 | **110** |

| Exposition (ouvertes) | composer | php-runtime | config | os-image | **Total** |
|---|---|---|---|---|---|
| `reachable` | 2 | 2 | 3 | 3 | **10** |
| `used-not-reachable` | 13 | 0 | 1 | 17 | **31** |
| `not-used` | 33 | 8 | 0 | 27 | **68** |
| `unknown` | 0 | 0 | 1 | 0 | **1** |

## 3. Écarts avec step-00 (`--diff`)

### Résolues (1)

| Faille | Paquet | step-00 | Corrigée par | Sévérité | Exposition à step-00 |
|---|---|---|---|---|---|
| GHSA-5pv8-ppvj-4h68 / CVE-2021-21424 (énumération d'utilisateurs via les réponses d'authentification) | symfony/symfony | 2.8.52 | 3.4.49 (correctif de la branche 3.4 en 3.4.48) | medium 5.3 | used-not-reachable |

Correction du calendrier de step-00 (§ 5) : il annonçait « step-01 : rien ». La plage enregistrée à step-00
(`>=3.0.0,<4.4.24`) était trop large ; Composer comme OSV ne signalent plus l'avis en 3.4.49. La correction n'a aucun
effet observable pour l'application : le point d'entrée Basic renvoyait déjà le même 401 que l'utilisateur existe
ou non.

### Ajoutées (`introduced`) : aucune

Aucun avis propre à la branche 3.4. Les composants arrivés avec 3.4 (Serializer avec `DataUriNormalizer`, Cache)
étaient déjà couverts par des avis ouverts à step-00. Ces avis sont réévalués ci-dessous, sans changement
d'exposition.

### Persistantes (110)

- 32 avis `symfony/symfony` : version installée passée à 3.4.49, toutes les plages couvrent encore `3.4.x`.
  `GHSA-wg5q-m2ww-f3c9` reste dans la plage (`>=3.4.14,<4.0.0`).
- 16 avis `twig/twig` 1.42.5, 10 entrées `php-runtime`, 47 entrées `os-image` et 5 `config` : inchangés.

Réévaluations d'exposition dues à Symfony 3.4. Aucun **niveau** ne change, seules les justifications sont mises à jour :

| Faille | Changement en 3.4 | Exposition |
|---|---|---|
| GHSA-pxj9-jh84-vhm5 (`DataUriNormalizer`) | La classe existe désormais (Serializer 3.4), mais `framework.serializer` n'est pas activé | not-used |
| CVE-2026-118515 (Serializer → `UploadedFile`) | Idem, et aucun `FileType` | not-used |
| GHSA-6qh9-h6wf-jgqc (`PdoAdapter`) | Composant Cache chargé (`cache.app` = FilesystemAdapter par défaut), aucun `PdoAdapter` | not-used |
| GHSA-m7v2-7gxm-vc2v (MonologBridge) | Le service `logger` est explicitement un `Psr\Log\NullLogger`, MonologBundle reste absent | not-used |
| GHSA-qpmx / GHSA-vqc8 / GHSA-xx3c (Mime, Mailer) | Composants toujours absents de 3.4.49 (introduits en 4.3, vérifié dans `vendor/`) | not-used |
| GHSA-3rg7-wf37-54rm (PATH_INFO) | La double vérification est conservée : `AdminController` appelle `$this->isGranted('ROLE_ADMIN')` (au lieu de `security.context`) | reachable, atténuée |

### Acceptations échues : aucune (aucune entrée `accepted`)

## 4. Tableau trié par sévérité × exposition (critiques, hautes et toutes les `reachable`)

| Faille | Paquet | Installé | Corrige | Disparaît à | Sévérité | Exposition | Recommandation |
|---|---|---|---|---|---|---|---|
| CVE-2022-22720 (smuggling sur erreur de corps) | debian10/apache2 | 2.4.38-3+deb10u4 | 2.4.38-3+deb10u8 | step-03 (nouvelle image) | critical 9.8 | reachable | Environnement local sur 127.0.0.1 : accepter jusqu'à step-03 ou reconstruire l'image (décision utilisateur) |
| GHSA-7p85-w9px-jpjp / CVE-2026-46633 (`{% use %}`) | twig/twig | 1.42.5 | 3.26.0 | step-05 | critical 9.8 | used-not-reachable | Aucun nom de template dynamique : surveiller à chaque marche |
| CVE-2021-21708, CVE-2022-37454 | php | 7.2.34 | 7.4.28 / 7.4.33 | step-03 | critical 9.8 | not-used | — |
| 34 autres CRITICAL de l'image | debian10/* | Debian 10.7 | deb10u* | step-03 | critical | 11 used-not-reachable, 23 not-used | — |
| PHP-RUNTIME-EOL | php | 7.2.34 | 8.2+ (8.4 cible) | step-07 | high | reachable | Inhérent au chemin (7.4 et 8.1 aussi hors support) |
| OS-EOL-debian10 | debian10/base-image | 10.7 | image supportée | step-07 (rescanner step-03, step-05) | high | reachable | Idem |
| GHSA-3rg7-wf37-54rm / CVE-2025-64500 (PATH_INFO) | symfony/symfony | 3.4.49 | 5.4.50 | step-04 | high 7.3 | reachable (atténuée) | Garder `isGranted('ROLE_ADMIN')` dans `AdminController` jusqu'à step-04 |
| CFG-003 compte gestionnaire en clair | app/config/security.yml | step-01 | identifiants hors dépôt, hachés | hors marche | high | reachable | Décision utilisateur |
| TRIVY-HIGH-debian10-apache2 | debian10/apache2 | 2.4.38-3+deb10u4 | 2.4.59-1~deb10u1 | step-03 | high | reachable | Voir CVE-2022-22720 |
| GHSA-2v3c-qxhr-7vpv / CVE-2026-109413 (Yaml) | symfony/symfony | 3.4.49 | 5.4.54 | step-04 | high 8.7 | used-not-reachable | — |
| GHSA-52m2-vc4m-jj33 / CVE-2022-39261 (loader Twig) | twig/twig | 1.42.5 | 1.44.7 / 2.15.3 | step-02 | high 7.5 | used-not-reachable | — |
| CFG-001 secret de démo | app/config/parameters.yml.dist | step-01 | secret hors dépôt | hors marche (step-02 : `APP_SECRET`) | high | used-not-reachable | Dérogation durable des commits |
| 6 agrégats HIGH de l'image (glibc, expat, openssl, libxml2, sqlite3, zlib) | debian10/* | Debian 10.7 | deb10u* | step-03 | high | used-not-reachable | — |
| GHSA-ph86 (x509), GHSA-qq5c (Process Windows), GHSA-m7v2 (MonologBridge), GHSA-qpmx (Mime), GHSA-6h46 (failure_forward) | symfony/symfony | 3.4.49 | 5.4.46 à 5.4.53 | step-04 | high | not-used | — |
| GHSA-529h, GHSA-pr2w (sandbox) | twig/twig | 1.42.5 | 3.27.0 / 3.26.0 | step-05 | high | not-used | — |
| 4 agrégats HIGH de l'image (libs TLS, libs clientes, outils système, noyau) | debian10/* | Debian 10.7 | deb10u* | step-03 | high | not-used | — |
| GHSA-3gv2-29qc-v67m / CVE-2022-24895 (fixation CSRF) | symfony/symfony | 3.4.49 | 4.4.50 | **step-02** | medium 6.3 | reachable | Disparaîtra avec la montée en 4.4 |
| CVE-2024-8925 (multipart) | php | 7.2.34 | 8.1.30 | step-05 | medium 5.3 | reachable | — |
| CFG-002 encodeur plaintext | app/config/security.yml | step-01 | password hasher | step-06 (à arbitrer) | medium | reachable | Changement de comportement à consigner |
| CFG-004 divulgation de version | docker/app/php.ini | step-01 | `expose_php=Off` | hors marche | low | reachable | Environnement |

Le détail de chaque entrée (plage affectée, versions qui corrigent, justification) est dans `docs/security/vulnerabilities.json`.

## 5. Calendrier de disparition prévu (mis à jour)

| Marche | Ce qui doit disparaître |
|---|---|
| step-02 (SF 4.4, Twig 2) | CVE-2022-24894, CVE-2022-24895, CVE-2023-46734 (4.4.51) ; Twig CVE-2022-39261, CVE-2024-45411. **Attendu** : `G-VULN` de renommage (`symfony/symfony` → composants Flex), voir step-00 § 5 |
| step-03 (PHP 7.4, nouvelle image) | CVE PHP corrigées en 7.4.x ; toutes les entrées `debian10/*` (nouvelles CVE d'image à qualifier) |
| step-04 (SF 5.4, Twig 3) | tous les avis `symfony/symfony` restants (5.4.56 requis) ; Twig CVE-2024-51754/51755 |
| step-05 (PHP 8.1) | Twig 2026 (3.26/3.27) ; CVE-2024-2756, CVE-2024-3096, CVE-2024-8925 |
| step-07 (PHP 8.4) | PHP-RUNTIME-EOL, OS-EOL-debian10 |

## 6. Constats de configuration de step-01 (examinés, non suivis comme failles)

- **`app.session.handler.native` (`\SessionHandler` natif au lieu de `StrictSessionHandler`)** : pas d'incidence de
  sécurité mesurable. `StrictSessionHandler` ne protège contre la fixation de session (refus des identifiants non
  initialisés via `validateId`) que si `session.use_strict_mode=1`. Or `docker/app/php.ini` ne le définit pas
  (défaut PHP 0) et `NativeSessionStorage` 3.4 ne le force pas. Avec la configuration par défaut de 3.4, la
  protection ne serait donc pas non plus active. Le comportement est celui de step-00. La fixation de session sur
  l'authentification reste couverte par `session_fixation_strategy: migrate` (défaut du pare-feu `main`).
  Point de vigilance : si `session.use_strict_mode=1` est un jour activé, le handler natif devra être réexaminé.
- **`logger` = `Psr\Log\NullLogger`** : ce n'est pas une faille exploitable. C'est une faiblesse de journalisation
  (OWASP A09) déjà présente à step-00, où il n'y avait aucun logger. Les exceptions HTTP non gérées et les échecs
  d'authentification `http_basic` ne sont journalisés nulle part. Les notices PHP natives vont toujours dans
  `/var/log/php/error.log`. Le comportement est préservé par choix (« bug pour bug ») ; c'est un candidat à une
  décision hors marche.
- **`LegacyAllowHeaderListener`** : il reconstruit seulement l'en-tête `Allow` et le message de la 405, sans effet
  sur le routage, l'authentification ou l'autorisation.
- **`LegacyIntegerToLocalizedStringTransformer`** : il conserve l'arrondi des kilométrages décimaux de 2.8. Les
  contraintes (`GreaterThanOrEqual`, contrôles de `ReservationManager`) s'appliquent toujours après la conversion :
  aucune validation n'est contournée.
- `security.yml`, `web/app.php`, `web/app_dev.php`, `AppKernel` : inchangés. `CFG-001` à `CFG-005` sont repris tels quels.
- Paquets abandonnés (inchangés) : `doctrine/annotations`, `doctrine/cache`, `doctrine/common`,
  `doctrine/doctrine-cache-bundle`, `sensio/framework-extra-bundle`.
- Dérogation durable des commits : elle vise `symfony/symfony` **2.8.52**. Les avis désormais rattachés à
  **3.4.49** sortent de son périmètre strict (« autre version ») : leur prise en compte au commit de step-01 revient
  à l'utilisateur (`/step-commit`).

## 7. Fichiers

- Suivi versionné : `docs/security/vulnerabilities.json` (fusion par `.claude/bin/track-merge.py`)
- Constats : `.claude/state/migration/security/step-01-findings.json`
- Sortie OSV : `.claude/state/migration/security/step-01-osv.json`
