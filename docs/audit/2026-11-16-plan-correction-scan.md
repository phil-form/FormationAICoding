# Plan de correction — contrôles de sécurité du 16/11/2026

Source : `/commit` → `.claude/state/audit/commit-scan/20261116-151956/report.md` (Trivy 0.76.2, Semgrep 1.185.0).
**Aucune correction n'a été appliquée.** Décisions prises le 16/11/2026 (voir fin de document) — plan prêt à exécuter.

## Synthèse

| # | Résultat | Sévérité | Origine | Atteignable ? | Action proposée | Effort |
|---|---|---|---|---|---|---|
| 1 | XXE possible dans `pit-summary.py` (parseur XML natif) | ERROR (SAST) | notre code (outillage) | faible : lit un rapport PIT généré localement | parseur `expat` refusant `DOCTYPE` / entités | 30 min |
| 2 | `docker/test/Dockerfile` s'exécute en root | HIGH (config) | notre code | conteneur éphémère, mais avec le **socket Docker** monté | utilisateur non-root + groupe du socket | 1 h |
| 3 | Pas de `HEALTHCHECK` (dev, test) | LOW (config) | notre code | — | dev : en ajouter un ; test : exception documentée | 15 min |
| 4 | `haproxy:3.2` (Debian) : 2 HIGH corrigeables, 46 sans correctif | HIGH | image tierce (test-prod) | oui (entrée publique du test-prod) | `haproxy:3.2.26-alpine` : **0 HIGH** | 15 min |
| 5 | `postgres:18-alpine` : 26 HIGH/CRITICAL, **tous dans `gosu`** | CRITICAL | image tierce | très faible : `gosu` ne sert qu'à quitter root au démarrage | **D1 → A** : image dérivée sans `gosu`, exécutée en utilisateur `postgres` | 2 h |
| 6 | WAF : 3 HIGH `libxslt` | HIGH | image tierce | **non** : seul le module nginx XSLT l'utilise, et il n'est pas chargé | **D2 → B** : exception jusqu'au prochain build CRS (échéance 30 j) | 15 min |
| 7 | `openbao:2.7.2` : 2 HIGH (go-jose RSA, x/net/html) — **déni de service** | HIGH | image tierce | faible : réseau interne seulement, méthode d'auth JWT non activée | **D3** : exception 30 j + veille 2.7.3 | 15 min |
| 8 | Images référencées par tag mobile | — (chaîne d'approvisionnement) | notre configuration | — | **D4** : épingler par **digest** partout + procédure de mise à jour | 1 h |
| 9 | Pas de mécanisme d'exception tracé | — (processus) | outillage | — | `.trivyignore.yaml` avec justification **et date d'expiration** | 30 min |

Résultats non bloquants restants : 1 MEDIUM dans l'image de l'API (`formation-api`), MEDIUM/LOW des images tierces
(suivis par le scan, sans action immédiate).

## Détail par action

### 1. `pit-summary.py` — XXE (notre code)

- **Constat** : `xml.etree.ElementTree.parse` sur `target/pit-reports/mutations.xml`. Le fichier est produit localement
  par PIT : l'exploitation suppose un rapport piégé (ex. dépendance de test malveillante écrivant ce fichier).
- **Correction** : parser avec `xml.parsers.expat` (bibliothèque standard, aucune dépendance ajoutée) en **refusant**
  toute déclaration `DOCTYPE` / `ENTITY` (rapport PIT légitime : aucune), taille de fichier bornée.
  Alternative : `defusedxml` — écartée (dépendance pip à installer partout où la skill tourne).
- **Vérification** : Semgrep sans `ERROR` ; test avec un XML contenant un `DOCTYPE` → refus ; synthèse identique sur un
  rapport PIT normal (jeu de test de la session).

### 2. `docker/test/Dockerfile` en root (notre code)

- **Constat** : le conteneur de tests tourne en root **et** dispose du socket Docker (Testcontainers) : une dépendance de
  test compromise obtiendrait root dans le conteneur, en plus du contrôle de Docker.
- **Correction** : utilisateur `tester` avec l'UID/GID de l'hôte (comme `docker/dev`), ajouté au groupe propriétaire du
  socket (`group_add: ${DOCKER_GID}` dans `docker-compose.yml`) ; cache Maven dans son `HOME` ; suppression du `chown`
  final de `run-tests.sh` (rapports déjà écrits avec le bon propriétaire).
- **Limite assumée** : l'accès au socket Docker reste équivalent à root sur l'hôte — inhérent à Testcontainers ; le
  conteneur n'est lancé qu'à la demande (`./start.sh --test`).
- **Vérification** : `./start.sh --test` (98 tests verts), `trivy config` sans HIGH, rapports lisibles par l'utilisateur.

### 3. `HEALTHCHECK` manquants (LOW)

- `docker/dev/Dockerfile` : ajouter `HEALTHCHECK` sur `/actuator/health` (utile à `depends_on`).
- `docker/test/Dockerfile` : conteneur éphémère sans service → **exception** `DS-0026` documentée (action 9).

### 4. HAProxy (image tierce — test-prod)

- **Constat** : la variante Debian embarque 205 vulnérabilités (dont 48 HIGH) ; `haproxy:3.2.26-alpine` : 0 HIGH, 1 MEDIUM.
- **Correction** : `image: haproxy:3.2.26-alpine@sha256:…` dans `docker-compose.test-prod.yml` (même `haproxy.cfg`).
- **Prod réelle** : HAProxy est installé sur le serveur (paquet système) → ses mises à jour suivent l'OS ; à noter dans
  `docs/ops/DEPLOIEMENT-PROD.md`.
- **Vérification** : `./start.sh --test-prod` + `docker/test-prod/check.sh` (22/22), scan de l'image.

### 5. PostgreSQL — `gosu` (image tierce)

- **Constat** : les 26 HIGH/CRITICAL concernent la bibliothèque standard Go **compilée dans `gosu`**
  (`/usr/local/bin/gosu`), utilisé uniquement par le point d'entrée pour passer de root à `postgres`. Ces CVE visent
  `net/http`, `crypto/x509`… que `gosu` n'utilise pas. La variante Debian est pire (151 HIGH).
- **Décision D1 → option A** : image dérivée `docker/db/Dockerfile` — `FROM postgres:18.7-alpine@sha256:…`,
  `RUN rm -f /usr/local/bin/gosu`, service `db` lancé en `user: "70:70"` (utilisateur `postgres` d'Alpine ; les volumes
  existants lui appartiennent déjà). Gain : plus de `gosu` **et** PostgreSQL ne démarre plus jamais en root.
  À vérifier : `docker/db/roles.sh`, `restore.sh`, recettes OpenBao (`check-phase1.sh`), tests.
- Option B écartée : exception en attendant une image officielle reconstruite.

### 6. WAF ModSecurity — `libxslt` (image tierce)

- **Constat** : `libxslt` n'est lié qu'à `ngx_http_xslt_filter_module`, **présent mais non chargé** (aucun `load_module`) :
  CVE non atteignables. Alpine propose déjà le correctif (`libxslt-1.1.43-r4`). Pas de build CRS plus récent
  (cadence observée : 2 à 3 semaines).
- **Décision D2 → option B** : exception `.trivyignore.yaml` pour les CVE `libxslt` de l'image CRS, justification
  « module XSLT non chargé », **`expired_at` = 16/12/2026** ; à la publication d'un build CRS plus récent : mise à jour
  du digest, scan, retrait de l'exception.
- Option A écartée pour l'instant : image dérivée sans `nginx-module-xslt` / `libxslt` (reste possible si le build
  amont tarde).

### 7. OpenBao 2.7.2 (image tierce)

- **Constat** : `go-jose` (DoS par RSA forgé dans un JWS) et `x/net/html` (DoS par HTML forgé) dans le binaire `bao`.
  Aucune version corrigée (2.7.2 = dernière, 26/10). OpenBao n'est **pas publié** (réseau interne, TLS), la méthode
  d'authentification JWT/OIDC n'est pas activée ; un DoS exigerait déjà un accès au réseau interne.
- **Décision D3** : exception, **`expired_at` = 16/12/2026** (ou sortie de 2.7.3, si plus tôt) ; mise à jour dès publication.
- **Vérification** : le scan rappelle l'exception à son expiration (elle redevient bloquante).

### 8. Épinglage des images par digest (configuration)

- **Constat** : tags mobiles (`postgres:18-alpine`, `haproxy:3.2`…) → contenu changeant sans trace ; l'image locale
  `postgres:18-alpine` avait 6 mois de retard.
- **Correction** : `image: <tag précis>@sha256:<digest>` dans les 3 fichiers compose et les `FROM` des Dockerfiles ;
  mise à jour = changement visible en revue de code, validé par `/commit`. Outillage de mise à jour automatique
  (Renovate) à envisager avec la CI (phase 6).

### 9. Exceptions tracées (processus)

- `.trivyignore.yaml` versionné : pour chaque exception, identifiant, chemin/paquet, **justification** et
  **`expired_at`** (Trivy ignore l'exception au-delà de cette date : le résultat redevient bloquant).
- `security-scan.sh` : utiliser ce fichier et lister les exceptions actives (et celles qui expirent sous 7 jours) dans
  le rapport. Les exceptions Semgrep éventuelles : `.semgrepignore` / `nosemgrep` **avec** justification, revue
  obligatoire.

## Ordre d'exécution proposé

1. **9** (mécanisme d'exceptions) — pré-requis des actions 3, 6 et 7.
2. **1, 2, 3** — notre code, sans décision ouverte.
3. **4, 5** — images HAProxy Alpine et PostgreSQL dérivée sans `gosu`.
4. **6, 7** — exceptions datées (WAF `libxslt`, OpenBao).
5. **8** — épinglage par digest (en dernier : les digests retenus sont ceux des images validées aux étapes 3–4).
6. `/commit` : le scan doit être sans résultat bloquant (exceptions comprises) ; recettes `./start.sh --test`,
   `docker/openbao/check-phase1.sh`, `docker/test-prod/check.sh`.

Estimation totale : ½ journée à 1 journée selon les options.

## Décisions (16/11/2026)

| # | Question | Décision |
|---|---|---|
| D1 | PostgreSQL : image dérivée sans `gosu` (A) ou exception temporaire (B) | **A** — image dérivée, utilisateur `postgres` |
| D2 | WAF : image dérivée sans XSLT (A) ou exception jusqu'au prochain build CRS (B) | **B** — exception jusqu'au 16/12/2026 |
| D3 | OpenBao : exception 30 jours + veille 2.7.3 | **Oui** — jusqu'au 16/12/2026 |
| D4 | Épinglage de toutes les images par digest | **Oui**, compose et Dockerfiles |

Échéance commune des exceptions : **16/12/2026** — le scan `/commit` les rendra de nouveau bloquantes à cette date.
