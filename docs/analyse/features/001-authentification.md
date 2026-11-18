# 001 — Authentification (login, refresh, logout)

| Champ | Valeur |
|---|---|
| Statut | validée (revue 6 : OK, 17/11/2026) |
| Source | `docs/analyse/client/delvaux-gestion-de-flotte.md` (§ 2.1 rôles, § 4 RM9, § 5 Utilisateur / Rôle, § 6 exigences techniques, § 7 comptes de démonstration, § 8 C4) ; `docs/adr/0001-gestion-des-secrets.md` (D4, D7) ; `docs/adr/0002-authentification.md` (D1–D5) ; `Next.md` phase 4 |
| Sécurité | oui — authentification, jetons de session, mots de passe, rôles, limitation de débit, journalisation d'événements de sécurité |
| Données sensibles | oui — aucune donnée `S2` ; `SECRET` : mot de passe, hachage du mot de passe, jeton de rafraîchissement, jeton d'accès (hachage / jamais renvoyés ni journalisés) |
| Dépend de | socle phases 1–3 (OpenBao, `CryptoService`) ; infra phase 4 en place : `framework.crypto.PasswordPepper` (+ `InMemoryPasswordPepper` de test), clé Transit `password-pepper`, Bouncy Castle, `CorsConfig.AUTH_PATHS` / `CorsConfig.CSRF_HEADER`, droits `transit/sign/jwt` et `transit/keys/jwt` (lecture) dans `policies/api.hcl`. **Nouvelle dépendance Maven (acceptée, Q1)** : `org.springframework.boot:spring-boot-starter-security-oauth2-resource-server` (version gérée par le BOM Spring Boot 4.1.2 ; l'ancien nom `spring-boot-starter-oauth2-resource-server` est déprécié sous Boot 4). Feature **002** dépend de celle-ci (entité `User`). **Prérequis de déploiement PRE-01** (§ 5.9) : l'IP cliente réelle doit parvenir à l'API à travers HAProxy et le WAF (sinon RG-05 devient un blocage global) ; livrables : `TRUSTED_PROXIES` de `docker-compose.test-prod.yml`, contrôles `CHK-01` / `CHK-02` dans `docker/test-prod/check.sh` ; mise à jour de `docs/ops/DEPLOIEMENT-PROD.md` et `docs/ops/OPENBAO.md` (hors périmètre des agents tests / code, à faire par l'utilisateur). |

## 1. Contexte et objectif

L'analyse client exige une authentification obligatoire avec mots de passe hachés (§ 6) et un contrôle des rôles côté
serveur (RM9). Aujourd'hui toutes les routes `/api/**` répondent 401 : aucune feature métier n'est utilisable.
Cette feature fournit la connexion (`login`), le renouvellement silencieux de session (`refresh`) et la déconnexion
(`logout`) selon l'ADR 0002 : jeton d'accès JWT ES256 de 5 min en mémoire côté Angular, jeton de rafraîchissement
opaque en cookie `HttpOnly`, à usage unique, organisé en familles. Elle définit aussi l'entité **utilisateur minimale**
(identifiant, hachage, rôles, dépôt, actif) nécessaire au login, alimente `framework.security.CurrentUser` à partir du
JWT et fournit un seeder de démonstration. Le CRUD des comptes, l'e-mail, le nom, la catégorie de permis et le NISS
relèvent de la feature 002.

## 2. Acteurs et rôles

| Acteur | Rôle applicatif | Ce qu'il peut faire |
|---|---|---|
| Visiteur non authentifié | — | `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout` (ces deux dernières avec le cookie de session) |
| Utilisateur authentifié | `DRIVER`, `FLEET_MANAGER`, `ADMIN` (cumulables, C4) | Tout ce qui précède + `GET /api/v1/auth/me` ; accès aux autres routes selon les features suivantes |
| Feature 002 (code) | — | Appelle `AuthService.revokeAllSessions` (changement de mot de passe, désactivation, suppression) **après** la mise à jour de `app_user`, dans la même transaction (RG-16), et `PasswordHasher.hash` |
| Système (tâche planifiée) | — | Purge des familles de jetons expirées |

Les rôles applicatifs sont ceux de `framework.security.Role` : `DRIVER` (CONDUCTEUR), `FLEET_MANAGER` (GESTIONNAIRE),
`ADMIN`.

## 3. Règles métier

- **RG-01** — Un utilisateur actif, non supprimé, non verrouillé, ayant au moins un rôle, qui fournit son identifiant de
  connexion et son mot de passe corrects obtient : un jeton d'accès (corps de la réponse), un cookie de rafraîchissement
  (`Set-Cookie`) et la description de l'utilisateur courant (id, rôles, dépôt). Une nouvelle **famille** de jetons de
  rafraîchissement est créée à chaque login réussi.
- **RG-02** — L'identifiant de connexion (`username`) est **insensible à la casse** : stocké normalisé (`trim` puis
  minuscules `Locale.ROOT`), recherché après la même normalisation. Format stocké : `^[a-z0-9][a-z0-9._-]{2,49}$`.
  Le mot de passe est utilisé **tel quel** (aucune normalisation, aucune troncature). Un identifiant **hors format**
  (ex. `ab`, `alice@example`, 51 à 100 caractères) n'est **pas** rejeté en validation : il suit le chemin
  « identifiant inconnu » (`dummyVerify`, échec IP enregistré, `401` identique). `LoginRequest.username` ne porte donc
  **aucun** `@Pattern` (seulement `@NotBlank @Size(max = 100)`, § 5.2), ce qui ne révèle pas le format.
- **RG-03** — Tout échec de login (identifiant inconnu, mot de passe faux, compte désactivé, compte supprimé, compte
  verrouillé, compte **sans aucun rôle**) produit **la même réponse** : `401`, `title` `Unauthorized`, `detail`
  `Invalid credentials.`, sans `Set-Cookie`. Quand aucun hachage réel n'est vérifié (identifiant inconnu ou supprimé,
  compte désactivé, verrouillé ou sans rôle), `PasswordHasher.dummyVerify` est appelé : il a **le même coût et les
  mêmes dépendances** que `verify` (place dans le sémaphore RG-21, appel `PasswordPepper.pepper(raw)` — donc OpenBao —,
  puis `Argon2PasswordEncoder.matches(pepper.value(), hachageFactice)`, hachage factice défini au § 5.5), pour ne révéler
  l'existence du compte ni par le temps de réponse ni par une erreur d'OpenBao (même `500` dans les deux cas, SEC-12).
- **RG-04** — Verrouillage par compte. Un compte est **verrouillé** si et seulement si `locked_until != null` **et**
  `now < locked_until`. Chaque mot de passe faux sur un compte existant, actif, ayant un rôle et non verrouillé
  incrémente `failed_login_count`. Quand l'incrément atteint `app.auth.login.max-failed-attempts` (**5**), le compte
  est verrouillé jusqu'à `now + app.auth.login.lock-duration` (**15 min**) et le compteur est remis à 0 ; sinon
  `locked_until` est remis à `null` (efface un verrou expiré). Pendant le verrouillage, tout login sur ce compte échoue
  (RG-03), même avec le bon mot de passe, sans modifier le compteur ni prolonger le verrou. Un login réussi remet le
  compteur à 0, `locked_until` à `null` et fixe `last_login_at = now`. Le verrouillage n'affecte pas les sessions déjà
  ouvertes (refresh). Ces écritures sont des mises à jour atomiques en base (RG-23).
  **Décision (concurrence), limitée au verrou** : un succès vérifié l'emporte sur un **verrou** posé entre-temps. Si
  une requête concurrente pose le verrou entre le `verify` réussi et l'écriture du succès, `registerSuccessfulLogin`
  efface ce verrou et le login réussit : le mot de passe correct a été vérifié avant le verrou. Le contrôle « compte
  verrouillé » se fait sur l'état lu par `findByUsername` (étape 2 de RG-24).
  **Cette décision ne s'étend ni au hachage, ni à `enabled`, ni à la suppression logique** : `registerSuccessfulLogin`
  est **conditionnelle** (§ 5.4) — elle ne s'applique que si `password_hash` est toujours égal au hachage vérifié,
  `enabled = true` et `deleted_at IS NULL`. Si le mot de passe a été changé, le compte désactivé ou supprimé entre la
  lecture (étape 2) et l'écriture (étape 5 de RG-24), elle renvoie `0` et le login échoue en `401` (RG-25).
- **RG-05** — Limitation par adresse IP cliente (adresse `HttpServletRequest#getRemoteAddr()`, telle que résolue par
  Tomcat `RemoteIpValve` : `X-Forwarded-For` pris en compte uniquement depuis `TRUSTED_PROXIES`, § 5.9 ; le code ne lit
  **jamais** lui-même `X-Forwarded-For`, `X-Real-IP` ni `Forwarded`). Cette règle suppose le prérequis de déploiement
  **PRE-01** (§ 5.9) : l'adresse résolue est celle du client vu par HAProxy, jamais celle d'un relais. Clé de comptage : l'adresse IPv4 telle quelle ; pour une IPv6, son
  préfixe **/64** (une IPv6 « IPv4-mapped » `::ffff:a.b.c.d` est traitée comme l'IPv4 `a.b.c.d`). L'adresse est
  analysée **sans aucune résolution DNS** : `java.net.InetAddress.ofLiteral(clientIp)` (JDK 22+) ; si elle lève
  `IllegalArgumentException` (chaîne non littérale), la chaîne brute est la clé. `InetAddress.getByName` /
  `getAllByName` sont interdits sur cette valeur. Pour chaque clé :
  `windowStart` = instant du premier échec de la fenêtre, `count` = nombre d'échecs. La clé est **bloquée** si et
  seulement si `count >= app.auth.login.ip-max-failures` (**20**) **et** `now < windowStart +
  app.auth.login.ip-window` (**5 min**). Un échec enregistré quand `now >= windowStart + ip-window` ouvre une nouvelle
  fenêtre (`windowStart = now`, `count = 1`). Une tentative de login depuis une clé bloquée reçoit `429` avec
  `Retry-After` = secondes restantes jusqu'à `windowStart + ip-window` (arrondi au supérieur, minimum 1), **sans**
  recherche d'utilisateur ni hachage. **Seuls les `401` de login (RG-03) comptent** comme échec : `429`, `503`, `500`,
  `400`, `415` ne comptent pas, et un login réussi ne réinitialise pas le compteur. Compteur en mémoire (instance
  unique, monolithe), table bornée à `app.auth.login.ip-max-tracked` (**10 000**) clés : à l'insertion d'une nouvelle
  clé dans une table pleine, la clé de plus ancien `windowStart` est évincée ; les entrées dont la fenêtre est terminée
  sont nettoyées.
- **RG-06** — Jeton d'accès : JWT compact JWS **ES256** signé par OpenBao Transit (`transit/sign/jwt`,
  `marshaling_algorithm=jws`), durée `app.auth.jwt.access-token-ttl` (**5 min**), contenu défini au § 5.8. Les rôles et
  le dépôt sont lus **en base** à chaque émission (login et refresh).
- **RG-07** — Toute requête portant `Authorization: Bearer <jwt>` (hors routes de RG-20) est authentifiée si et
  seulement si le jeton respecte toutes les règles de vérification du § 5.8 ; le `SecurityContext` contient alors un
  `CurrentUserAuthentication` dont le principal est `CurrentUser(id = sub, roles = roles, depotId = depot_id)`.
  Sinon : `401`. Le jeton n'est accepté **que** dans l'en-tête `Authorization` (jamais en paramètre d'URL, en corps ni
  en cookie). La vérification est locale (clés publiques Transit en cache) : aucun accès base **ni appel OpenBao** par
  requête. Un `kid` absent du cache, ou toute exception levée par `JwtKeyProvider.publicKey`, donne `401` (échec
  fermé, jamais `500`, § 5.8).
- **RG-08** — Jeton de rafraîchissement : 32 octets aléatoires (`SecureRandom`) encodés base64url sans remplissage
  (43 caractères), transmis **uniquement** dans le cookie `__Secure-refresh` (`HttpOnly; Secure; SameSite=Strict;
  Path=/api/v1/auth`, pas de `Domain`, `Max-Age` = `expires_at` **du jeton émis** − `now`, en secondes, arrondi
  inférieur — et non le temps restant de la famille). Il n'apparaît jamais dans un corps de réponse. Seule son
  empreinte est stockée : `token_hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
  .digest(cookieValue.getBytes(StandardCharsets.US_ASCII)))`, où `cookieValue` est la **chaîne de 43 caractères**
  telle qu'elle figure dans `Set-Cookie` / `Cookie` (SHA-256 des octets ASCII de la chaîne base64url, **pas** des
  32 octets aléatoires décodés) ; hex minuscule, 64 caractères. Au refresh et au logout, même calcul sur la valeur
  reçue, sans décodage base64. Si la requête porte plusieurs cookies `__Secure-refresh`, **seul le premier** (ordre de
  l'en-tête `Cookie`) est lu.
- **RG-09** — Refresh réussi (toutes les étapes de RG-22 franchies) : le jeton présenté est marqué utilisé
  (**atomiquement**), un nouveau jeton est émis dans la **même famille** (nouveau cookie), et un nouveau jeton d'accès
  est émis avec les rôles et le dépôt **actuels** de l'utilisateur.
- **RG-10** — Réutilisation : présenter un jeton **déjà utilisé** d'une famille non révoquée et non expirée révoque
  toute sa famille (raison `REUSE_DETECTED`), y compris le jeton le plus récent ; réponse `401` (RG-11). Deux refresh
  concurrents avec le même jeton : un seul réussit, l'autre est traité comme une réutilisation. Aucun délai de grâce.
- **RG-11** — Expiration : chaque jeton expire à `min(émission + app.auth.refresh.idle-ttl (8 h),
  famille.absolute_expires_at)` ; la famille expire à `login + app.auth.refresh.absolute-ttl` (**24 h**). Un jeton est
  expiré si `expires_at <= now`, une famille si `absolute_expires_at <= now`. Tout refresh refusé (cookie absent,
  jeton inconnu, expiré, réutilisé, famille révoquée ou expirée, utilisateur désactivé, supprimé ou sans rôle) répond
  `401`, `title` `Unauthorized`, `detail` `Invalid or expired session.` et **efface** le cookie (`Max-Age=0`) — même
  réponse dans tous les cas. Mécanisme : `InvalidSessionException` et son gestionnaire dédié (§ 5.7).
- **RG-12** — Logout : si le cookie désigne un jeton connu (quel que soit son état), sa famille est révoquée (raison
  `LOGOUT`, sauf si déjà révoquée : raison inchangée). Réponse `204` et cookie effacé, que le cookie soit présent,
  inconnu ou absent (idempotent). Les autres familles du même utilisateur (autres appareils) ne sont pas touchées.
  Le jeton d'accès déjà émis reste valide jusqu'à son expiration (≤ 5 min, voir § 7.4).
- **RG-13** — Anti-CSRF (ADR 0002 D3) sur `POST /api/v1/auth/refresh` et `POST /api/v1/auth/logout`, vérifié
  **avant** toute lecture du cookie **et avant le filtre CORS** (§ 5.5, `AuthCsrfFilter`) : en-tête `Origin` présent et
  accepté par la configuration CORS résolue pour la requête (`CorsConfiguration.checkOrigin(origin) != null`, origines
  `app.security.cors.allowed-origins` / `allowed-origin-patterns` ; `Origin: null` n'est jamais accepté), et en-tête
  `CorsConfig.CSRF_HEADER` (`X-Requested-With`) présent et non vide (après `trim`). Sinon `403` au format
  `ProblemDetail` (§ 5.7). Seule la méthode `POST` est acceptée sur ces routes.
- **RG-14** — Hachage des mots de passe (ADR 0002 D4/D5) : `poivre = PasswordPepper.pepper(motDePasse)` puis
  Argon2id (`Argon2PasswordEncoder`, paramètres `app.auth.password.*` : sel 16 o, hachage 32 o, p = 1, m = 65 536 Kio,
  t = 3) sur `Pepper.value()`. Format stocké : `p<version de clé de poivre>$<chaîne Argon2 encodée>`
  (ex. `p1$$argon2id$v=19$m=65536,t=3,p=1$…`). Un hachage stocké est **bien formé** s'il correspond à
  `^p([1-9][0-9]{0,8})\$(\$argon2id\$.+)$` ; sinon (`null`, `abc`, `p1$`, `px$…`, `p0$…`, autre algorithme) `verify`
  renvoie `MISMATCH` **sans** appeler le poivre ni lever d'exception ; ce contrôle de format **précède** le sémaphore
  RG-21 (un hachage mal formé ne prend pas de place et ne peut pas donner `503`). Un hachage bien formé dont la version de poivre
  est refusée par `PasswordPepper.pepper(raw, version)` propage la `CryptoException` (→ `500`, cas d'intégrité anormal).
  Appels au poivre de `verify` (hachage bien formé) : `latest = pepper(raw)` (dernière version, **toujours un seul
  appel** dans le cas courant) ; si `latest.keyVersion()` est égale à la version stockée, `latest` est réutilisé ;
  sinon `pepper(raw, versionStockée)` est appelé **en plus** (cas de rotation uniquement) et c'est lui qui sert à
  `matches`. Re-hachage nécessaire (`MATCH_REHASH_NEEDED`) si la version de
  poivre stockée est inférieure à `latest.keyVersion()` **ou** si
  `Argon2PasswordEncoder.upgradeEncoding(chaîne Argon2)` est vrai (mémoire **ou** itérations stockées inférieures aux
  paramètres courants). Après un login réussi avec `MATCH_REHASH_NEEDED`, le service appelle `PasswordHasher.hash(raw)`
  puis enregistre le nouveau hachage (mise à jour conditionnelle, RG-23) ; si `hash` lève une exception (`CryptoException`,
  `RetryLaterException`), elle est journalisée (`PASSWORD_REHASH_FAILED`) et **le login réussit** avec l'ancien hachage
  inchangé. **Décision** : `PASSWORD_REHASHED` est journalisé **seulement** si `updatePasswordHash` renvoie `1`, après
  le commit ; `0` (hachage modifié entre-temps, ex. par 002) → aucun événement de re-hachage, le login réussit.
- **RG-15** — Un utilisateur désactivé (`enabled = false`), supprimé logiquement (RM10) ou sans aucun rôle ne peut ni
  se connecter (RG-03) ni rafraîchir sa session ; au refresh, sa famille est révoquée (raison `USER_DISABLED`, étape 7
  de RG-22).
- **RG-16** — `AuthService.revokeAllSessions(userId, reason)` révoque toutes les familles non révoquées d'un
  utilisateur (pour 002 : changement de mot de passe → `PASSWORD_CHANGED`, désactivation / suppression →
  `USER_DISABLED`, action admin → `ADMIN_REVOKED`). Idempotent ; les familles déjà révoquées gardent leur raison.
  `SESSIONS_REVOKED` est journalisé **après le commit de la transaction physique englobante** (SEC-10, § 5.5) : jamais
  si elle est annulée.
  **Contrainte imposée à la feature 002** (condition de RG-25) : toute opération qui change le mot de passe, désactive
  ou supprime un compte met d'abord à jour `app_user` (`password_hash`, `enabled`, `deleted_at`) **puis** appelle
  `revokeAllSessions`, **dans la même transaction**. Avec l'isolation par défaut de PostgreSQL (`READ COMMITTED`), le
  verrou de ligne posé par la mise à jour de `app_user` sérialise cette opération avec la transaction de succès d'un
  login concurrent (RG-24 étape 5) : soit le login valide le premier et sa famille est vue puis révoquée par
  `revokeAllSessions`, soit 002 valide le premier et `registerSuccessfulLogin` renvoie `0`.
  **Flush garanti côté 001** (mise à jour 6) : 002 peut modifier `app_user` par dirty checking
  (`user.setEnabled(false)`, `setPasswordHash(…)`) ou par `SoftDeleter.delete` (obligatoire pour RM10), qui ne
  produisent l'`UPDATE app_user` qu'au flush. `revokeAllSessions` **envoie en base les modifications en attente de la
  transaction englobante avant la révocation** : `RefreshTokenFamilyRepository.revokeAllActiveByUserId` est déclarée
  `@Modifying(flushAutomatically = true)` (§ 5.4). Le verrou de ligne `app_user` est ainsi posé **avant** la
  révocation des familles, quel que soit le mode d'écriture choisi par 002 (AC-69).
- **RG-17** — Purge : les familles dont `absolute_expires_at` ou `revoked_at` est antérieur à
  `now - app.auth.refresh.purge-retention` (**7 jours**) sont supprimées physiquement avec leurs jetons (données
  techniques, hors RM10). Tâche planifiée `app.auth.refresh.purge-cron` (`0 15 3 * * *`, fuseau
  `TimeConfig.BUSINESS_ZONE`).
- **RG-18** — Seeder de démonstration (profil `seed`, dev uniquement) : comptes `admin` (`ADMIN`), `gestionnaire`
  (`FLEET_MANAGER`, `DRIVER`), `conducteur` (`DRIVER`), actifs, `depot_id = null` (les dépôts arrivent avec 003), mot
  de passe commun lu dans `app.seed.demo-password` (variable `SEED_DEMO_PASSWORD`), haché par `PasswordHasher`.
  `shouldRun()` = `admin` n'existe pas (ne lit pas le mot de passe). `seed()` valide le mot de passe **avant toute
  écriture** : vide, blanc ou de moins de 12 caractères → `IllegalStateException` (message sans la valeur), ce qui fait
  échouer le démarrage en profil `seed` **quand le seeder s'exécute** (si `admin` existe déjà, aucun contrôle).
  **Décision (exception documentée à ADR 0001 D9)** : le mot de passe de démonstration reste une variable
  d'environnement (`SEED_DEMO_PASSWORD` de `.env.dev` → propriété `app.seed.demo-password`), **sans** lecture dans
  `kv/formation` ni nouvelle mécanique KV. Justification : la valeur n'est lue que par `UserSeeder`, qui n'existe
  qu'avec le profil `seed` (dev uniquement, refusé avec `prod`) ; les comptes de démonstration n'existent pas en prod ;
  ce n'est pas un secret applicatif de l'API (aucun écart de code dev / prod sur un chemin de production).
- **RG-19** — `GET /api/v1/auth/me` renvoie l'utilisateur courant tel que porté par le jeton d'accès
  (`CurrentUser` : id, rôles, dépôt), sans accès base.
- **RG-20** — Sur `POST /api/v1/auth/login`, `/refresh` et `/logout`, l'en-tête `Authorization` est **ignoré** : un
  jeton d'accès expiré ou invalide ne provoque pas de `401` (le front peut appeler `refresh` avec un jeton expiré).
  L'exception porte sur la méthode **et** le chemin : sur ces chemins avec une autre méthode, l'en-tête est traité
  normalement (AC-40).
- **RG-21** — Le nombre de hachages / vérifications Argon2 simultanés (`hash`, `verify`, `dummyVerify`) est limité à
  `app.auth.password.max-concurrent-hashes` (**4**) ; une demande qui n'obtient pas de place en
  `app.auth.password.hash-acquire-timeout` (**2 s**) échoue en `503` avec `Retry-After: 1`. La place est prise avant
  l'appel au poivre et rendue après Argon2. Pour `verify`, le contrôle de format du hachage stocké (RG-14) a lieu
  **avant** la prise de place : un hachage mal formé renvoie `MISMATCH` sans toucher au sémaphore.
- **RG-22** — **Ordre des contrôles du refresh** (après le contrôle CSRF RG-13). La première étape qui s'applique
  décide ; les révocations sont des mises à jour conditionnelles `revoked_at IS NULL` (une famille déjà révoquée garde
  sa raison) :
  1. cookie absent ou vide → `401` (aucune écriture) ;
  2. empreinte SHA-256 inconnue → `401` (aucune écriture) ;
  3. famille révoquée (`revoked_at` non null) → `401`, **aucune écriture** (raison conservée, même si le jeton est
     déjà utilisé) ;
  4. famille expirée (`absolute_expires_at <= now`) → `401`, **sans révocation** ;
  5. jeton déjà utilisé (`used_at` non null) → révocation `REUSE_DETECTED` → `401` (même si le jeton est aussi expiré
     ou l'utilisateur désactivé) ;
  6. jeton expiré (`expires_at <= now`) → `401`, **sans révocation** ;
  7. utilisateur désactivé, supprimé logiquement ou sans rôle → révocation `USER_DISABLED` → `401` ;
  8. signature du nouveau jeton d'accès, **hors transaction** (§ 5.5) ; une exception (`CryptoException`) → `500`,
     **aucune écriture** (le jeton présenté reste inutilisé, `used_at` null, et reste utilisable ensuite) ;
  9. consommation atomique `markUsed` = 0 (concurrence) → révocation `REUSE_DETECTED` → `401` ;
  10. insertion du nouveau jeton de rafraîchissement et réponse `200` (RG-09).
  Les étapes 1 à 7 lisent l'état et, en cas de refus, valident leur révocation avant la levée du `401` ; les étapes 9
  et 10 forment une transaction courte distincte (§ 5.5).
- **RG-23** — **Écritures concurrentes** : aucune route d'authentification ne renvoie `409`. Toutes les écritures sur
  des lignes existantes de `app_user`, `refresh_token_family` et `refresh_token` faites par cette feature (échec /
  succès de login, re-hachage, consommation d'un jeton, révocations) passent par des requêtes de mise à jour
  conditionnelles (§ 5.4) qui **n'incrémentent pas** la colonne `version` et ne passent jamais par le dirty checking
  d'une entité gérée. Seules les insertions (`refresh_token_family`, `refresh_token`) utilisent `save`.
- **RG-24** — **Ordre des contrôles du login** (après validation `400` / `415`) :
  1. clé IP bloquée (RG-05) → `429` (non compté, aucune recherche, aucun hachage) ;
  2. normalisation (RG-02) et recherche de l'utilisateur non supprimé ;
  3. inconnu, désactivé, sans rôle ou verrouillé (RG-04) → `dummyVerify`, puis `LOGIN_FAILURE`, puis échec IP
     enregistré → `401` ; si plusieurs causes s'appliquent, `reason` de `LOGIN_FAILURE` suit la priorité `DISABLED` >
     `NO_ROLE` > `LOCKED` (SEC-10) ;
  4. `verify` = `MISMATCH` → mise à jour atomique d'échec (RG-04) validée, puis `LOGIN_FAILURE reason=BAD_PASSWORD`,
     puis `ACCOUNT_LOCKED` si cette mise à jour a posé le verrou, puis échec IP enregistré → `401` (ordre exact au
     § 5.5) ;
  5. `MATCH` / `MATCH_REHASH_NEEDED` → `now` lu **une fois** (`clock.instant()`) ; calcul du nouveau hachage si
     nécessaire (RG-14) ; identifiant de la famille attribué d'avance (`assignId()`, claim `sid`) ; signature du jeton
     d'accès ; **puis seulement** une transaction courte : mise à jour atomique **conditionnelle** de succès
     (`registerSuccessfulLogin`, RG-04 / RG-25) — si elle renvoie `0`, voir RG-25 —, enregistrement du nouveau hachage,
     insertion de la famille et du jeton ; commit ; journalisation (`LOGIN_SUCCESS`, `PASSWORD_REHASHED`) → `200`.
  La validation du corps (`400`, `415`) est faite par Spring **avant** l'appel du service : une requête invalide reçoit
  `400` / `415` même depuis une IP bloquée (le limiteur n'est **pas** un filtre servlet). Une exception de
  `dummyVerify` / `verify` (`CryptoException` → `500`, `RetryLaterException` → `503`) n'enregistre **aucun** échec (ni
  `failed_login_count`, ni IP : `recordFailure` n'est pas appelé). Un échec de signature (étape 5) survient avant toute
  écriture : `500`, aucune famille, `failed_login_count`, `locked_until`, `last_login_at` et `password_hash` inchangés,
  aucun échec IP. Une exception dans la transaction de l'étape 5 l'annule entièrement (même résultat). **Aucune
  transaction ni connexion DB n'est tenue pendant l'attente du sémaphore, le poivre, Argon2 et la signature
  Transit** (étapes 3 à 5) : découpage au § 5.5.
- **RG-25** — **Identifiants modifiés pendant un login** (ajoutée lors de la mise à jour 5). À l'étape 5 de RG-24,
  `registerSuccessfulLogin(id, verifiedHash, now)` est la **première** écriture de la transaction de succès, avec
  `verifiedHash` = la valeur de `password_hash` lue à l'étape 2 (celle qui a été vérifiée, **avant** tout re-hachage).
  Elle ne s'applique que si `password_hash = verifiedHash`, `enabled = true` et `deleted_at IS NULL`. Si elle renvoie
  `0` (mot de passe changé, compte désactivé ou supprimé depuis l'étape 2) : rien n'est écrit (ni famille, ni jeton, ni
  re-hachage, ni `last_login_at`, ni `failed_login_count`, ni effacement du verrou), la transaction est annulée, puis
  (hors transaction) `LOGIN_FAILURE reason=CREDENTIALS_CHANGED` est journalisé, l'échec IP est enregistré
  (`recordFailure`, c'est un `401` de login au sens de RG-05) et la réponse est `401` `Invalid credentials.` identique à
  RG-03, **sans** `Set-Cookie`. Le jeton d'accès déjà signé est abandonné (jamais renvoyé). `failed_login_count` n'est
  pas incrémenté (le mot de passe soumis était correct au moment de la vérification). Le retrait concurrent de tous les
  rôles n'est **pas** couvert par cette condition (§ 7.4).

## 4. Critères d'acceptation

**Conventions des tests** (points d'accroche définis par ce contrat) :

- Origine autorisée de test = valeur placée par le test dans `app.security.cors.allowed-origins`
  (ex. `https://app.example.test`) ; « en-têtes CSRF valides » = `Origin: <origine autorisée>` +
  `X-Requested-With: XMLHttpRequest`.
- Horloge : `Clock` `@Primary` de test, réglable ; `T0` = instant du login, **à la seconde entière** (ex.
  `2026-01-15T08:00:00Z`, aucune fraction) ; tous les instants des tests (`T`, `T0 + 1 h`, `now`…) en sont dérivés par
  ajout de secondes entières, ce qui rend exactes les comparaisons d'égalité avec les colonnes `timestamptz`
  (précision microseconde) et les claims `iat` / `exp`. Tous les composants de la feature (`AuthServiceImpl`,
  `LoginRateLimiter`, `TransitJwtKeyProvider`, décodeur JWT et ses validateurs) utilisent le bean `Clock`.
- **Utilisateurs de test** : `User.builder().username("alice").passwordHash(passwordHasher.hash("<mdp>"))
  .roles(EnumSet.of(...)).depotId(...).build()` puis `UserRepository.save` (les valeurs par défaut `enabled = true`,
  `failedLoginCount = 0`, `roles = HashSet vide` sont garanties par `@Builder.Default`, § 5.3). `PasswordHasher` = bean
  réel (`Argon2PasswordHasher`), poivre `InMemoryPasswordPepper`. Désactivation, verrouillage, ajout de rôle,
  suppression logique (`UPDATE app_user SET deleted_at = …`) et lecture de l'état en base : par SQL (`JdbcTemplate`),
  ou `SoftDeleter.delete` dans une transaction.
- **Limiteur IP** : chaque test appelle `LoginRateLimiter.reset()` en `@BeforeEach` **et** fixe une adresse cliente
  propre (`MockHttpServletRequest#setRemoteAddr`), MockMvc utilisant `127.0.0.1` par défaut.
- **Doubles de test (côté tests uniquement)** : `TestcontainersConfig` fournit, en plus de `InMemoryPasswordPepper`
  (qui doit permettre de simuler une rotation : nouvelle dernière version, anciennes versions encore utilisables), un
  bean `JwtKeyProvider` `InMemoryJwtKeyProvider` (paires P-256 générées en mémoire, `currentKeyId()` = `"1"` au départ,
  méthode de rotation créant la version suivante en gardant les précédentes). Aucune implémentation de
  `JwtKeyProvider` n'est active dans `src/main` avec `app.openbao.enabled=false`. **Conséquence** : `src/main` ne
  fournissant alors ni `PasswordPepper` ni `JwtKeyProvider`, **tout contexte Spring de test** (`@SpringBootTest`, tranches
  `@WebMvcTest` / `@DataJpaTest` de 001 et des features suivantes, tests existants du socle) doit importer
  `TestcontainersConfig` ou fournir lui-même ces deux beans ; sinon il ne démarre pas (échec à classer INFRA / TEST au
  triage, pas CODE). Les tests existants sont à vérifier lors de `/write-tests`.
- **Pas de `@Transactional` sur les tests d'intégration de 001** (ni sur la classe, ni sur les méthodes, ni par une
  méta-annotation) : avec MockMvc (même thread), la transaction du test engloberait la requête et fausserait
  AC-SEC-13, AC-30, AC-49, AC-50, AC-68 et toute journalisation « après commit ». Nettoyage explicite par SQL
  (`JdbcTemplate` : `DELETE FROM refresh_token`, `refresh_token_family`, `app_user_role`, `app_user`) en `@BeforeEach`
  et/ou `@AfterEach`. Un `TransactionTemplate` local reste permis quand un AC le demande (ex. AC-41, AC-69).
- Seeder : `UserSeeder` n'existe qu'avec le profil `seed` ; les tests l'instancient avec son constructeur public
  (§ 5.5) et les beans réels `UserRepository` / `PasswordHasher`.
- Les `ProblemDetail` ont `type` = `about:blank` et `Content-Type: application/problem+json`.

**Login**

- **AC-01** (RG-01, RG-06, RG-08) — *Given* l'utilisateur `alice` actif, rôles `DRIVER` + `FLEET_MANAGER`, dépôt `D`
  *When* `POST /api/v1/auth/login` `{"username":"alice","password":"<bon>"}` *Then* `200`, corps `TokenResponse` :
  `accessToken` non vide (3 segments séparés par `.`), `tokenType` = `"Bearer"`, `expiresIn` = `300`,
  `user.id` = id d'alice, `user.roles` = `["DRIVER","FLEET_MANAGER"]`, `user.depotId` = `D` ; en base : une ligne
  `refresh_token_family` (user_id = alice, `absolute_expires_at` = `T0 + 24 h`, `revoked_at` null) et une ligne
  `refresh_token` (`expires_at` = `T0 + 8 h`, `used_at` null).
- **AC-02** (RG-08) — *Given* AC-01 *Then* exactement un en-tête `Set-Cookie` nommé `__Secure-refresh`, valeur de
  43 caractères `[A-Za-z0-9_-]`, attributs `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/v1/auth`,
  `Max-Age=28800`, sans attribut `Domain` ; le corps JSON ne contient pas cette valeur ; en base `token_hash` =
  SHA-256, en hex minuscule, des octets US-ASCII de la chaîne de 43 caractères du cookie (formule RG-08), et aucune
  colonne ne contient la valeur brute.
- **AC-03** (RG-02) — *Given* `alice` *When* login avec `"username":"  ALICE "` et le bon mot de passe *Then* `200`.
- **AC-04** (RG-03, RG-02) — *When* login avec un identifiant inconnu, puis avec `alice` + mauvais mot de passe, puis
  avec l'identifiant hors format `"alice@example"` (et un mot de passe quelconque) *Then* les trois réponses : `401`
  (jamais `400`), `title` = `Unauthorized`, `detail` = `Invalid credentials.`, aucun `Set-Cookie`, corps identiques
  (mêmes clés et valeurs).
- **AC-05** (RG-03, RG-15) — *Given* `bob` avec `enabled = false`, et `carol` supprimée logiquement (`deleted_at`
  renseigné) *When* login avec leur bon mot de passe *Then* `401` identique à AC-04 ; aucune famille créée.
- **AC-06** (RG-04) — *Given* `alice` (`failed_login_count` = 0) *When* 4 logins avec mauvais mot de passe *Then*
  4 × `401` et `failed_login_count` = 4, `locked_until` null ; *When* 5ᵉ échec à `T` *Then* `401`,
  `failed_login_count` = 0, `locked_until` = `T + 15 min`.
- **AC-07** (RG-04) — *Given* `alice` verrouillée à `T` *When* login avec le **bon** mot de passe à `T + 14 min 59 s`
  *Then* `401` identique à AC-04, `locked_until` inchangé, `failed_login_count` = 0 ; *When* même login à `T + 15 min`
  *Then* `200`, `locked_until` null.
- **AC-08** (RG-04) — *Given* `alice` avec `failed_login_count` = 3 *When* login réussi *Then* `200`,
  `failed_login_count` = 0, `last_login_at` = `now`.
- **AC-09** (RG-04) — *Given* `alice` connectée (cookie `C`) puis verrouillée par 5 échecs *When*
  `POST /api/v1/auth/refresh` avec `C` et en-têtes CSRF valides *Then* `200`.
- **AC-10** (RG-05) — *Given* l'horloge à `T0` et 20 logins échoués depuis l'IP `203.0.113.10` avec des identifiants
  **inconnus** (ex. `unknown-01` … `unknown-20`, pour ne verrouiller aucun compte) *When* à `T0 + 60 s`, 21ᵉ login
  depuis cette IP **avec les identifiants corrects d'alice** *Then* `429`, `title` = `Too Many Requests`, `detail` =
  `Too many failed login attempts. Retry later.`, en-tête `Retry-After: 240` ; aucun `Set-Cookie` ; *And* un login
  depuis l'IP `203.0.113.11` avec les identifiants d'alice → `200`.
- **AC-11** (RG-05) — *Given* AC-10 (premier échec à `T0`) *When* login correct depuis `203.0.113.10` à
  `T0 + 4 min 59 s` *Then* `429` avec `Retry-After: 1` ; *When* le même login à exactement `T0 + 5 min` *Then* `200`.
- **AC-12** (RG-05) — *Given* 25 requêtes de login invalides (`400`, ex. `username` vide) depuis une IP *When* un
  login correct depuis cette IP *Then* `200`.
- **AC-13** (validation) — *When* `POST /api/v1/auth/login` avec `username` absent, vide ou > 100 caractères, ou
  `password` absent, vide ou > 128 caractères *Then* `400`, `title` = `Validation Failed`, `errors[].field` =
  `username` / `password` ; le corps ne contient pas la valeur soumise.
- **AC-14** (validation) — *When* login avec un champ inconnu (`{"username":"alice","password":"…","role":"ADMIN"}`)
  *Then* `400`. *When* login avec `Content-Type: text/plain` *Then* `415`.

**Hachage**

- **AC-15** (RG-14) — *Given* un utilisateur créé avec `PasswordHasher.hash("<mdp>")` *Then* `password_hash` correspond
  à `^p\d+\$\$argon2id\$v=19\$m=65536,t=3,p=1\$.+\$.+$` (avec les paramètres par défaut) et ne contient pas le mot de
  passe ; deux appels `hash` avec le même mot de passe donnent deux valeurs différentes (sel).
- **AC-16** (RG-14) — *Given* le hachage d'alice stocké avec la version de poivre 1, puis rotation simulée de
  `InMemoryPasswordPepper` (dernière version = 2) *When* login avec le bon mot de passe *Then* `200` et
  `password_hash` commence désormais par `p2$` ; un second login réussit encore.
- **AC-17** (RG-14) — *Given* le hachage d'alice calculé avec des paramètres Argon2 inférieurs (ex. `m=19456,t=2`)
  *When* login réussi avec les paramètres courants par défaut *Then* `password_hash` contient `m=65536,t=3`.
  *Given* un hachage déjà à jour *When* login réussi *Then* `password_hash` inchangé.
- **AC-18** (RG-14) — `PasswordHasher.verify` (test unitaire ou IT) : bon mot de passe → `MATCH` ; mauvais →
  `MISMATCH` ; hachage mal formé (`"abc"`, `"p1$"`, `"px$…"`, `"p0$$argon2id$…"`, `""`, `null`) → `MISMATCH` sans
  exception ; hachage bien formé `p99$` + chaîne Argon2id valide alors que le poivre ne connaît pas la version 99 →
  `CryptoException` propagée.

**Jeton d'accès**

- **AC-19** (RG-06) — *Given* AC-01 *Then* l'en-tête JWS décodé vaut `alg` = `ES256`, `typ` = `JWT`, `kid` = version
  courante de la clé de signature (`JwtKeyProvider.currentKeyId()`) ; la charge utile contient `iss` =
  `app.auth.jwt.issuer`, `aud` = `app.auth.jwt.audience`, `sub` = id d'alice, `iat` = `T0` (secondes epoch),
  `exp` = `T0 + 300`, `jti` (UUID), `sid` = id de la famille créée, `roles` = `["DRIVER","FLEET_MANAGER"]`,
  `depot_id` = `D` ; la signature (64 octets base64url) se vérifie avec `SHA256withECDSAinP1363Format` et la clé
  publique `JwtKeyProvider.publicKey(kid)`. Pour un utilisateur sans dépôt, la claim `depot_id` est absente.
- **AC-20** (RG-07, RG-19) — *Given* le jeton d'accès d'AC-01 *When* `GET /api/v1/auth/me` avec
  `Authorization: Bearer <jeton>` *Then* `200`, corps `{"id":"<alice>","roles":["DRIVER","FLEET_MANAGER"],
  "depotId":"<D>"}` ; pour un utilisateur sans dépôt, `"depotId":null`.
- **AC-21** (RG-07) — *When* `GET /api/v1/auth/me` sans en-tête `Authorization` *Then* `401`, `title` =
  `Unauthorized`.
- **AC-22** (RG-07) — *When* `GET /api/v1/auth/me` avec un jeton d'AC-01 et l'horloge à `T0 + 300 s` *Then* `401`,
  `title` = `Unauthorized` ; à `T0 + 299 s` → `200`.
- **AC-23** (RG-07) — *When* `GET /api/v1/auth/me` avec, chacun séparément, un jeton : (a) charge utile modifiée
  après signature (ex. `roles` → `["ADMIN"]`) ; (b) `alg` = `none` sans signature ; (c) `alg` = `HS256` signé avec
  des octets quelconques ; (d) `kid` inconnu ; (e) `iss` différent ; (f) `aud` différent ; (g) `exp` absent ;
  (h) `sub` qui n'est pas un UUID ; (i) `roles` vide, absent ou contenant `"SUPERUSER"` ; (j) signé ES256 par une
  autre clé P-256 avec le `kid` courant ; (k) `iat` > `now` *Then* `401` dans chaque cas (jamais `500`).
- **AC-24** (RG-07) — *When* `GET /api/v1/auth/me?access_token=<jeton valide>` sans en-tête `Authorization`
  *Then* `401`.
- **AC-25** (RG-07, RG-06) — *Given* rotation de `InMemoryJwtKeyProvider` après l'émission d'un jeton (le jeton porte
  l'ancien `kid`, version encore disponible) *When* `GET /api/v1/auth/me` avec ce jeton *Then* `200` ; les jetons émis
  ensuite portent le nouveau `kid`.
- **AC-26** (RG-20) — *Given* un cookie de refresh valide et un jeton d'accès expiré ou altéré *When*
  `POST /api/v1/auth/refresh` avec ce jeton dans `Authorization`, le cookie et des en-têtes CSRF valides *Then*
  `200`. Idem `POST /api/v1/auth/login` avec un `Authorization` invalide et des identifiants corrects → `200`.

**Refresh**

- **AC-27** (RG-09) — *Given* login à `T0` (cookie `A`) *When* à `T0 + 1 h`, `POST /api/v1/auth/refresh` (sans
  corps) avec `A` et en-têtes CSRF valides *Then* `200`, corps `TokenResponse` (nouveau `accessToken`, `iat` =
  `T0 + 1 h`), nouveau cookie `__Secure-refresh` = `B` ≠ `A`, `Max-Age=28800` ; en base : le jeton `A` a `used_at` =
  `T0 + 1 h`, le jeton `B` appartient à la même famille avec `expires_at` = `T0 + 9 h`.
- **AC-28** (RG-09) — *Given* login d'alice (`DRIVER`) puis, en base, ajout du rôle `ADMIN` et changement de dépôt
  *When* refresh *Then* le nouveau jeton d'accès et `user` portent `roles` = `["DRIVER","ADMIN"]` et le nouveau dépôt.
- **AC-29** (RG-10) — *Given* login (cookie `A`), refresh avec `A` (cookie `B`) *When* refresh avec `A` *Then* `401`
  (RG-11) et la famille a `revoked_at` = `now`, `revocation_reason` = `REUSE_DETECTED` ; *When* ensuite refresh avec
  `B` *Then* `401`.
- **AC-30** (RG-10) — *Given* un jeton `A` *When* deux refresh simultanés avec `A` *Then* exactement un `200` et un
  `401` (jamais `409`), et la famille est révoquée (`REUSE_DETECTED`).
- **AC-31** (RG-11) — *Given* login à `T0` (cookie `A`) *When* refresh avec `A` à `T0 + 8 h - 1 s` *Then* `200` ;
  *Given* un autre login à `T0` (cookie `A'`) *When* refresh avec `A'` à `T0 + 8 h` *Then* `401`.
- **AC-32** (RG-11) — *Given* login à `T0`, puis refresh réussis à `T0 + 7 h`, `T0 + 14 h`, `T0 + 21 h` *Then* le
  cookie émis à `T0 + 21 h` a `Max-Age=10800` et le jeton `expires_at` = `T0 + 24 h` ; *When* refresh à `T0 + 24 h`
  *Then* `401`.
- **AC-33** (RG-11) — *When* refresh avec en-têtes CSRF valides et : (a) sans cookie ; (b) cookie de valeur inconnue ;
  (c) cookie d'une famille révoquée par logout *Then* `401`, `title` = `Unauthorized`, `detail` =
  `Invalid or expired session.`, corps identiques entre les cas, et `Set-Cookie: __Secure-refresh=` avec `Max-Age=0`,
  `Path=/api/v1/auth`, `HttpOnly`, `Secure`, `SameSite=Strict`.
- **AC-34** (RG-15) — *Given* alice connectée (cookie `A`) puis `enabled = false` en base *When* refresh avec `A`
  *Then* `401` (AC-33) et la famille a `revocation_reason` = `USER_DISABLED`. Idem si alice est supprimée logiquement.

**Logout**

- **AC-35** (RG-12) — *Given* alice connectée deux fois (cookies `A` et `X`, deux familles) *When*
  `POST /api/v1/auth/logout` avec `A` et en-têtes CSRF valides *Then* `204`, corps vide, `Set-Cookie` effaçant
  `__Secure-refresh` (`Max-Age=0`, `Path=/api/v1/auth`) ; la famille de `A` a `revocation_reason` = `LOGOUT` ;
  refresh avec `A` → `401` ; refresh avec `X` → `200`.
- **AC-36** (RG-12) — *When* logout avec en-têtes CSRF valides sans cookie, ou avec un cookie inconnu *Then* `204` et
  cookie effacé. *When* logout avec un cookie d'une famille déjà révoquée pour `REUSE_DETECTED` *Then* `204` et la
  raison reste `REUSE_DETECTED`.

**Anti-CSRF**

- **AC-37** (RG-13) — *Given* un cookie valide *When* `POST /api/v1/auth/refresh` (resp. `/logout`) sans en-tête
  `X-Requested-With` (ou vide) mais avec `Origin` autorisée *Then* `403`, `title` = `Forbidden`, `detail` =
  `Cross-site request rejected.` ; aucun `Set-Cookie` ; le jeton n'est pas consommé (`used_at` null) / la famille
  n'est pas révoquée.
- **AC-38** (RG-13) — *Given* un cookie valide *When* refresh (resp. logout) avec `X-Requested-With` mais sans
  `Origin`, ou avec `Origin: null` *Then* `403`, `title` = `Forbidden`, `detail` = `Cross-site request rejected.`,
  `Content-Type: application/problem+json`, jeton non consommé.
- **AC-39** (RG-13) — *When* refresh (resp. logout) avec `X-Requested-With` et `Origin: https://evil.example`
  (non autorisée) *Then* `403`, `title` = `Forbidden`, `detail` = `Cross-site request rejected.` (rejet par
  `AuthCsrfFilter`, placé avant le filtre CORS), jeton non consommé.
- **AC-40** (RG-13) — *When* `GET /api/v1/auth/refresh` (resp. `/logout`, `/login`) sans `Authorization` *Then*
  `401` ; avec un jeton d'accès valide *Then* `405`.

**Révocation globale, purge, seed**

- **AC-41** (RG-16) — *Given* alice avec 2 familles actives et 1 famille révoquée `LOGOUT` *When*
  `AuthService.revokeAllSessions(alice, PASSWORD_CHANGED)` *Then* les 2 familles actives ont `revocation_reason` =
  `PASSWORD_CHANGED`, la troisième reste `LOGOUT` ; les refresh avec leurs cookies → `401` ; un second appel ne
  modifie rien. *And* (log capturé) le premier appel produit une ligne `event=SESSIONS_REVOKED userId=<alice> ip=-`
  avec `reason=PASSWORD_CHANGED` et `count=2`. *Given* alice avec 1 famille active *When* `revokeAllSessions(alice,
  USER_DISABLED)` appelé dans un `TransactionTemplate` dont le callback appelle ensuite `status.setRollbackOnly()`
  *Then* la famille n'est pas révoquée et la sortie de log ne contient **aucune** ligne `event=SESSIONS_REVOKED`
  (journalisation après commit de la transaction englobante, SEC-10).
- **AC-42** (RG-17) — *Given* à `now` : famille F1 `absolute_expires_at` = `now - 8 j` ; F2 révoquée à `now - 8 j`
  (non expirée) ; F3 `absolute_expires_at` = `now - 6 j` ; F4 active *When* `AuthService.purgeExpiredSessions()`
  *Then* renvoie `2`, F1 et F2 et tous leurs `refresh_token` sont supprimés, F3 et F4 restent.
- **AC-43** (RG-18) — *Given* aucun utilisateur `admin` en base et `new UserSeeder(userRepository, passwordHasher,
  "Demo-Password-2026")` *When* `shouldRun()` puis `seed()` *Then* `shouldRun()` = `true` ; les utilisateurs `admin`
  (`ADMIN`), `gestionnaire` (`DRIVER`, `FLEET_MANAGER`), `conducteur` (`DRIVER`) existent, actifs, `depot_id` null ;
  un login `admin` / `Demo-Password-2026` → `200` ; un second `shouldRun()` → `false`.
- **AC-44** (RG-18) — *Given* aucun `admin` *When* `seed()` d'un `UserSeeder` construit avec `""`, `"   "` ou
  `"elevenchars"` (11 caractères) *Then* `IllegalStateException` dont le message ne contient pas la valeur, et
  aucun utilisateur créé. *Given* aucun `admin` *When* `seed()` d'un seeder construit avec `"twelve-chars"`
  (12 caractères, borne) *Then* pas d'exception et les trois comptes existent. *Given* `admin` existant *When* `shouldRun()` d'un seeder construit avec `""` *Then* `false`,
  sans exception.

**Concurrence de hachage**

- **AC-45** (RG-21) — Test unitaire de `Argon2PasswordHasher` construit avec `max-concurrent-hashes = 1`,
  `hash-acquire-timeout = 100 ms` et un `PasswordPepper` simulé qui bloque au premier appel : pendant que le thread A
  est bloqué dans `hash`, un appel `hash`, ou `verify(raw, h)` avec un hachage `h` **bien formé** (obtenu par un appel
  `hash` préalable, avant le blocage du poivre), ou `dummyVerify`, du thread B lève `RetryLaterException` de
  statut `503` et `retryAfter` = 1 s ; une fois A débloqué, un nouvel appel réussit. En HTTP, cette exception donne
  `503`, `title` = `Service Unavailable`, `detail` = `The service is busy. Retry later.`, `Retry-After: 1`.

**Ordre du refresh, concurrence, cas limites** (ajoutés lors de la mise à jour 2)

- **AC-46** (RG-22) — *Given* login (cookie `A`), refresh avec `A` (cookie `B`), puis logout avec `B` (famille
  révoquée `LOGOUT`) *When* refresh avec `A` (jeton déjà utilisé) *Then* `401` (AC-33) et `revocation_reason` reste
  `LOGOUT`, `revoked_at` inchangé.
- **AC-47** (RG-22) — *Given* login à `T0` (cookie `A`) puis `enabled = false` en base *When* refresh avec `A` à
  `T0 + 8 h` (jeton expiré) *Then* `401` et la famille a `revoked_at` null. *Given* la séquence d'AC-32 (cookie `D`
  émis à `T0 + 21 h`) *When* refresh avec `D` à `T0 + 24 h` (famille expirée) *Then* `401` et `revoked_at` null.
  *Given* la séquence d'AC-32 *When* refresh à `T0 + 24 h` avec le cookie émis à `T0 + 14 h` (**déjà utilisé** à
  `T0 + 21 h`, famille expirée) *Then* `401` et `revoked_at` null (l'étape 4 précède l'étape 5).
- **AC-48** (RG-22) — *Given* login (cookie `A`), refresh avec `A` (cookie `B`), puis `enabled = false` en base
  *When* refresh avec `A` *Then* `401` et `revocation_reason` = `REUSE_DETECTED` (l'étape 5 précède l'étape 7).
- **AC-49** (RG-23, RG-04, SEC-10) — Configuration du test (surcharge de propriétés) :
  `app.auth.password.max-concurrent-hashes=10`, `memory-kib=19456`, `iterations=2` (aucun `503` possible).
  *Given* `alice` (`failed_login_count` = 0) *When* 10 logins simultanés (threads parallèles, IP distinctes) avec un
  mauvais mot de passe *Then* 10 × `401` (aucun `409`, `500` ni `503`), en base `failed_login_count` = 0,
  `locked_until` = `now + 15 min`, et la sortie de log capturée contient **exactement une** ligne
  `event=ACCOUNT_LOCKED`.
- **AC-50** (RG-23) — *Given* alice connectée (cookie `A`) *When* `POST /logout` et `POST /refresh` simultanés avec
  `A` (en-têtes CSRF valides) *Then* logout → `204`, refresh → `200` ou `401` (jamais `409`) ; ensuite la famille est
  révoquée (`LOGOUT`) et un refresh avec `A` ou avec le cookie éventuellement émis → `401`.
- **AC-51** (RG-23) — *Given* alice avec `app_user.version` = `v` et un hachage en poivre version 1 *When* un login
  échoué, puis (après rotation du poivre) un login réussi avec re-hachage, puis un refresh et un logout *Then*
  `app_user.version` vaut toujours `v`, et les colonnes `version` de la famille et des jetons concernés sont inchangées
  depuis leur insertion.
- **AC-52** (RG-04) — *Given* `alice` verrouillée à `T` (`locked_until` = `T + 15 min`) *When* login avec un mauvais
  mot de passe à `T + 16 min` *Then* `401`, `failed_login_count` = 1, `locked_until` null.
- **AC-53** (RG-05) — Test unitaire de `LoginRateLimiter` (construit avec les valeurs par défaut et une horloge
  figée) : après 20 `recordFailure("2001:db8::1")`, `blockedFor("2001:db8::ffff")` est présent (même /64) et
  `blockedFor("2001:db8:0:1::1")` est vide ; après 20 `recordFailure("::ffff:203.0.113.20")`,
  `blockedFor("203.0.113.20")` est présent.
- **AC-54** (RG-05) — Test unitaire de `LoginRateLimiter` avec `ip-max-failures = 1` et `ip-max-tracked = 2` :
  `recordFailure("198.51.100.1")` à `T`, `recordFailure("198.51.100.2")` à `T + 1 s`, `recordFailure("198.51.100.3")`
  à `T + 2 s` *Then* `blockedFor("198.51.100.1")` vide (évincée), `blockedFor("198.51.100.2")` et
  `blockedFor("198.51.100.3")` présents ; après `reset()`, les trois sont vides.
- **AC-55** (RG-14) — *Given* le hachage d'alice en poivre version 1, rotation du poivre (version 2) et
  `@MockitoSpyBean PasswordHasher` dont `hash` lève `CryptoException` *When* login avec le bon mot de passe *Then*
  `200` et `password_hash` inchangé (commence toujours par `p1$`).
- **AC-56** (RG-03, RG-15) — *Given* `dave` actif, bon mot de passe, **sans aucun rôle** (`app_user_role` vide)
  *When* login *Then* `401` identique à AC-04, aucune famille créée. *Given* alice connectée (cookie `A`) puis tous
  ses rôles supprimés en base *When* refresh avec `A` *Then* `401` et `revocation_reason` = `USER_DISABLED`.
- **AC-57** (RG-06) — `TransitJwtKeyProviderIT` (OpenBao en conteneur, comme `TransitPasswordPepperIT` : moteur
  Transit, clé `jwt` de type `ecdsa-p256`), `TransitJwtKeyProvider` construit avec son constructeur public et une
  horloge réglable : `sign(currentKeyId(), input)` renvoie 64 octets qui se vérifient avec
  `SHA256withECDSAinP1363Format` et `publicKey(currentKeyId())` ; `publicKey("999")` est vide ; après
  `transit/keys/jwt/rotate` et avance de l'horloge au-delà de `key-cache-refresh-interval`, `publicKey(<nouvelle
  version>)` est encore **vide** (`publicKey` ne recharge jamais le cache) ; puis `currentKeyId()` vaut la nouvelle
  version, `publicKey(<nouvelle version>)` est présent, et une signature faite avec l'ancien `kid` se vérifie encore
  avec `publicKey(ancien kid)`.
- **AC-58** (RG-08) — *Given* un cookie valide `A` *When* refresh avec l'en-tête `Cookie: __Secure-refresh=<A>;
  __Secure-refresh=<inconnu>` et en-têtes CSRF valides *Then* `200` ; *When* avec un autre cookie valide `A2` et
  l'ordre inverse (`<inconnu>` puis `<A2>`) *Then* `401` et `A2` non consommé.
- **AC-59** (RG-05) — *Given* une IP bloquée (AC-10) *When* login avec `Origin: <origine autorisée>` *Then* `429` et
  l'en-tête `Access-Control-Expose-Headers` contient `Retry-After`.

**Ajouté lors de la mise à jour 3**

- **AC-60** (RG-07, SEC-12) — *Given* le jeton d'accès d'AC-01 et `@MockitoSpyBean JwtKeyProvider` dont `publicKey`
  lève `CryptoException` *When* `GET /api/v1/auth/me` avec `Authorization: Bearer <jeton>` *Then* `401`,
  `Content-Type: application/problem+json`, `title` = `Unauthorized` (jamais `500`, jamais le corps `/error` de Spring
  Boot).

**Ajouté lors de la mise à jour 4**

Observation des échecs IP (AC-62, AC-64, AC-SEC-12) : `@MockitoSpyBean LoginRateLimiter`, puis
`verify(limiter, never()).recordFailure(any())`.

- **AC-61** (RG-24, RG-05) — *Given* l'IP `203.0.113.30` bloquée (20 échecs avec identifiants inconnus, comme AC-10)
  *When* login depuis cette IP avec `{"username":"","password":"x"}` *Then* `400`, `title` = `Validation Failed`
  (pas `429`) ; *When* login depuis cette IP avec `Content-Type: text/plain` *Then* `415` ; *When* ensuite login
  correct d'alice depuis cette IP *Then* `429` (la validation passe avant le contrôle IP, et les `400` / `415` ne
  débloquent ni ne comptent rien).
- **AC-62** (RG-24, RG-04, RG-14) — *Given* alice avec `failed_login_count` = 3, `last_login_at` null, hachage en
  poivre version 1 (valeur `H1`), rotation du poivre (dernière version = 2), et `@MockitoSpyBean JwtKeyProvider` dont
  `sign` lève `CryptoException` *When* login avec le bon mot de passe *Then* `500`, `title` = `Internal Server Error`,
  aucun `Set-Cookie` ; en base : `failed_login_count` = 3, `last_login_at` null, `password_hash` = `H1` (commence
  toujours par `p1$`), aucune ligne `refresh_token_family` ni `refresh_token` pour alice ; `recordFailure` jamais
  appelé. *When* `sign` rétabli (spy réinitialisé), même login *Then* `200` et `password_hash` commence par `p2$`.
- **AC-63** (RG-22, RG-09) — *Given* alice connectée (cookie `A`) et `@MockitoSpyBean JwtKeyProvider` dont `sign`
  lève `CryptoException` *When* `POST /api/v1/auth/refresh` avec `A` et en-têtes CSRF valides *Then* `500`, aucun
  en-tête `Set-Cookie`, en base `used_at` de `A` null, famille non révoquée, un seul `refresh_token` dans la famille ;
  *When* `sign` rétabli, refresh avec `A` *Then* `200`.
- **AC-64** (RG-24, RG-04) — *Given* alice (`failed_login_count` = 0) et un `PasswordPepper` (`@MockitoBean` ou
  `@MockitoSpyBean`) dont `pepper(…)` lève `CryptoException` *When* login d'alice avec un **mauvais** mot de passe
  *Then* `500`, en base `failed_login_count` = 0 et `locked_until` null, `recordFailure` jamais appelé. *When* le même
  login avec un `pepper(…)` qui lève `RetryLaterException` (`503`) *Then* `503`, mêmes constats.
- **AC-65** (RG-05) — *Given* l'horloge à `T0`, depuis l'IP `203.0.113.40` : 19 logins échoués avec des identifiants
  inconnus, puis 1 login **réussi** d'alice (`200`), puis 1 login échoué (identifiant inconnu, `401`), tous avant
  `T0 + 5 min` *When* un nouveau login correct d'alice depuis cette IP *Then* `429` (le succès n'a pas remis le compteur
  à zéro : 20 échecs dans la fenêtre).
- **AC-66** (RG-05, SEC-13, PRE-01) — Test d'intégration **avec Tomcat réel** (`@SpringBootTest(webEnvironment =
  RANDOM_PORT)`, client HTTP vers `http://127.0.0.1:<port>`, et non MockMvc qui ne passe pas par `RemoteIpValve`),
  propriétés surchargées : `server.forward-headers-strategy=native`,
  `server.tomcat.remoteip.internal-proxies=127\.0\.0\.1|0:0:0:0:0:0:0:1`,
  `server.tomcat.remoteip.remote-ip-header=x-forwarded-for`. Chaque requête porte l'en-tête au format produit par le
  WAF (`X-Forwarded-For: <client>, <client>`, § 5.9).
  *Given* 20 logins échoués (identifiants inconnus) avec `X-Forwarded-For: 198.51.100.7, 198.51.100.7` *When* login
  correct d'alice avec `X-Forwarded-For: 198.51.100.8, 198.51.100.8` *Then* `200` ; *When* le même login avec
  `X-Forwarded-For: 198.51.100.7, 198.51.100.7` *Then* `429` ; *And* la sortie de log capturée contient
  `event=LOGIN_FAILURE` avec `ip=198.51.100.7` et aucune ligne `event=LOGIN_FAILURE` avec `ip=127.0.0.1`.
- **AC-67** (SEC-13) — Même montage qu'AC-66 mais `server.tomcat.remoteip.internal-proxies=10\.255\.255\.255`
  (le client de test n'est **pas** un proxy de confiance) *Given* 20 logins échoués (identifiants inconnus), chacun
  avec un `X-Forwarded-For` différent (`198.51.100.101` … `198.51.100.120`) *When* login correct d'alice avec
  `X-Forwarded-For: 198.51.100.200` *Then* `429` (l'en-tête est ignoré : tout est compté sur `127.0.0.1`) ; les lignes
  `event=LOGIN_FAILURE` contiennent `ip=127.0.0.1`.

**Ajouté lors de la mise à jour 5**

- **AC-68** (RG-25, RG-04, RG-16, SEC-07, SEC-10) — *Given* alice (`failed_login_count` = 2, `last_login_at` null,
  hachage `H1` = `passwordHasher.hash("<bon>")`), une valeur `H2` = `passwordHasher.hash("<autre>")` calculée
  d'avance, `@MockitoSpyBean PasswordHasher` dont `verify` appelle la méthode réelle puis, avant de rendre la main,
  exécute par `JdbcTemplate` (auto-commit) l'une des mises à jour suivantes, et `@MockitoSpyBean LoginRateLimiter`
  *When* `POST /api/v1/auth/login` avec le bon mot de passe d'alice, pour chaque cas séparément :
  (a) `UPDATE app_user SET password_hash = '<H2>' WHERE id = <alice>` ;
  (b) `UPDATE app_user SET enabled = false WHERE id = <alice>` ;
  (c) `UPDATE app_user SET deleted_at = now() WHERE id = <alice>`
  *Then* dans chaque cas : `401` dont le corps est identique à celui d'AC-04 (`title` `Unauthorized`, `detail`
  `Invalid credentials.`), aucun `Set-Cookie` ; en base aucune ligne `refresh_token_family` ni `refresh_token` pour
  alice, `last_login_at` null, `failed_login_count` = 2, et `password_hash` = `H2` dans le cas (a), `H1` dans les cas
  (b) et (c) ; `recordFailure` appelé exactement une fois ; la sortie de log capturée contient
  `event=LOGIN_FAILURE userId=<alice>` avec `reason=CREDENTIALS_CHANGED` et ne contient pas `event=LOGIN_SUCCESS`.
  *And* (témoin) sans mise à jour dans `verify`, le même login → `200`. *And* (cas (a) suivi de) un login avec
  `<autre>` → `200`.

**Ajouté lors de la mise à jour 6**

- **AC-69** (RG-16, RG-25) — *Given* alice active (`enabled = true`, `deleted_at` null) avec une famille active
  (login réussi, cookie `A`) *When*, dans un `TransactionTemplate` (bean injecté), pour chaque cas séparément :
  `User u = userRepository.findById(alice).orElseThrow()` ; puis (a) `u.setEnabled(false)`, resp. (b)
  `softDeleter.delete(u)` (`framework.entity.SoftDeleter`), **sans** appel à `flush` ; puis
  `jdbcTemplate.queryForObject("select enabled from app_user where id = ?", Boolean.class, alice)` (resp.
  `"select deleted_at from app_user where id = ?"`) ; puis `authService.revokeAllSessions(alice, USER_DISABLED)` ;
  puis, **dans la même transaction**, la même requête `JdbcTemplate` *Then* la première lecture renvoie `true`
  (resp. `null`) — aucune écriture encore émise — et la seconde renvoie `false` (resp. une date non nulle) :
  l'`UPDATE app_user` a été émis par `revokeAllSessions`, avant la révocation ; après le commit, la famille a
  `revocation_reason` = `USER_DISABLED` et un refresh avec `A` → `401`.

## 5. Contrat technique

### 5.1 Endpoints (`be.bstorm.formation.controller.AuthController`, `@RequestMapping("/api/v1/auth")`)

| Méthode | Route | Requête | Réponse succès | Erreurs |
|---|---|---|---|---|
| POST | `/api/v1/auth/login` | `LoginRequest` (JSON) | `200` `TokenResponse` + `Set-Cookie __Secure-refresh` | 400, 401, 415, 429, 500, 503 |
| POST | `/api/v1/auth/refresh` | aucun corps ; cookie `__Secure-refresh` ; en-têtes `Origin`, `X-Requested-With` | `200` `TokenResponse` + `Set-Cookie __Secure-refresh` (nouveau) | 401 (+ cookie effacé), 403, 500 |
| POST | `/api/v1/auth/logout` | aucun corps ; cookie ; `Origin`, `X-Requested-With` | `204` + cookie effacé | 403, 500 (base indisponible) |
| GET | `/api/v1/auth/me` | `Authorization: Bearer` | `200` `CurrentUserResponse` | 401 |
| autre méthode (ex. `GET`) | `/api/v1/auth/login`, `/refresh`, `/logout` | — | — | 401 sans jeton valide (refus par défaut) ; 405 avec un jeton valide (AC-40) |

Aucune de ces routes ne renvoie `409` (RG-23).

**Comportement existant, hors contrat** : une requête vers `POST /api/v1/auth/login` ou `GET /api/v1/auth/me` portant
un en-tête `Origin` non autorisé est rejetée par le `CorsFilter` existant (`CorsConfig`) : `403` avec un corps texte
`Invalid CORS request`, **pas** un `ProblemDetail`. Ce cas n'est **ni testé ni modifié** par 001 (aucun test, aucun
correctif). Seules `/refresh` et `/logout` passent d'abord par `AuthCsrfFilter` (RG-13, `403` `ProblemDetail`).

Accès (`SecurityConfig`) : `POST` sur les trois premières routes = `permitAll` ; `GET /api/v1/auth/me` et tout le
reste = authentifié (refus par défaut inchangé). L'adresse IP passée au service est `HttpServletRequest#getRemoteAddr()`.
Le cookie est lu par `@CookieValue(name = AuthCookies.REFRESH_COOKIE, required = false)` (premier cookie de ce nom,
RG-08).

Cookies — classe utilitaire `be.bstorm.formation.framework.security.AuthCookies` (utilisée par le contrôleur et par
`GlobalExceptionHandler`) :

```java
package be.bstorm.formation.framework.security;

public final class AuthCookies {
    public static final String REFRESH_COOKIE = "__Secure-refresh";
    public static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    /** __Secure-refresh=<jeton>; Path=/api/v1/auth; Max-Age=<ttl en secondes, arrondi inférieur>; Secure; HttpOnly; SameSite=Strict */
    public static ResponseCookie issued(String refreshToken, Duration ttl);

    /** __Secure-refresh=; Path=/api/v1/auth; Max-Age=0; Secure; HttpOnly; SameSite=Strict (Expires passé toléré) */
    public static ResponseCookie cleared();
}
```

Constantes conservées sur le contrôleur (alias) : `AuthController.REFRESH_COOKIE = AuthCookies.REFRESH_COOKIE`,
`AuthController.REFRESH_COOKIE_PATH = AuthCookies.REFRESH_COOKIE_PATH`.

CORS : `app.security.cors.exposed-headers` passe à `Location,Retry-After` dans `application.yml` (s'applique à toutes
les configurations, dont `AUTH_PATHS`) pour que le front lise `Retry-After` sur `429` / `503`.

### 5.2 DTO (`be.bstorm.formation.dto.request` / `.response`)

```java
package be.bstorm.formation.dto.request;

public record LoginRequest(
    @NotBlank @Size(max = 100) String username,   // aucun @Pattern : un identifiant hors format → 401 (RG-02)
    @NotBlank @Size(max = 128) String password
) {
    @Override public String toString() { return "LoginRequest[username=***, password=***]"; }
}
```

```java
package be.bstorm.formation.dto.response;

/** Jamais de jeton de rafraîchissement ici (cookie uniquement). */
public record TokenResponse(
    String accessToken,          // JWT compact
    String tokenType,            // toujours "Bearer"
    long expiresIn,              // secondes (300 par défaut)
    CurrentUserResponse user
) {
    @Override public String toString() { return "TokenResponse[accessToken=***, …]"; } // jeton masqué
}

public record CurrentUserResponse(
    UUID id,
    List<Role> roles,            // be.bstorm.formation.framework.security.Role, ordre de déclaration de l'enum
    UUID depotId                 // null possible, sérialisé "depotId": null
) {}
```

### 5.3 Entités (`be.bstorm.formation.entity`)

Lombok selon `CLAUDE.md` (`@Getter @Setter @NoArgsConstructor(access = PROTECTED) @AllArgsConstructor @Builder`,
jamais `@Data` / `@ToString` non maîtrisé). Aucun `toString` ne doit inclure `passwordHash` ni `tokenHash`.
Valeurs par défaut **obligatoirement** déclarées avec `@Builder.Default` (sinon le builder les ignore) :

```java
@Builder.Default private boolean enabled = true;
@Builder.Default private int failedLoginCount = 0;
@Builder.Default private Set<Role> roles = new HashSet<>();   // ensemble mutable
```

| Entité | Champ | Type Java | Colonne | Contraintes |
|---|---|---|---|---|
| `User` (`@Table(name = "app_user")`, `extends SoftDeletableEntity`, `@SQLRestriction(SoftDeletableEntity.NOT_DELETED)`, `@DynamicUpdate` — revue 6 mineur 1 : une modification d’entité par 002 ne réécrit pas `locked_until` / compteurs posés par les mises à jour conditionnelles de 001) | `username` | `String` | `username varchar(50)` | not null ; normalisé RG-02 ; unique parmi les non supprimés |
| | `passwordHash` | `String` | `password_hash varchar(255)` | not null ; format RG-14 |
| | `enabled` | `boolean` | `enabled boolean` | not null, défaut `true` (`@Builder.Default`) |
| | `depotId` | `UUID` | `depot_id uuid` | null ; FK vers `depot` ajoutée par 003 |
| | `roles` | `Set<Role>` | table `app_user_role(user_id, role)` | `@ElementCollection(fetch = LAZY)`, `@CollectionTable(name = "app_user_role", joinColumns = @JoinColumn(name = "user_id"))`, `@Enumerated(STRING)`, `@Column(name = "role", length = 20)` ; défaut ensemble vide mutable (`@Builder.Default`) ; un utilisateur sans rôle ne peut pas se connecter (RG-03) |
| | `failedLoginCount` | `int` | `failed_login_count integer` | not null, défaut 0 (`@Builder.Default`), ≥ 0 |
| | `lockedUntil` | `Instant` | `locked_until timestamptz` | null |
| | `lastLoginAt` | `Instant` | `last_login_at timestamptz` | null |
| `RefreshTokenFamily` (`@Table(name = "refresh_token_family")`, `extends BaseEntity`) | `user` | `User` | `user_id uuid` | `@ManyToOne(fetch = LAZY, optional = false)` |
| | `absoluteExpiresAt` | `Instant` | `absolute_expires_at timestamptz` | not null |
| | `revokedAt` | `Instant` | `revoked_at timestamptz` | null |
| | `revocationReason` | `SessionRevocationReason` | `revocation_reason varchar(30)` | `@Enumerated(STRING)`, null ssi `revokedAt` null |
| `RefreshToken` (`@Table(name = "refresh_token")`, `extends BaseEntity`) | `family` | `RefreshTokenFamily` | `family_id uuid` | `@ManyToOne(fetch = LAZY, optional = false)` |
| | `tokenHash` | `String` | `token_hash varchar(64)` | `@Column(name = "token_hash", length = 64, nullable = false, unique = true)` ; hex minuscule |
| | `expiresAt` | `Instant` | `expires_at timestamptz` | not null |
| | `usedAt` | `Instant` | `used_at timestamptz` | null |

```java
package be.bstorm.formation.entity;
public enum SessionRevocationReason { LOGOUT, REUSE_DETECTED, USER_DISABLED, PASSWORD_CHANGED, ADMIN_REVOKED }
```

`RefreshTokenFamily` et `RefreshToken` ne sont **pas** supprimables logiquement (données techniques de session,
purgées par RG-17).

### 5.4 Repositories (`be.bstorm.formation.repository`)

Toutes les méthodes `@Modifying` ci-dessous sont des mises à jour JPQL **sans** le mot-clé `versioned` (la colonne
`version` n'est pas incrémentée, RG-23) et fixent `updatedAt = :now` explicitement. Les requêtes JPQL données sont
indicatives ; les signatures et la sémantique (conditions, valeur de retour) font partie du contrat.

```java
public interface UserRepository extends JpaRepository<User, UUID> {
    /** Ne voit pas les supprimés (@SQLRestriction). Rôles chargés dans la même requête : l'entité est utilisée hors
     *  transaction par le login (§ 5.5), sans LazyInitializationException. */
    @EntityGraph(attributePaths = "roles")
    Optional<User> findByUsername(String normalizedUsername);
    boolean existsByUsername(String normalizedUsername);

    /** SEC-10 : lu dans la même transaction, juste après registerFailedLogin = 1 ; non null ⇔ cette mise à jour a posé
     *  le verrou (la ligne reste verrouillée par la transaction jusqu'au commit). */
    @Query("select u.lockedUntil from User u where u.id = :id")
    Instant findLockedUntilById(@Param("id") UUID id);

    /** RG-04 : échec sur un compte non verrouillé. 1 si appliqué, 0 si le compte est verrouillé (ou absent). */
    @Modifying
    @Query("""
        update User u set
          u.failedLoginCount = case when u.failedLoginCount + 1 >= :maxAttempts then 0 else u.failedLoginCount + 1 end,
          u.lockedUntil      = case when u.failedLoginCount + 1 >= :maxAttempts then :lockUntil else null end,
          u.updatedAt        = :now
        where u.id = :id and (u.lockedUntil is null or u.lockedUntil <= :now)""")
    int registerFailedLogin(@Param("id") UUID id, @Param("now") Instant now,
                            @Param("maxAttempts") int maxAttempts, @Param("lockUntil") Instant lockUntil);

    /** RG-04 / RG-25 : succès → compteur 0, verrou null (effacé sans condition), last_login_at = now ;
     *  appliqué SEULEMENT si le hachage est toujours celui qui a été vérifié, le compte actif et non supprimé.
     *  1 si appliqué ; 0 sinon (le login échoue en 401, aucune autre écriture). La condition deleted_at est explicite
     *  (ne pas compter sur @SQLRestriction dans une mise à jour JPQL). */
    @Modifying
    @Query("""
        update User u set u.failedLoginCount = 0, u.lockedUntil = null, u.lastLoginAt = :now, u.updatedAt = :now
        where u.id = :id and u.passwordHash = :verifiedHash and u.enabled = true and u.deletedAt is null""")
    int registerSuccessfulLogin(@Param("id") UUID id, @Param("verifiedHash") String verifiedHash,
                                @Param("now") Instant now);

    /** RG-14 : re-hachage, appliqué seulement si le hachage n'a pas changé entre-temps (ex. changement par 002). */
    @Modifying
    @Query("update User u set u.passwordHash = :newHash, u.updatedAt = :now where u.id = :id and u.passwordHash = :expectedHash")
    int updatePasswordHash(@Param("id") UUID id, @Param("expectedHash") String expectedHash,
                           @Param("newHash") String newHash, @Param("now") Instant now);
}

public interface RefreshTokenFamilyRepository extends JpaRepository<RefreshTokenFamily, UUID> {
    /** Révoque une famille non encore révoquée ; 1 si appliqué, 0 si déjà révoquée (raison conservée). */
    @Modifying
    @Query("update RefreshTokenFamily f set f.revokedAt = :now, f.revocationReason = :reason, f.updatedAt = :now where f.id = :id and f.revokedAt is null")
    int revoke(@Param("id") UUID familyId, @Param("reason") SessionRevocationReason reason, @Param("now") Instant now);

    /** RG-16 : révoque toutes les familles non révoquées de l'utilisateur ; renvoie leur nombre.
     *  flushAutomatically = true OBLIGATOIRE : les modifications en attente de la transaction englobante (UPDATE
     *  app_user de 002 par dirty checking ou SoftDeleter) sont envoyées en base AVANT la révocation (RG-16, RG-25,
     *  AC-69). Ne pas remplacer par le flush AUTO d'Hibernate (tables disjointes : aucun flush). */
    @Modifying(flushAutomatically = true)
    @Query("update RefreshTokenFamily f set f.revokedAt = :now, f.revocationReason = :reason, f.updatedAt = :now where f.user.id = :userId and f.revokedAt is null")
    int revokeAllActiveByUserId(@Param("userId") UUID userId, @Param("reason") SessionRevocationReason reason,
                                @Param("now") Instant now);
}

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Consommation atomique (RG-09/RG-10/RG-22 étape 9) : 1 si le jeton était inutilisé, 0 sinon. */
    @Modifying
    @Query("update RefreshToken t set t.usedAt = :now, t.updatedAt = :now where t.id = :id and t.usedAt is null")
    int markUsed(@Param("id") UUID id, @Param("now") Instant now);
}
```

Les méthodes de purge (suppression en masse des jetons puis des familles) sont libres ; seul le résultat d'AC-42 compte.

Ces méthodes `@Modifying` sont appelées à l'intérieur d'une transaction ouverte par le service (§ 5.5). Au login,
la famille est créée avec `user = userRepository.getReferenceById(user.getId())` (l'entité chargée par
`findByUsername` est détachée).

Note d'implémentation : au refresh, l'utilisateur est chargé par `UserRepository.findById(family.getUser().getId())`
(l'id d'un proxy LAZY est lisible sans initialisation) : un résultat vide signifie « supprimé logiquement »
(`@SQLRestriction`). Ne jamais initialiser l'association `family.user` d'un utilisateur supprimé (`EntityNotFoundException`).
Le service ne modifie jamais les champs des entités existantes chargées (`User`, `RefreshTokenFamily`,
`RefreshToken`) : toute modification passe par les méthodes ci-dessus.

### 5.5 Services

```java
package be.bstorm.formation.service;

public interface AuthService {

    /** RG-01..05, RG-14, RG-15, RG-24, RG-25. @throws UnauthorizedException (401 "Invalid credentials."),
     *  RetryLaterException 429 (RG-05) ou 503 (RG-21), CryptoException (500). */
    IssuedTokens login(LoginRequest request, String clientIp);

    /** RG-09..11, RG-15, RG-22. refreshToken null/vide ou tout refus → InvalidSessionException
     *  (401 "Invalid or expired session.", cookie effacé par GlobalExceptionHandler). */
    IssuedTokens refresh(String refreshToken, String clientIp);

    /** RG-12, idempotent, ne lève jamais d'exception métier (refreshToken null accepté). */
    void logout(String refreshToken, String clientIp);

    /** RG-16, pour la feature 002. Idempotent. */
    void revokeAllSessions(UUID userId, SessionRevocationReason reason);

    /** RG-17. @return nombre de familles supprimées. */
    int purgeExpiredSessions();

    /** RG-19 : utilisateur courant (CurrentUserProvider.require()) mappé, sans accès base ni transaction. */
    CurrentUserResponse currentUser();

    /** @param refreshTtl Max-Age du cookie = expires_at du jeton émis − now, arrondi inférieur à la seconde (RG-08) ;
     *                    PAS le temps restant de la famille */
    record IssuedTokens(TokenResponse body, String refreshToken, Duration refreshTtl) {
        @Override public String toString() { return "IssuedTokens[refreshToken=***, refreshTtl=" + refreshTtl + "]"; }
    }
}
```

Implémentation `service.impl.AuthServiceImpl` — **découpage transactionnel imposé** (SEC-11 : aucune connexion DB
tenue pendant le sémaphore, le poivre, Argon2 **ni un appel Transit de signature** ; pool Hikari de 10 connexions) :

- **Pas de `@Transactional` au niveau de la classe.** Annotations par méthode, ou `TransactionTemplate` (bean
  injecté) pour les blocs internes à `login`.
- `login` : **non transactionnel** (aucune annotation ; jamais appelé dans une transaction englobante).
  1. `LoginRateLimiter.blockedFor` (mémoire) ;
  2. `userRepository.findByUsername` (transaction en lecture propre au repository, rôles chargés par
     `@EntityGraph`, connexion rendue au retour) ;
  3. `dummyVerify` / `verify` **hors transaction**. Chemin « inconnu / désactivé / sans rôle / verrouillé », dans cet
     ordre : `dummyVerify` ; **puis** `LOGIN_FAILURE` (raison selon la priorité de SEC-10) ; **puis** `recordFailure` ;
     **puis** levée de `UnauthorizedException`. Si `dummyVerify` lève une exception, rien de ce qui suit n'a lieu ;
  4. `MISMATCH` → dans cet ordre exact :
     a. `TransactionTemplate` courte : `registerFailedLogin` puis, si elle renvoie 1, `findLockedUntilById` → booléen
        `lockedNow` (= résultat non null) ; si elle renvoie 0, `lockedNow = false` ; **aucune journalisation dans ce
        bloc** ;
     b. commit ;
     c. `LOGIN_FAILURE reason=BAD_PASSWORD` (toujours, même si `registerFailedLogin` = 0) ;
     d. `ACCOUNT_LOCKED` si `lockedNow` ;
     e. `recordFailure` ;
     f. levée de `UnauthorizedException` (hors transaction : rien à annuler) ;
  5. `now = clock.instant()` (une seule lecture, utilisée pour `iat`, `last_login_at`, `absolute_expires_at`,
     `expires_at`) ; `MATCH_REHASH_NEEDED` → `PasswordHasher.hash(raw)` (bean injecté, observable par un spy) **hors
     transaction** ; exception capturée → `PASSWORD_REHASH_FAILED`, pas de nouveau hachage ;
  6. **hors transaction** : construction de la `RefreshTokenFamily` (sans `user`) et `assignId()` (= `sid`), génération
     du jeton de rafraîchissement, `CurrentUser` via `AuthMapper.toCurrentUser`, puis `JwtKeyProvider.currentKeyId()` et
     `sign` ; une exception ici → propagée (`500`), aucune écriture ;
  7. `TransactionTemplate` courte : `registerSuccessfulLogin(id, verifiedHash, now)` **en premier**, avec
     `verifiedHash` = `password_hash` lu à l'étape 2 ; si elle renvoie `0` → `status.setRollbackOnly()`, le bloc
     renvoie une décision « refusé » sans aucune autre écriture (RG-25) ; sinon `updatePasswordHash(id, verifiedHash,
     nouveauHachage, now)` (si nouveau hachage), `family.setUser(userRepository.getReferenceById(id))`, `save` de la
     famille puis du jeton ; toute exception annule ce bloc ;
  8. après la sortie du bloc : décision « refusé » → `LOGIN_FAILURE reason=CREDENTIALS_CHANGED`, puis `recordFailure`,
     puis levée de `UnauthorizedException` (`401`, aucun `Set-Cookie`) ; sinon (après commit) `LOGIN_SUCCESS`, puis
     `PASSWORD_REHASHED` si `updatePasswordHash` = 1 (RG-14), puis `200`.
- `refresh` : **non transactionnel** (aucune annotation), trois phases :
  - **A** — `TransactionTemplate` courte : étapes 1 à 7 de RG-22 (lectures, révocations `REUSE_DETECTED` /
    `USER_DISABLED`) et, si elles passent, construction du `CurrentUser` (rôles initialisés **dans** ce bloc) ; le bloc
    renvoie une décision ; en cas de refus, la révocation est **validée** puis `InvalidSessionException` est levée
    **après** le commit (aucun `noRollbackFor` nécessaire) ;
  - **B** — **hors transaction** : `currentKeyId()` + `sign` (étape 8) ; exception → `500`, aucune écriture ;
  - **C** — `TransactionTemplate` courte : `markUsed` ; `0` → `revoke(REUSE_DETECTED)`, commit, puis
    `InvalidSessionException` ; `1` → insertion du nouveau `RefreshToken`, commit, puis `REFRESH_SUCCESS`.
  Le `now` lu au début de A sert à toutes les phases. Aucun hachage Argon2 dans le refresh.
  Journaux du refresh (SEC-10) : `REFRESH_REJECTED` et `REFRESH_REUSE_DETECTED` sont écrits **après** le commit de la
  phase A (étapes 1 à 7) ou de la phase C (étape 9) qui a décidé le refus, juste avant la levée de
  `InvalidSessionException` ; `REFRESH_SUCCESS` après le commit de la phase C. Aucune journalisation à l'intérieur d'un
  bloc `TransactionTemplate`.
- `logout`, `revokeAllSessions`, `purgeExpiredSessions` : `@Transactional` (propagation par défaut `REQUIRED`).
- `logout` : l'événement `LOGOUT` est journalisé par le **même mécanisme** que `SESSIONS_REVOKED` :
  `TransactionSynchronizationManager.registerSynchronization(… afterCommit() …)`, enregistré dans la méthode (y compris
  quand le cookie est absent ou inconnu : `userId=-`) ; jamais d'appel direct au logger dans le corps de la méthode
  transactionnelle. Sans synchronisation active (cas défensif), journalisation immédiate.
- `revokeAllSessions` : `revokeAllActiveByUserId` (qui flushe d'abord le contexte de persistance de la transaction
  englobante, `flushAutomatically = true`, § 5.4 ; aucun `entityManager.flush()` supplémentaire n'est requis), puis
  enregistrement de la journalisation `SESSIONS_REVOKED` par
  `TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { afterCommit() … })`
  quand une synchronisation est active (`TransactionSynchronizationManager.isSynchronizationActive()`, toujours vrai
  sous `@Transactional`) : la ligne est écrite au commit de la **transaction physique englobante** (celle de 002 si
  l'appel a lieu dans sa transaction, sinon celle de la méthode) et jamais en cas d'annulation. Sans synchronisation
  active (cas défensif), journalisation immédiate. Une ligne par appel validé, `count` pouvant valoir 0.
- `currentUser` : aucune annotation, aucun accès base (RG-19).

Purge appelée par une méthode
`@Scheduled(cron = "${app.auth.refresh.purge-cron}", zone = "Europe/Brussels")` (nécessite `@EnableScheduling`
dans une classe de `framework/config`, ex. `SchedulingConfig`).

Composants du socle (`framework`) :

```java
package be.bstorm.formation.framework.security.password;

public interface PasswordHasher {
    /** RG-14 : "p<version>$" + Argon2id(pepper(raw)). Utilisé par le seeder, le re-hachage et la feature 002. */
    String hash(String rawPassword);
    /** RG-14 : hachage mal formé → MISMATCH sans appel au poivre ; version de poivre refusée → CryptoException. */
    Verification verify(String rawPassword, String storedHash);
    /** RG-03 : sémaphore RG-21 + pepper.pepper(raw) (un appel) + encoder.matches(pepper.value(), dummyHash).
     *  Ne renvoie rien ; propage CryptoException / RetryLaterException comme verify. */
    void dummyVerify(String rawPassword);

    enum Verification { MISMATCH, MATCH, MATCH_REHASH_NEEDED }
}

/** Implémentation unique (pas d'implémentation de test). Sémaphore RG-21 pris avant le poivre, rendu après Argon2. */
@Component
public class Argon2PasswordHasher implements PasswordHasher {
    public Argon2PasswordHasher(PasswordPepper pepper, AuthProperties properties) { … }
}
```

Hachage factice (`dummyHash`) : calculé **dans le constructeur** par
`encoder.encode(<32 octets SecureRandom encodés en base64 standard, 44 caractères>)`, où `encoder` est le même
`Argon2PasswordEncoder` (paramètres `app.auth.password.*`) ; sans poivre ni préfixe `p<n>$`. **Le constructeur
n'appelle jamais `PasswordPepper`** (aucune dépendance à OpenBao au démarrage ; un poivre simulé bloquant ou un
`@MockitoBean` non configuré n'affecte pas la construction). Le constructeur n'utilise pas le sémaphore.

```java
package be.bstorm.formation.framework.security.jwt;

/**
 * Clés de signature des jetons d'accès (ADR 0001 D4).
 * Aucune implémentation en mémoire dans src/main : en profil test, TestcontainersConfig fournit InMemoryJwtKeyProvider.
 */
public interface JwtKeyProvider {
    /** kid de la clé courante = version Transit en décimal, ex. "1". */
    String currentKeyId();
    /** Signature ES256 (r||s, 64 octets) de signingInput (octets ASCII de "<header b64url>.<payload b64url>"). */
    byte[] sign(String keyId, byte[] signingInput);
    /** Clé publique P-256 du kid ; vide si inconnu ou retiré. Lit le cache uniquement : jamais d'appel réseau
     *  (appelée à chaque requête authentifiée). */
    Optional<ECPublicKey> publicKey(String keyId);
}

/**
 * Implémentation dev/prod. transit/sign/jwt (key_version, hash_algorithm=sha2-256, marshaling_algorithm=jws → r||s ;
 * une conversion DER → P1363 est acceptable si l'API Spring Vault ne permet pas jws) et transit/keys/jwt (latest_version,
 * clés publiques des versions >= min_decryption_version).
 * Cache (dernière version + clés publiques) :
 *  - publicKey(kid) ne lit QUE le cache, ne déclenche jamais de chargement (kid inconnu → vide, sans appel OpenBao) ;
 *  - currentKeyId() (chemin de signature, login / refresh, qui dépend déjà d'OpenBao) recharge le cache s'il est vide
 *    ou si le dernier chargement date d'au moins app.auth.jwt.key-cache-refresh-interval (horloge = bean Clock) ;
 *    un seul rechargement à la fois. L'instance ne signe donc jamais avec un kid absent de son propre cache ;
 *  - chargement initial sur ApplicationReadyEvent ; un échec y est journalisé (WARN) sans bloquer le démarrage ;
 *  - un rechargement en échec conserve le cache précédent (WARN, sans jeton) ; si le cache est vide,
 *    currentKeyId() lève CryptoException (→ 500 au login / refresh).
 * VaultException → CryptoException.
 */
@Component
@ConditionalOnBooleanProperty("app.openbao.enabled")
public class TransitJwtKeyProvider implements JwtKeyProvider {
    public static final String KEY = "jwt";
    public TransitJwtKeyProvider(VaultOperations vaultOperations, AuthProperties properties, Clock clock) { … }
}
```

Émission / vérification : classes libres dans `framework.security.jwt` (ex. `AccessTokenIssuer`, `JwtDecoder` Nimbus +
convertisseur `Jwt → CurrentUserAuthentication`), branchées dans `SecurityConfig` via `oauth2ResourceServer` avec
`ProblemDetailSecurityHandler` comme point d'entrée, et un `BearerTokenResolver` qui renvoie `null` **si et seulement
si** la méthode est `POST` **et** le chemin est exactement `/api/v1/auth/login`, `/api/v1/auth/refresh` ou
`/api/v1/auth/logout` (RG-20) ; sur toute autre méthode de ces chemins (ex. `GET /api/v1/auth/login`), le jeton est lu
normalement (`405` avec un jeton valide, `401` sans, AC-40). Il ne lit **que** l'en-tête `Authorization` (ni
paramètre, ni corps, ni cookie). Pièges Nimbus
à traiter (§ 5.8) : voir « Notes d'implémentation ».

Anti-CSRF : filtre `be.bstorm.formation.framework.security.AuthCsrfFilter` (RG-13).

```java
public class AuthCsrfFilter extends OncePerRequestFilter {
    /** Pas un @Component (sinon enregistré aussi comme filtre servlet) : instancié dans SecurityConfig. */
    public AuthCsrfFilter(CorsConfigurationSource corsConfigurationSource, HandlerExceptionResolver resolver) { … }
}
```

- S'applique uniquement à `POST /api/v1/auth/refresh` et `POST /api/v1/auth/logout` ; les autres requêtes (dont les
  preflight `OPTIONS`) passent sans contrôle.
- Position : `http.addFilterBefore(authCsrfFilter, org.springframework.web.filter.CorsFilter.class)` — donc avant le
  filtre CORS (qui rejetterait sinon `Origin: null` / une origine inconnue avec son propre corps texte) et avant
  `BearerTokenAuthenticationFilter`.
- Contrôle de l'origine : `CorsConfiguration c = corsConfigurationSource.getCorsConfiguration(request)` ; accepté ssi
  `origin != null && c != null && c.checkOrigin(origin) != null`.
- Rejet : journalise `CSRF_REJECTED` puis délègue au `HandlerExceptionResolver` (bean `handlerExceptionResolver`, comme
  `ProblemDetailSecurityHandler`) avec `new BusinessException(HttpStatus.FORBIDDEN, "Forbidden",
  "Cross-site request rejected.")` → même format `ProblemDetail` que les autres erreurs. Ne pas utiliser
  `AccessDeniedException` (convertie en 401 pour un anonyme). La chaîne s'arrête (pas d'appel à `chain.doFilter`).
- Le `CsrfFilter` de Spring reste désactivé : ADR 0002 D3 le remplace (mettre à jour la javadoc de `SecurityConfig`).

Ordre résultant de la chaîne (extrait) : `HttpsEnforcementFilter` → … → `AuthCsrfFilter` → `CorsFilter` → … →
`BearerTokenAuthenticationFilter` → … → `AuthorizationFilter`.

Limitation IP : composant `be.bstorm.formation.framework.security.LoginRateLimiter` (RG-05).

```java
@Component
public class LoginRateLimiter {
    public LoginRateLimiter(AuthProperties properties, Clock clock) { … }
    /** Durée restante du blocage de la clé de cette IP (RG-05), vide si non bloquée. */
    public Optional<Duration> blockedFor(String clientIp);
    /** Enregistre un échec de login (401 uniquement). */
    public void recordFailure(String clientIp);
    /** Vide toute la table (tests ; aucune route ne l'expose). */
    public void reset();
}
```

Thread-safe (ex. `ConcurrentHashMap` + opérations atomiques par clé). Analyse de l'adresse par
`InetAddress.ofLiteral` uniquement (RG-05) : jamais de résolution DNS ; adresse non littérale → chaîne brute comme clé.

Configuration `be.bstorm.formation.framework.security.AuthProperties` (enregistrée par
`@EnableConfigurationProperties` ou `@ConfigurationPropertiesScan`) :

```java
@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(@Valid @NotNull Jwt jwt, @Valid @NotNull Refresh refresh,
                             @Valid @NotNull Login login, @Valid @NotNull Password password) {
    public record Jwt(@NotBlank String issuer, @NotBlank String audience,
                      @NotNull Duration accessTokenTtl, @NotNull Duration keyCacheRefreshInterval) {}
    public record Refresh(@NotNull Duration idleTtl, @NotNull Duration absoluteTtl,
                          @NotNull Duration purgeRetention, @NotBlank String purgeCron) {}   // absoluteTtl >= idleTtl
    public record Login(@Min(1) int maxFailedAttempts, @NotNull Duration lockDuration,
                        @Min(1) int ipMaxFailures, @NotNull Duration ipWindow, @Min(1) int ipMaxTracked) {}
    public record Password(@Min(16) int saltLength, @Min(32) int hashLength, @Min(1) int parallelism,
                           @Min(19456) int memoryKib, @Min(2) int iterations,
                           @Min(1) int maxConcurrentHashes, @NotNull Duration hashAcquireTimeout) {}
}
```

Valeurs par défaut (`application.yml`) :

```yaml
app:
  auth:
    jwt:
      issuer: ${JWT_ISSUER:formation-api}
      audience: ${JWT_AUDIENCE:formation-front}
      access-token-ttl: 5m
      key-cache-refresh-interval: 60s
    refresh:
      idle-ttl: 8h
      absolute-ttl: 24h
      purge-retention: 7d
      purge-cron: "0 15 3 * * *"
    login:
      max-failed-attempts: 5
      lock-duration: 15m
      ip-max-failures: 20
      ip-window: 5m
      ip-max-tracked: 10000
    password:
      salt-length: 16
      hash-length: 32
      parallelism: 1
      memory-kib: 65536
      iterations: 3
      max-concurrent-hashes: 4
      hash-acquire-timeout: 2s
  seed:
    demo-password: ${SEED_DEMO_PASSWORD:}
  security:
    cors:
      exposed-headers: Location,Retry-After
```

Les AC utilisent ces valeurs par défaut ; un test peut les surcharger (ex. Argon2 au plancher `memory-kib: 19456`,
`iterations: 2` pour la vitesse), sauf AC-15 / AC-17 qui supposent `m=65536,t=3`.

Seeder : `be.bstorm.formation.seed.UserSeeder` (`@Seeder`, `implements DataSeeder`, `order()` = 10), **constructeur
explicite** (pas `@RequiredArgsConstructor`) :

```java
public UserSeeder(UserRepository users, PasswordHasher passwordHasher,
                  @Value("${app.seed.demo-password:}") String demoPassword) { … }
```

`shouldRun()` = `!users.existsByUsername("admin")` (ne lit pas le mot de passe) ; `seed()` valide le mot de passe
(RG-18) avant toute écriture, puis enregistre les trois comptes par `UserRepository.save` (ne nécessite pas de
transaction englobante). `.env.dev.example` documente `SEED_DEMO_PASSWORD`, transmis à `api-dev` ; README : comptes de
démonstration (sans le mot de passe). Exception documentée à ADR 0001 D9 (RG-18) : aucune lecture KV, aucun autre
composant que `UserSeeder` ne lit `app.seed.demo-password`, et `SEED_DEMO_PASSWORD` n'est transmise à aucun service
de `docker-compose.prod.yml`.

Dépendance : ajouter `org.springframework.boot:spring-boot-starter-security-oauth2-resource-server` (sans version) au
`pom.xml`, et la ligne correspondante au tableau « Stack technique » de `CLAUDE.md` (ligne Sécurité : « + resource
server OAuth2 (Nimbus JOSE) pour la vérification des JWT »).

### 5.6 Mapper (`be.bstorm.formation.mapper.AuthMapper`)

```java
@Mapper(config = MapStructConfig.class)
public interface AuthMapper {
    /** Appelé seulement si user.getRoles() est non vide (le service vérifie avant, RG-03 / RG-22). */
    CurrentUser toCurrentUser(User user);                 // id, roles, depotId
    CurrentUserResponse toResponse(CurrentUser user);     // roles triés via la méthode default ci-dessous

    @Mapping(target = "tokenType", constant = "Bearer")
    TokenResponse toTokenResponse(String accessToken, long expiresIn, CurrentUser user);

    default List<Role> sortRoles(Set<Role> roles) { return roles.stream().sorted().toList(); }
}
```

Aucune construction manuelle de `TokenResponse` ni de `CurrentUserResponse` dans le service ou le contrôleur.

### 5.7 Erreurs

| Cas | Exception | HTTP | `title` | `detail` |
|---|---|---|---|---|
| Login refusé (RG-03) | `framework.exception.UnauthorizedException` (nouvelle) | 401 | `Unauthorized` | `Invalid credentials.` |
| Refresh refusé (RG-11, RG-22) | `framework.exception.InvalidSessionException` (nouvelle, `extends UnauthorizedException`) ; gestionnaire dédié qui ajoute `Set-Cookie: AuthCookies.cleared()` | 401 | `Unauthorized` | `Invalid or expired session.` |
| Jeton d'accès absent | `InsufficientAuthenticationException` (existant) | 401 | `Unauthorized` | (existant, non testé) |
| Jeton d'accès invalide | `AuthenticationException` du resource server (existant) | 401 | `Unauthorized` | (existant, non testé) |
| CSRF (RG-13) | `BusinessException(FORBIDDEN, "Forbidden", …)` via `AuthCsrfFilter` → `HandlerExceptionResolver` | 403 | `Forbidden` | `Cross-site request rejected.` |
| Limite IP (RG-05) | `framework.exception.RetryLaterException` (nouvelle) | 429 | `Too Many Requests` | `Too many failed login attempts. Retry later.` |
| Hachage saturé (RG-21) | `RetryLaterException` (`retryAfter` = 1 s) | 503 | `Service Unavailable` | `The service is busy. Retry later.` |
| Validation | existant | 400 | `Validation Failed` | (existant) |
| OpenBao indisponible (poivre, signature, chargement des clés à la signature) | `CryptoException` → gestionnaire générique existant | 500 | `Internal Server Error` | (existant, `errorId`) |
| Clé publique introuvable ou erreur de `publicKey` pendant la vérification d'un jeton d'accès | rejet par le sélecteur de clé → `BadJwtException` → `InvalidBearerTokenException` → point d'entrée existant | 401 | `Unauthorized` | (existant, non testé) |

`type` = `about:blank` partout. L'en-tête `WWW-Authenticate` n'est pas spécifié (non testé). Aucun `409` (RG-23).

Signatures des nouvelles exceptions (`be.bstorm.formation.framework.exception`) :

```java
public class UnauthorizedException extends BusinessException {
    public UnauthorizedException(String detail) { super(HttpStatus.UNAUTHORIZED, "Unauthorized", detail); }
}

public class InvalidSessionException extends UnauthorizedException {
    public static final String DETAIL = "Invalid or expired session.";
    public InvalidSessionException() { super(DETAIL); }
}

@Getter
public class RetryLaterException extends BusinessException {
    private final Duration retryAfter;
    public RetryLaterException(HttpStatus status, String title, String detail, Duration retryAfter) { … }
}
```

Nouveaux gestionnaires dans `GlobalExceptionHandler` (le plus spécifique l'emporte sur `handleBusiness`) :

```java
@ExceptionHandler(InvalidSessionException.class)
ResponseEntity<ProblemDetail> handleInvalidSession(InvalidSessionException ex);
// 401, title "Unauthorized", detail ex.getMessage(), en-tête Set-Cookie = AuthCookies.cleared().toString()

@ExceptionHandler(RetryLaterException.class)
ResponseEntity<ProblemDetail> handleRetryLater(RetryLaterException ex);
// statut / title / detail de l'exception, en-tête Retry-After = secondes de retryAfter arrondies au supérieur, minimum 1
```

### 5.8 Jeton d'accès (format)

En-tête : `{"alg":"ES256","typ":"JWT","kid":"<version>"}`. Charge utile :

| Claim | Valeur |
|---|---|
| `iss` | `app.auth.jwt.issuer` |
| `aud` | `app.auth.jwt.audience` (chaîne) |
| `sub` | UUID de l'utilisateur |
| `iat` | `clock.instant().getEpochSecond()` (secondes epoch, fraction tronquée) |
| `exp` | `iat + access-token-ttl` en secondes |
| `jti` | UUID aléatoire |
| `sid` | UUID de la `RefreshTokenFamily` |
| `roles` | tableau des noms `Role`, ordre de déclaration, non vide |
| `depot_id` | UUID du dépôt, **absent** si null |

Vérification (RG-07), toutes obligatoires : format compact à 3 segments ; `alg` == `ES256` exactement (aucun autre
algorithme, jamais `none`) ; `kid` présent et connu de `JwtKeyProvider.publicKey` ; signature valide ; `iss` et `aud`
égaux à la configuration ; `exp` présent et `now < exp` (aucune tolérance) ; `iat` présent et `iat <= now` ; `sub` UUID
valide ; `roles` tableau non vide de valeurs `Role` connues ; `depot_id` UUID valide s'il est présent.

Bornes temporelles (décision) : **tolérance d'horloge = 0** ; `exp` **exclusif** — jeton accepté ssi
`now < exp`, refusé dès `now == exp` (avec `now = clock.instant()` et `exp` = `Instant.ofEpochSecond(exp)`) ;
`iat <= now` accepté (égalité incluse).

Notes d'implémentation (pièges Nimbus / Spring Security) :

- **Ne pas utiliser `JwtTimestampValidator`** : même avec `Duration.ZERO`, il teste `now.minus(skew).isAfter(exp)` et
  accepte donc le jeton à `now == exp` (AC-22 échouerait). Écrire un `OAuth2TokenValidator<Jwt>` qui exige `exp` et
  `iat` présents, `now < exp` et `iat <= now`, avec le bean `Clock`.
- Le décodeur est construit à partir d'un `DefaultJWTProcessor` créé par le code (pour le sélecteur de clé) :
  neutraliser son vérificateur de claims, qui tolère 60 s —
  `processor.setJWTClaimsSetVerifier((claims, context) -> {})` — puis `new NimbusJwtDecoder(processor)` et
  `decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(…))` avec **uniquement** les validateurs de cette
  feature (temps, `iss`, `aud`) ; ne pas partir de `JwtValidators.createDefault*()` (qui réintroduit
  `JwtTimestampValidator`). Toute la validation des claims passe par les `OAuth2TokenValidator`.
- Sélecteur de clé (`JWSKeySelector`) restreint à `JWSAlgorithm.ES256` (rejette `none`, `HS256`) et alimenté par
  `JwtKeyProvider.publicKey(kid)` ; `kid` absent ou inconnu → liste vide → rejet (`401`). Le sélecteur **capture toute
  exception** levée par `publicKey` (ex. `CryptoException`), la journalise (`WARN`, sans le jeton) et renvoie une liste
  vide : sinon l'exception sortirait de la chaîne de filtres (`AuthenticationServiceException` relancée par
  `BearerTokenAuthenticationFilter` → corps `/error`, pas `ProblemDetail`). Voir AC-60.
- Toute erreur de conversion `Jwt → CurrentUserAuthentication` (`sub` non UUID, rôle inconnu, `roles` vide,
  `depot_id` invalide, `IllegalArgumentException` de `CurrentUser`) doit être levée en
  `InvalidBearerTokenException` (ou `BadJwtException`) → `401`, jamais `500`.

### 5.9 Résolution de l'IP cliente — prérequis de déploiement PRE-01

RG-05 rend l'IP cliente **critique pour la disponibilité** : si toutes les requêtes arrivent avec l'adresse d'un
relais (HAProxy ou WAF), 20 échecs de login de n'importe qui bloquent l'authentification de **tous** les utilisateurs
pendant 5 minutes (API4:2023). Constat sur l'existant (vérifié le 2026-11-17) :

| Maillon | Fichier | Comportement constaté |
|---|---|---|
| HAProxy | `docker/prod/haproxy/haproxy.cfg` (`frontend https_in`) | supprime `Forwarded`, `X-Forwarded-*`, `X-Real-IP` envoyés par le client, puis `option forwardfor` → `X-Forwarded-For: <client>` |
| WAF (nginx + ModSecurity, image `owasp/modsecurity-crs:4.25.2-nginx-alpine`) | `docker-compose.test-prod.yml`, service `waf` (172.31.0.11) | `SET_REAL_IP_FROM: 172.31.0.10` (HAProxy), `REAL_IP_HEADER: X-Forwarded-For`, `real_ip_recursive on` (défaut de l'image) → `$remote_addr` = client ; transmet `X-Forwarded-For: $proxy_add_x_forwarded_for` (modèle `includes/proxy_backend.conf.template` de l'image) = `<client>, <client>`, et `X-Forwarded-Proto: $scheme` (`https`) |
| API | `application-prod.yml` | `server.forward-headers-strategy: native` → Tomcat `RemoteIpValve`, `internal-proxies: ${TRUSTED_PROXIES}`, `remote-ip-header: x-forwarded-for` ; en test-prod `TRUSTED_PROXIES: '172\.31\.0\.11'` (WAF) |
| Résultat test-prod | — | la valve lit `X-Forwarded-For` de droite à gauche, s'arrête à la première adresse non fiable → `getRemoteAddr()` = **IP du client vue par HAProxy** : la chaîne de référence est **correcte** |
| Prod réelle | `docs/ops/DEPLOIEMENT-PROD.md` | le WAF n'est pas dans `docker-compose.prod.yml` ; l'exemple de `.env` (`TRUSTED_PROXIES='172\.30\.0\.1'`) ne vaut que pour HAProxy seul sur l'hôte, et le sous-réseau cité (172.30) n'est pas celui de prod par défaut (172.31) : **non garanti** |

**PRE-01** (exigence de déploiement, condition de mise en production de 001) — pour toute topologie :

1. le premier relais (HAProxy) supprime tout en-tête d'IP envoyé par le client et pose `X-Forwarded-For` (déjà fait) ;
2. chaque relais intermédiaire (WAF) **ajoute** son client à `X-Forwarded-For` sans l'écraser
   (`$proxy_add_x_forwarded_for`) ; s'il résout lui-même l'IP réelle (`real_ip`), il ne fait confiance qu'au relais
   précédent (`SET_REAL_IP_FROM` = IP de HAProxy vue par le WAF) ;
3. `TRUSTED_PROXIES` (regex) couvre **exactement** les adresses des relais de la chaîne, **HAProxy et WAF** (défense
   en profondeur : la résolution reste correcte même si le WAF ne fait pas `real_ip`, car l'IP de HAProxy est alors
   reconnue comme relais et sautée), et **rien d'autre** ; jamais vide (la valeur par défaut de Spring Boot fait
   confiance à tous les réseaux privés et à la boucle locale) ;
4. l'API n'est joignable que par le dernier relais (déjà vérifié par `check.sh`, section « Isolement »).

Livrables de 001 (code-writer, hors `src/`) :

- `docker-compose.test-prod.yml`, service `api-prod` : `TRUSTED_PROXIES: '172\.31\.0\.1[01]'` (HAProxy 172.31.0.10 et
  WAF 172.31.0.11), commentaire mis à jour ; `.env.example` : commentaire de `TRUSTED_PROXIES` aligné (« toutes les IP
  des relais : HAProxy et WAF »).
- `docker/test-prod/check.sh`, nouvelle section « == IP cliente (limitation des logins) == », exécutée après la
  section « Trafic légitime » :
  - **CHK-01** — un `POST $U/api/v1/auth/login` (`Content-Type: application/json`,
    `{"username":"chk-ip-probe","password":"Chk-Wrong-Passw0rd"}`) → `401` ; puis la dernière ligne de
    `dc logs api-prod` contenant `event=LOGIN_FAILURE` a une valeur `ip=` non vide, différente de `-`, de `172.31.0.10`
    (HAProxy) et de `172.31.0.11` (WAF) → `ok`, sinon `ko "IP cliente = IP d'un relais : RG-05 deviendrait global"` ;
  - **CHK-02** — même requête avec l'en-tête `X-Forwarded-For: 198.51.100.66` → `401`, et la dernière ligne
    `event=LOGIN_FAILURE` ne contient **pas** `ip=198.51.100.66` (en-tête client supprimé par HAProxy).
  Ces contrôles se lancent à la main (`docker/test-prod/check.sh`), pas par `/run-tests`.

Aucun contrôle au démarrage de l'API : elle ne peut pas connaître la topologie en amont (le démarrage échoue déjà si
`TRUSTED_PROXIES` est absent en prod, `docker-compose.prod.yml`). La vérification côté code est AC-66 / AC-67 ; côté
chaîne, CHK-01 / CHK-02. Actions utilisateur (hors périmètre des agents, `docs/`) : `DEPLOIEMENT-PROD.md` — exiger
PRE-01, corriger l'exemple `TRUSTED_PROXIES` (IP de HAProxy **et** du WAF, sous-réseau réel) et faire passer
`check.sh` (ou CHK-01 adapté) avant ouverture ; voir Q11.

## 6. Modèle de données / migration

Migration `V3__authentication.sql` :

```sql
CREATE TABLE app_user (
    id                 uuid         PRIMARY KEY,
    created_at         timestamptz  NOT NULL,
    updated_at         timestamptz  NOT NULL,
    version            bigint       NOT NULL,
    deleted_at         timestamptz,
    deleted_by         uuid,
    username           varchar(50)  NOT NULL CHECK (username ~ '^[a-z0-9][a-z0-9._-]{2,49}$'),
    password_hash      varchar(255) NOT NULL,
    enabled            boolean      NOT NULL DEFAULT true,
    depot_id           uuid,                                   -- FK vers depot : feature 003
    failed_login_count integer      NOT NULL DEFAULT 0 CHECK (failed_login_count >= 0),
    locked_until       timestamptz,
    last_login_at      timestamptz
);
CREATE UNIQUE INDEX ux_app_user_username ON app_user (username) WHERE deleted_at IS NULL;

-- Table de collection (@ElementCollection) : pas de colonnes BaseEntity.
CREATE TABLE app_user_role (
    user_id uuid        NOT NULL REFERENCES app_user (id),
    role    varchar(20) NOT NULL CHECK (role IN ('DRIVER', 'FLEET_MANAGER', 'ADMIN')),
    PRIMARY KEY (user_id, role)
);

CREATE TABLE refresh_token_family (
    id                  uuid        PRIMARY KEY,
    created_at          timestamptz NOT NULL,
    updated_at          timestamptz NOT NULL,
    version             bigint      NOT NULL,
    user_id             uuid        NOT NULL REFERENCES app_user (id),
    absolute_expires_at timestamptz NOT NULL,
    revoked_at          timestamptz,
    revocation_reason   varchar(30) CHECK (revocation_reason IN
                        ('LOGOUT', 'REUSE_DETECTED', 'USER_DISABLED', 'PASSWORD_CHANGED', 'ADMIN_REVOKED')),
    CHECK ((revoked_at IS NULL) = (revocation_reason IS NULL))
);
CREATE INDEX ix_refresh_token_family_user ON refresh_token_family (user_id) WHERE revoked_at IS NULL;

CREATE TABLE refresh_token (
    id         uuid        PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version    bigint      NOT NULL,
    family_id  uuid        NOT NULL REFERENCES refresh_token_family (id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    expires_at timestamptz NOT NULL,
    used_at    timestamptz
);
CREATE INDEX ix_refresh_token_family ON refresh_token (family_id);
```

`token_hash` est un `varchar(64)` (et non `char(64)`) pour passer la validation `ddl-auto=validate` d'un champ
`String` (cohérent avec `stored_file.sha256 varchar(64)` de V2). Aucune colonne chiffrée (aucune donnée `S2`, voir
§ 7.5) ; aucune instruction de niveau base. La feature 002 ajoutera e-mail, nom, prénom, catégorie de permis,
`niss_enc` / `niss_index` par une nouvelle migration.

## 7. Sécurité

Référentiels : OWASP Top 10 **2025**, API Security Top 10 **2023**, ASVS **5.0.0** (30/05/2025) niveau **L2**
(références ASVS citées au niveau chapitre / section : V6 Authentication, V7 Session Management, V9 Self-contained
Tokens, V3.3 Cookie Setup, V11 Cryptography, V16 Security Logging and Error Handling). Cheat sheets : Authentication,
Password Storage, Session Management, JSON Web Token for Java, Cross-Site Request Forgery Prevention, Logging.

### 7.1 Surface d'attaque

| Élément | Détail |
|---|---|
| Endpoints publics | `POST /api/v1/auth/login` (identifiants), `/refresh` et `/logout` (cookie) — exposés à Internet via HAProxy + WAF |
| Endpoint authentifié | `GET /api/v1/auth/me` ; plus généralement tout l'API via `Authorization: Bearer` |
| Données | mot de passe, hachage, jeton de rafraîchissement, JWT (`SECRET`) ; identifiant, rôles, dépôt, IP, compteurs, historique de sessions (`S1`) |
| Acteurs | visiteur anonyme (attaquant potentiel : force brute, credential stuffing, vol de cookie, CSRF), utilisateur authentifié (élévation via JWT forgé) |
| Frontières de confiance | navigateur ↔ HAProxy ↔ WAF ↔ API (TLS 1.3) ; en-têtes `X-Forwarded-For` de confiance uniquement depuis les relais (`TRUSTED_PROXIES`, § 5.9) ; API ↔ OpenBao (poivre, signature, clés publiques) ; API ↔ PostgreSQL |
| Dépendances | `spring-boot-starter-security-oauth2-resource-server` (Nimbus JOSE), Bouncy Castle (Argon2) |

### 7.2 Exigences

- **SEC-01** — Mots de passe hachés Argon2id + poivre HMAC Transit non exportable, version de poivre mémorisée,
  re-hachage transparent ; jamais en clair ni chiffrés de façon réversible (RG-14). *(réf. : A04:2025, API2:2023,
  ASVS 5.0 V6.2 / V11.4, Password Storage CS, ADR 0002 D4/D5)*
- **SEC-02** — Pas d'énumération de comptes : réponse, coût de calcul **et dépendances appelées** (poivre OpenBao)
  identiques pour toutes les causes d'échec (RG-03), hachage factice. *(réf. : A07:2025, API2:2023, ASVS 5.0 V6.3,
  Authentication CS)*
- **SEC-03** — Anti force brute : verrouillage temporaire par compte (RG-04) et limitation par IP (/64 en IPv6, table
  bornée) avec `429` + `Retry-After` (RG-05), en plus de la limitation HAProxy. *(réf. : API2:2023, API4:2023,
  ASVS 5.0 V6.3, Authentication CS)*
- **SEC-04** — Jeton d'accès : algorithme imposé côté serveur (ES256 uniquement, rejet de `none` et HS256), clé privée
  jamais exportée (Transit), vérification `kid`/signature/`iss`/`aud`/`exp`/`iat` sans tolérance, durée 5 min, jeton
  seulement dans `Authorization` (RG-06, RG-07, § 5.8). *(réf. : A04:2025, A07:2025, API2:2023, ASVS 5.0 V9.1 / V9.2,
  JWT CS)*
- **SEC-05** — Jeton de rafraîchissement : 256 bits aléatoires, stocké haché (SHA-256), cookie `__Secure-`
  `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`, jamais dans un corps (RG-08). *(réf. : ASVS 5.0 V3.3 / V7.2,
  Session Management CS, ADR 0002 D2)*
- **SEC-06** — Rotation à chaque usage, détection de réutilisation avec révocation de la famille, consommation
  atomique, ordre des contrôles déterministe (RG-09, RG-10, RG-22). *(réf. : API2:2023, ASVS 5.0 V7.2, OAuth 2.0
  Security BCP — refresh token rotation)*
- **SEC-07** — Durées de session : 8 h d'inactivité, 24 h absolues ; logout côté serveur ; révocation globale
  disponible pour 002 (changement de mot de passe, désactivation) ; utilisateur désactivé/supprimé/sans rôle refusé au
  refresh ; aucune session ne peut être créée par un login concurrent d'un changement de mot de passe, d'une
  désactivation ou d'une suppression et survivre à la révocation (écriture de succès conditionnelle, ordre imposé à
  002, flush avant révocation garanti par 001) — **sauf** un jeton d'accès de ≤ 5 min émis par un refresh concurrent
  de la révocation (risque accepté, § 7.4) (RG-11, RG-12, RG-15, RG-16, RG-25). *(réf. : ASVS 5.0 V7.3 / V7.4, Session Management CS, ADR 0001 D7)*
- **SEC-08** — CSRF sur les routes à cookie : `Origin` obligatoire et autorisé, en-tête `X-Requested-With`
  obligatoire, contrôle placé avant le filtre CORS, `POST` uniquement, `SameSite=Strict` (RG-13) ; login protégé par
  l'exigence `Content-Type: application/json` (preflight CORS). *(réf. : A01:2025, ASVS 5.0 V3.5, CSRF Prevention CS,
  ADR 0002 D3)*
- **SEC-09** — Pas d'affectation de masse ni de fuite : `LoginRequest` limité à 2 champs (champs inconnus → 400),
  réponses sans hachage ni jeton de rafraîchissement, erreurs sans écho de valeur, `Cache-Control: no-store`.
  *(réf. : API3:2023, API8:2023, ASVS 5.0 V16.5)*
- **SEC-10** — Journalisation des événements de sécurité sur le logger nommé `security`, une ligne par événement au
  format `event=<EVENT> userId=<uuid|-> ip=<ip|->[ familyId=<uuid>][ reason=<RAISON>][ count=<n>]`, où `ip` est
  l'adresse complète `clientIp` passée au service (`getRemoteAddr()`, pas la clé /64 du limiteur). Les événements d'un
  bloc transactionnel sont journalisés **après** son commit (jamais un succès annulé). Jamais : mot de
  passe, identifiant saisi, hachage, poivre, jeton d'accès, jeton de rafraîchissement, valeur du cookie.
  *(réf. : A09:2025, ASVS 5.0 V16.2 / V16.3, Logging CS)*

  | Événement | Niveau | `userId` | `familyId` | Autres champs |
  |---|---|---|---|---|
  | `LOGIN_SUCCESS` | INFO | uuid | famille créée | — |
  | `LOGIN_FAILURE` | INFO | uuid du compte trouvé à l'étape 2 de RG-24 (y compris pour `CREDENTIALS_CHANGED`, même si le compte a été supprimé ou modifié depuis, AC-68 (c)) ; `-` si aucun compte non supprimé n'a été trouvé à l'étape 2 | — | `reason` ∈ `UNKNOWN_USER`, `BAD_PASSWORD`, `DISABLED`, `LOCKED`, `NO_ROLE`, `CREDENTIALS_CHANGED` (RG-25 : `registerSuccessfulLogin` = 0) ; causes cumulées → priorité `DISABLED` > `NO_ROLE` > `LOCKED` ; non émis si `verify` / `dummyVerify` lève une exception |
  | `ACCOUNT_LOCKED` | WARN | uuid | — | — (émis seulement par la requête dont `registerFailedLogin` a posé le verrou, déterminé par `findLockedUntilById` non null dans la même transaction, § 5.4–5.5 : exactement une fois par verrouillage, même en concurrence ; écrit **après** le commit et **après** le `LOGIN_FAILURE` de la même requête) |
  | `LOGIN_RATE_LIMITED` | WARN | `-` | — | — |
  | `PASSWORD_REHASHED` | INFO | uuid | — | — (seulement si `updatePasswordHash` = 1, après commit) |
  | `PASSWORD_REHASH_FAILED` | WARN | uuid | — | — |
  | `REFRESH_SUCCESS` | INFO | uuid | famille | — |
  | `REFRESH_REJECTED` | INFO | uuid si la famille est connue, sinon `-` | famille si connue | `reason` ∈ `NO_COOKIE`, `UNKNOWN_TOKEN`, `FAMILY_REVOKED`, `FAMILY_EXPIRED`, `TOKEN_EXPIRED`, `USER_DISABLED` |
  | `REFRESH_REUSE_DETECTED` | WARN | uuid | famille | — |
  | `LOGOUT` | INFO | uuid si jeton connu, sinon `-` | famille si connue | — (écrit par `TransactionSynchronization.afterCommit` de la transaction de `logout`, § 5.5) |
  | `SESSIONS_REVOKED` | INFO | uuid | — | `ip=-`, `reason` = `SessionRevocationReason`, `count` = familles révoquées ; écrit par `TransactionSynchronization.afterCommit` de la transaction englobante (§ 5.5) |
  | `CSRF_REJECTED` | WARN | `-` | — | — |

  Mot de passe faux (`verify` = `MISMATCH`) : toujours `LOGIN_FAILURE reason=BAD_PASSWORD`, y compris quand
  `registerFailedLogin` renvoie 0 (compte verrouillé entre-temps par une requête concurrente). Le 5ᵉ échec produit
  `LOGIN_FAILURE reason=BAD_PASSWORD` **puis** `ACCOUNT_LOCKED`. Ordre unique, valable partout (RG-24, § 5.5,
  AC-SEC-08, AC-49) : commit de l'écriture d'échec → `LOGIN_FAILURE` → `ACCOUNT_LOCKED` (si posé) → `recordFailure` →
  `401`. Aucun événement du login n'est journalisé à l'intérieur d'un bloc `TransactionTemplate`.

- **SEC-11** — Consommation de ressources : taille des champs bornée (100 / 128), hachages Argon2 simultanés limités
  avec `503` (RG-21), aucun hachage pour une IP bloquée, table du limiteur bornée (RG-05), **aucune transaction ni
  connexion DB tenue pendant l'attente du sémaphore, le poivre, Argon2 et la signature Transit** (§ 5.5 : une rafale
  de logins / refresh ou un OpenBao lent ne peut pas épuiser le pool de connexions de toute l'API), aucune résolution
  DNS de l'adresse cliente (RG-05). *(réf. : API4:2023, ADR 0002 D4)*
- **SEC-12** — Gestion des conditions exceptionnelles : OpenBao indisponible (poivre / signature) → échec fermé
  (`500` générique avec `errorId`, aucune authentification accordée, aucun détail technique), y compris pour un
  identifiant inconnu (pas d'oracle). Vérification d'un jeton d'accès : aucun appel OpenBao (cache seul) ; clé
  introuvable ou erreur du fournisseur de clés → `401` (échec fermé, sans révéler l'état d'OpenBao à un anonyme).
  *(réf. : A10:2025)*
- **SEC-13** — IP cliente fiable pour la limitation : l'adresse utilisée par RG-05 et journalisée est celle que
  résout Tomcat `RemoteIpValve` (`X-Forwarded-For` accepté seulement depuis `TRUSTED_PROXIES`) ; le code ne lit jamais
  d'en-tête d'IP lui-même ; la chaîne de déploiement respecte PRE-01 (§ 5.9), pour qu'un attaquant ne puisse ni
  usurper une IP (contourner RG-05) ni bloquer tout le monde via l'IP d'un relais (déni de service global). *(réf. :
  API4:2023, A02:2025 Security Misconfiguration, ASVS 5.0 V6.3 / V13 Configuration, Authentication CS)*

### 7.3 Critères d'acceptation sécurité

- **AC-SEC-01** (SEC-01) — *Given* un utilisateur créé avec le mot de passe `Correct-Horse-Battery-9` *Then*
  `password_hash` ne contient ni le mot de passe ni son encodage base64, commence par `p<N>$$argon2id$` ; *And* si l'on
  stocke `p<N>$` + un hachage calculé par `Argon2PasswordEncoder` directement sur le mot de passe (sans poivre), le
  login avec ce mot de passe répond `401`.
- **AC-SEC-02** (SEC-02, RG-24) — *Given* `@MockitoSpyBean PasswordHasher` *When* login avec un identifiant inconnu, puis avec
  un compte désactivé, puis avec un compte verrouillé, puis avec un compte sans rôle *Then* chaque fois `dummyVerify`
  est appelé exactement une fois et `verify` jamais ; *When* login avec un compte actif et un mauvais mot de passe
  *Then* `verify` appelé une fois, `dummyVerify` jamais. Les corps des cinq réponses `401` sont identiques.
- **AC-SEC-03** (SEC-03, RG-24) — *Given* une IP bloquée (AC-10) *When* une 22ᵉ tentative *Then* `429` et ni
  `PasswordHasher.verify` ni `dummyVerify` n'est appelé (spy). Voir aussi AC-53, AC-54, AC-61, AC-65.
- **AC-SEC-04** (SEC-04) — AC-23 (a) à (k) et AC-24 ; *And* un JWT valide présenté via un cookie nommé
  `Authorization` ou `access_token` (sans en-tête) → `401`.
- **AC-SEC-05** (SEC-05) — Après login et refresh : aucun corps de réponse ne contient la valeur du cookie ;
  `refresh_token.token_hash` ne contient jamais la valeur brute ; l'attribut `HttpOnly` est présent sur tous les
  `Set-Cookie` `__Secure-refresh` (émission et effacement).
- **AC-SEC-06** (SEC-06) — AC-29, AC-30, AC-46, AC-48.
- **AC-SEC-07** (SEC-07) — AC-31, AC-32, AC-34, AC-35, AC-41, AC-56, AC-68, AC-69.
- **AC-SEC-08** (SEC-10) — *Given* capture des logs (`OutputCaptureExtension`) *When* login échoué avec
  `{"username":"mallory-probe","password":"Wr0ng-P4ssw0rd-XYZ"}` *Then* la sortie contient `event=LOGIN_FAILURE` et
  `reason=UNKNOWN_USER`, et ne contient ni `mallory-probe` ni `Wr0ng-P4ssw0rd-XYZ` ; *When* login réussi puis refresh
  *Then* la sortie contient `event=LOGIN_SUCCESS` et `event=REFRESH_SUCCESS`, et ne contient ni le mot de passe, ni le
  jeton d'accès, ni la valeur du cookie, ni `password_hash` ; *When* réutilisation (AC-29) *Then*
  `event=REFRESH_REUSE_DETECTED` avec `familyId=<id>` ; *When* rejet CSRF (AC-37) *Then* `event=CSRF_REJECTED` ;
  *When* 5ᵉ échec (AC-06) *Then* `event=LOGIN_FAILURE` avec `reason=BAD_PASSWORD` puis, plus loin dans la sortie,
  `event=ACCOUNT_LOCKED userId=<alice>`. *Given* `erin` désactivée, sans rôle et verrouillée (`locked_until` futur)
  *When* login *Then* `event=LOGIN_FAILURE userId=<erin>` avec `reason=DISABLED` ; *Given* `frank` actif, sans rôle et
  verrouillé *Then* `reason=NO_ROLE`. *Given* rotation du poivre et `@MockitoSpyBean UserRepository` dont
  `updatePasswordHash(…)` renvoie `0` (`doReturn(0)`, simule un hachage modifié entre-temps) *When* login réussi
  *Then* `200`, `password_hash` inchangé, et la sortie ne contient pas `event=PASSWORD_REHASHED` ; sans ce stub,
  le même scénario produit `event=PASSWORD_REHASHED userId=<alice>`.
- **AC-SEC-09** (SEC-08) — AC-37, AC-38, AC-39, AC-40 ; *And* login avec `Content-Type: application/x-www-form-urlencoded`
  → `415`.
- **AC-SEC-10** (SEC-09) — Réponses de login / refresh : en-tête `Cache-Control` contenant `no-store` ; corps sans
  clé `password`, `passwordHash`, `refreshToken` ; AC-13 et AC-14.
- **AC-SEC-11** (SEC-11) — AC-45, AC-13 (bornes de taille) et AC-54 (table bornée).
- **AC-SEC-12** (SEC-12) — *Given* un `PasswordPepper` (resp. `JwtKeyProvider.sign`) qui lève `CryptoException`
  (`@MockitoBean`, en place dès le démarrage du contexte) *When* login avec des identifiants corrects *Then* `500`, `title` = `Internal Server Error`,
  propriété `errorId` présente, aucun `Set-Cookie`, aucune famille créée ; *And* avec le `PasswordPepper` en erreur,
  login avec un identifiant **inconnu** → `500` avec le même `title` / `detail` (seul `errorId` diffère), et aucun
  échec IP enregistré : `@MockitoSpyBean LoginRateLimiter`, `recordFailure` **jamais** appelé. *And* `JwtKeyProvider.publicKey` en erreur → AC-60 (`401`).
  Voir aussi AC-62, AC-63, AC-64.
  **Création d'alice (décision unique, déroge à la convention du § 4 pour la variante `PasswordPepper`)** : le bean
  `PasswordHasher` du contexte ne peut pas hacher (poivre simulé). Le hachage est pré-calculé par une instance
  **distincte**, hors contexte Spring : `new Argon2PasswordHasher(new InMemoryPasswordPepper(…), authProperties)
  .hash("<bon>")` (une instance propre de `InMemoryPasswordPepper`, **pas** le bean ; `authProperties` = bean
  `AuthProperties` injecté), puis `User.builder()….passwordHash(<ce hachage>)…build()` et `UserRepository.save`. Seul
  compte : le hachage est bien formé (`p<n>$$argon2id$…`, RG-14), donc `verify` atteint le poivre simulé. Ni
  `@MockitoSpyBean` avec stub posé après coup, ni hachage écrit en dur, ni insertion SQL. Variante `JwtKeyProvider.sign` :
  le poivre est réel, alice est créée selon la convention du § 4.
- **AC-SEC-13** (SEC-11, RG-24) — *Given* `@MockitoSpyBean PasswordPepper` qui, à chaque appel de `pepper(…)`, et
  `@MockitoSpyBean JwtKeyProvider` qui, à chaque appel de `sign(…)`, enregistrent
  `TransactionSynchronizationManager.isActualTransactionActive()` avant de déléguer *When* successivement : login avec
  un identifiant inconnu, login d'alice avec un mauvais mot de passe, login d'alice réussi après rotation du poivre
  (re-hachage, AC-16), puis refresh avec le cookie obtenu *Then* les réponses sont `401`, `401`, `200`, `200` et
  **toutes** les valeurs enregistrées sont `false` (aucune transaction active pendant le poivre — donc pendant
  `verify` / `dummyVerify` / `hash` — ni pendant la signature, au login comme au refresh).
- **AC-SEC-14** (SEC-13) — AC-66 (IP résolue depuis un relais de confiance, format WAF `<client>, <client>`) et AC-67
  (en-tête ignoré depuis une source non fiable) ; *And* côté chaîne, CHK-01 et CHK-02 de `docker/test-prod/check.sh`
  (§ 5.9) passent sur `./start.sh --test-prod`.

### 7.4 Risques résiduels / hors périmètre

- Jeton d'accès non révocable avant expiration (≤ 5 min) après logout, révocation ou désactivation : vérification
  sans état, choix ADR 0001 D7. La claim `sid` permettrait une liste de révocation ultérieure (Q4).
- Limitation IP en mémoire : perdue au redémarrage et non partagée entre instances (monolithe mono-instance). Table
  bornée : un attaquant disposant de très nombreuses adresses (> 10 000 /64 ou IPv4) peut faire évincer des entrées ;
  atténué par le coût Argon2 de chaque échec, la limitation HAProxy et le verrouillage par compte.
- Verrouillage par compte exploitable en déni de service ciblé (verrouiller un compte connu) : atténué par la durée
  courte (15 min) et la limitation IP ; les sessions ouvertes ne sont pas affectées.
- Refresh concurrents légitimes (plusieurs onglets) traités comme une réutilisation : le front doit sérialiser les
  refresh (un seul en vol, coordination inter-onglets) — Q3.
- Logout concurrent d'un refresh : le refresh peut émettre un jeton dans une famille révoquée à l'instant suivant ;
  ce jeton est inutilisable (étape 3 de RG-22) — sans conséquence.
- Plusieurs cookies `__Secure-refresh` (dépôt d'un cookie par un sous-domaine compromis, « cookie tossing ») : seul le
  premier est lu ; le préfixe `__Host-` (qui l'empêcherait) imposerait `Path=/` et exposerait le cookie à toute l'API —
  écarté (ADR 0002 D2).
- OpenBao indisponible = plus de login ni de refresh (risque accepté, `Next.md`). Les jetons d'accès déjà émis restent
  vérifiés depuis le cache ; si le chargement initial du cache a échoué au démarrage, tout jeton d'accès reçoit `401`
  jusqu'au premier login / refresh réussi (échec fermé).
- Prérequis ADR 0002 : front et API sur le même site enregistré (`SameSite=Strict`).
- **IP cliente mal résolue** (PRE-01, § 5.9) : si `TRUSTED_PROXIES` ne couvre pas le relais qui se connecte à l'API,
  ou si un relais écrase `X-Forwarded-For`, toutes les requêtes portent l'IP d'un relais et RG-05 devient un blocage
  **global** (20 échecs de n'importe qui → `429` pour tous pendant 5 min). Vérification : AC-66 / AC-67 (code),
  CHK-01 / CHK-02 (chaîne test-prod) ; en prod, CHK-01 à rejouer après chaque changement de topologie. Inversement,
  une regex trop large permettrait d'usurper une IP si un relais non maîtrisé laissait passer `X-Forwarded-For`.
- **Écart temporel résiduel** (accepté) : le chemin `MISMATCH` d'un compte existant ajoute une transaction d'écriture
  courte (`registerFailedLogin` + `findLockedUntilById` + commit), absente des chemins « inconnu / désactivé /
  verrouillé / sans rôle ». L'écart (quelques ms) est noyé dans la variance d'Argon2 (~centaines de ms) et ne peut être
  exploité statistiquement qu'au prix de nombreuses tentatives, freinées par RG-04 et RG-05. Pas de compensation
  artificielle.
- **Retrait d'une version de poivre** : relever `min_decryption_version` de la clé Transit `password-pepper` rend
  inutilisable tout hachage d'une version inférieure (`500` à chaque login du compte, et `500` ≠ `401` révèle
  l'existence du compte). Règle d'exploitation : **ne jamais** relever `min_decryption_version` de `password-pepper`
  avant que tous les hachages aient été re-hachés (`password_hash` ne commençant plus par `p<ancienne>$`) ou avant une
  réinitialisation forcée des mots de passe concernés (002). À reporter dans `docs/ops/OPENBAO.md` (action utilisateur).
- **Succès concurrent d'un verrouillage** (RG-04, décision) : un login dont le mot de passe a été vérifié avant la pose
  du verrou par une requête concurrente réussit et efface ce verrou. Accepté : l'attaquant ne gagne rien (il faut le bon
  mot de passe) et le verrou n'a de sens que contre les tentatives suivantes. Cette tolérance est **limitée au verrou** :
  un changement de hachage, une désactivation ou une suppression concurrents font échouer le login (RG-25, AC-68).
- **Garantie de RG-25 conditionnée à 002** : elle ne tient que si 002 met à jour `app_user` avant `revokeAllSessions`
  dans la même transaction (RG-16) et si l'isolation reste `READ COMMITTED` (défaut PostgreSQL). Le moment où
  l'`UPDATE app_user` est émis ne dépend plus de 002 : `revokeAllActiveByUserId` (`flushAutomatically = true`)
  flushe les modifications en attente avant la révocation, que 002 écrive par dirty checking, `SoftDeleter` ou requête
  explicite (AC-69). Reste à vérifier dans l'analyse et la revue de 002 : l'ordre (modification **puis** appel) et la
  transaction commune.
- **Refresh concurrent d'un `revokeAllSessions`** (accepté, mise à jour 6) : si 002 révoque la famille entre la phase A
  et la phase C d'un refresh (RG-22), `markUsed` renvoie `1` et le refresh répond `200` avec un **nouveau jeton d'accès**
  émis après le changement de mot de passe / la désactivation. Le cookie émis est inutilisable (famille révoquée, étape 3
  de RG-22). Effet borné par la durée du jeton d'accès (≤ 5 min, même nature que Q4) ; pas de condition supplémentaire
  sur la phase C (elle réduirait la fenêtre sans la fermer).
- **Retrait concurrent de tous les rôles** pendant un login : non couvert par la condition de `registerSuccessfulLogin`
  (les rôles sont dans `app_user_role`). Effet borné : le jeton d'accès émis porte les rôles lus à l'étape 2 (≤ 5 min,
  même risque que ci-dessus) et le premier refresh révoque la famille (`USER_DISABLED`, étape 7 de RG-22). Accepté.
- **Écarts temporels résiduels de `verify`** (acceptés, données anormales ou exploitation ponctuelle) : (a) un hachage
  stocké mal formé (données corrompues) renvoie `MISMATCH` sans poivre ni Argon2 : réponse quasi instantanée qui révèle
  l'existence du compte ; (b) pendant une rotation du poivre, `verify` fait deux appels au poivre (`pepper(raw)` puis
  `pepper(raw, versionStockée)`) contre un seul pour `dummyVerify`, jusqu'au re-hachage du compte au login suivant.
  Écarts de quelques ms (b) à quelques centaines de ms (a), sur des comptes en nombre fini ; pas de compensation
  artificielle (AC-18 inchangé).
- **WAF (OWASP CRS 4.25, paranoïa 2) et mots de passe** : un mot de passe contenant des séquences ressemblant à de
  l'injection SQL / shell (`'`, `--`, `;`, `` ` ``, `$(`) peut déclencher une règle CRS : le WAF répond `403` avant
  l'API et l'utilisateur ne peut pas se connecter (le chemin `/refresh` n'est pas concerné : pas de corps). **Aucune
  exclusion CRS dans cette feature** (pas de modification de la configuration du WAF, pas de contrôle `check.sh`
  supplémentaire) ; décision à prendre (Q12).
- Signature Transit hors transaction (§ 5.5) : un OpenBao lent ralentit les logins / refresh mais ne retient aucune
  connexion DB ; le refresh n'est pas borné par le sémaphore RG-21 (pas d'Argon2), seulement par les délais du client
  Spring Vault et la limitation HAProxy.

### 7.5 Données sensibles

Grille : `.claude/skills/sensitive-data/classification.md`.

| Donnée | Où (entité.champ / DTO / fichier) | Classe | Raison | Traitement |
|---|---|---|---|---|
| Mot de passe | `LoginRequest.password`, `UserSeeder` (`app.seed.demo-password`) | SECRET | sert à s'authentifier | jamais stocké ni journalisé ; poivre Transit + Argon2id ; `toString` masqué ; mot de passe de démonstration : variable `SEED_DEMO_PASSWORD` de `.env.dev`, profil `seed` (dev) uniquement — exception documentée à ADR 0001 D9 (RG-18) |
| Hachage du mot de passe | `User.passwordHash` / `app_user.password_hash` | SECRET (dérivé) | permettrait une attaque hors ligne sans poivre | jamais renvoyé, journalisé ni inclus dans un `toString` |
| Jeton de rafraîchissement | cookie `__Secure-refresh`, `IssuedTokens.refreshToken` | SECRET | permet d'obtenir des jetons d'accès | SHA-256 en base (`refresh_token.token_hash`), cookie `HttpOnly`, jamais dans un corps ni un log |
| Jeton d'accès (JWT) | `TokenResponse.accessToken`, en-tête `Authorization` | SECRET (bearer) | permet d'agir au nom de l'utilisateur | jamais journalisé ; ne contient aucune donnée `S2` (claims lisibles en clair) |
| Poivre | `PasswordPepper.Pepper.value` | SECRET | dérivé du mot de passe | jamais journalisé (`toString` masqué existant) |
| Identifiant de connexion | `User.username`, `LoginRequest.username` | S1 | identifie une personne | contrôle d'accès ; pas dans les logs (seul l'UUID y figure) ; `toString` de `LoginRequest` masqué |
| Rôles, dépôt | `User.roles`, `User.depotId`, claims `roles` / `depot_id`, `CurrentUserResponse` | S1 | rattachement professionnel | renvoyés uniquement à l'utilisateur lui-même |
| Compteurs et dates de connexion | `failed_login_count`, `locked_until`, `last_login_at` | S1 | comportement d'une personne | jamais exposés par l'API dans 001 |
| Sessions (familles / jetons : `user_id`, `created_at`, `absolute_expires_at`, `used_at`, `revoked_at`, raison ; claim `sid`) | `refresh_token_family`, `refresh_token`, JWT | S1 | historique de connexion et nombre d'appareils d'une personne | jamais exposées par l'API dans 001 ; purgées 7 jours après expiration / révocation (RG-17) |
| Adresse IP cliente | paramètre `clientIp`, logs `security`, mémoire du limiteur | S1 | donnée personnelle (RGPD) | finalité sécurité uniquement ; non stockée en base ; conservation = celle des logs (Q6) |
| UUID utilisateur / famille, `jti` | claims, logs | S0 | identifiants techniques | peuvent figurer dans les logs |

Aucune donnée `S2` : aucun chiffrement `CryptoService`, aucun nouveau `SensitiveDataType`, aucune colonne `_enc`.

- **SD-01** — Le mot de passe n'est jamais persisté ni journalisé ; seul `password_hash` (RG-14) est stocké.
  *(classe SECRET ; ADR 0002 D4/D5)*
- **AC-SD-01** (SD-01) — *Given* un login (réussi puis échoué) avec le mot de passe `Correct-Horse-Battery-9` *Then*
  aucune colonne texte de `app_user`, `refresh_token_family`, `refresh_token` ne contient cette valeur, et la sortie de
  log capturée non plus.
- **SD-02** — Le jeton de rafraîchissement n'est stocké que sous forme d'empreinte SHA-256 et ne circule que dans le
  cookie `HttpOnly`. *(classe SECRET ; ADR 0002 D2)*
- **AC-SD-02** (SD-02) — = AC-02 et AC-SEC-05.
- **SD-03** — Le jeton d'accès, le hachage, le poivre et la valeur du cookie n'apparaissent dans aucun log ni
  `ProblemDetail` ; `LoginRequest`, `TokenResponse`, `IssuedTokens` ont un `toString` masqué. *(classe SECRET)*
- **AC-SD-03** (SD-03) — = AC-SEC-08 ; *And* test unitaire : `toString()` de `LoginRequest`, `TokenResponse` et
  `IssuedTokens` ne contient ni le mot de passe, ni le jeton d'accès, ni le jeton de rafraîchissement.
- **SD-04** — Données `S1` minimisées : le JWT et `CurrentUserResponse` ne portent que id, rôles, dépôt (+ `sid`,
  `jti` techniques) ; aucune claim e-mail / nom / NISS (y compris après 002) ; les données de session ne sont pas
  exposées et sont purgées (RG-17). *(classe S1 ; RGPD art. 5.1.c et 5.1.e)*
- **AC-SD-04** (SD-04) — *Given* AC-01 *Then* la charge utile du JWT ne contient que les claims du § 5.8 (aucune
  autre clé) et `CurrentUserResponse` exactement les clés `id`, `roles`, `depotId` ; *And* AC-42 (purge).

Prérequis d'infrastructure OpenBao : aucun nouveau — clés Transit `jwt` (ecdsa-p256) et `password-pepper` (hmac) et
droits `transit/sign/jwt`, `transit/keys/jwt` (lecture), `transit/hmac/password-pepper` déjà présents dans
`docker/openbao/configure.sh` et `docker/openbao/policies/api.hcl`. Prérequis de déploiement : **PRE-01** (§ 5.9,
l'adresse IP cliente — donnée `S1` — doit être celle du client réel, condition de RG-05).

## 8. Hors périmètre

- CRUD des comptes, e-mail, nom, prénom, catégorie de permis, NISS, affectation à un dépôt, désactivation par l'admin
  → **002** (qui appelle `revokeAllSessions` et `PasswordHasher.hash`).
- Politique de mot de passe à la création / au changement (longueur ≥ 12, ≤ 128, sans règle de composition, liste de
  mots de passe compromis, **refus des mots de passe blancs** — le login exige `@NotBlank` et ≤ 128 caractères : un
  compte créé avec un mot de passe blanc ou plus long ne pourrait jamais se connecter) et changement de mot de passe en
  libre-service → **002** (voir Q2). Création d'un compte sans rôle également à refuser par 002.
- Réinitialisation de mot de passe oublié, MFA, « se souvenir de moi », liste des sessions actives d'un utilisateur.
- FK `app_user.depot_id → depot(id)` et rattachement des comptes de démonstration aux dépôts → **003**.
- Déverrouillage manuel d'un compte par un admin → 002.
- Liste de révocation des jetons d'accès (`jti` / `sid`).

## 9. Questions ouvertes

| # | Question | Hypothèse par défaut | Bloquante ? |
|---|---|---|---|
| Q1 | Ajout de la dépendance resource server OAuth2 (Nimbus JOSE) — non listée dans `CLAUDE.md` | **Tranchée (utilisateur, 2026-11-17) : acceptée.** Artefact Boot 4 : `spring-boot-starter-security-oauth2-resource-server`, version BOM ; à ajouter au tableau de la stack (§ 5.5) | Non |
| Q2 | Politique de mot de passe (ASVS : ≥ 12, ≤ 128 au moins 64, sans composition, liste de mots de passe compromis : fichier local ou service externe ?) ; refus des mots de passe blancs | Définie dans 002 ; liste locale embarquée (pas d'appel externe) ; mots de passe blancs refusés | Non pour 001 |
| Q3 | Plusieurs onglets / appareils : délai de grâce pour les refresh concurrents ? | Aucun (ADR 0002 strict) ; le front sérialise les refresh | Non |
| Q4 | Révocation immédiate des jetons d'accès (vérification de `sid` en base à chaque requête) ? | Non : 5 min de validité résiduelle acceptées (ADR 0001 D7) | Non |
| Q5 | Seuils anti-abus : 5 échecs / 15 min par compte, 20 échecs / 5 min par IP (un dépôt = une IP NAT partagée par de nombreux conducteurs) | Valeurs ci-dessus, configurables (`app.auth.login.*`) | Non |
| Q6 | Durée de conservation des logs de sécurité contenant l'IP (RGPD) | À fixer avec le client (proposition : 1 an) | Non |
| Q7 | Nombre de sessions simultanées par utilisateur | Illimité | Non |
| Q8 | Comptes de démonstration sans dépôt jusqu'à 003 (le `gestionnaire` ne gère alors aucun dépôt) | Oui ; 003 complète le seeder | Non |
| Q9 | Valeurs prod de `JWT_ISSUER` / `JWT_AUDIENCE` | URL publique de l'API / origine du front | Non |
| Q10 | OpenBao indisponible au login : `500` générique ou `503` avec `Retry-After` ? | `500` (comportement existant de `CryptoException`) | Non |
| Q11 | Topologie de production du WAF (conteneur dans `docker-compose.prod.yml` comme en test-prod, ou service sur l'hôte) et IP par laquelle il atteint l'API | Comme test-prod (WAF conteneurisé, IP fixe, `SET_REAL_IP_FROM` = HAProxy) ; `TRUSTED_PROXIES` = IP de HAProxy et du WAF ; CHK-01 rejoué sur le serveur avant ouverture. Bloquante pour la **mise en production** de 001, pas pour son développement | Non (dév.) / Oui (mise en prod) |
| Q12 | WAF en paranoïa 2 : faut-il une exclusion CRS ciblée pour le champ `password` de `POST /api/v1/auth/login` (et un contrôle `check.sh` associé, ex. mot de passe `x' OR '1'='1 --` → `401` et non `403`) ? | Pas d'exclusion dans 001 (risque accepté et consigné au § 7.4) ; à trancher avant la mise en production, éventuellement avec 002 (politique de mot de passe) | Non |

## Historique

| Date | Modification |
|---|---|
| 2026-11-17 | Création (analyse préparatoire 001, ADR 0002, `Next.md` phase 4) ; section Sécurité complète (owasp-check : SEC-01 à SEC-12) ; § 7.5 Données sensibles (sensitive-data : aucune donnée S2, SECRET hachés). |
| 2026-11-17 | Mise à jour après `reviews/001-authentification.review.md` (verdict À corriger), tous les points traités. **#1** (bloquant) constructeur explicite de `UserSeeder` (§ 5.5), validation dans `seed()` seulement, RG-18 / AC-43 / AC-44 reformulés. **#2** ordre des contrôles du refresh : RG-22 + AC-46, AC-47, AC-48. **#3** écritures atomiques sans `version` (RG-23, signatures § 5.4), « aucun 409 », AC-49, AC-50, AC-51. **#4** `InvalidSessionException` + gestionnaire dédié + `AuthCookies.cleared()` (§ 5.1, § 5.7). **#5** `AuthCsrfFilter` avant `CorsFilter`, contrôle `checkOrigin`, AC-38 / AC-39 durcis. **#6** `token_hash varchar(64)`. **#7** `@Builder.Default` (`enabled`, `failedLoginCount`, `roles`) + conventions de création des utilisateurs de test. **#8** `LoginRateLimiter.reset()` + IP propre par test (conventions § 4). **#9** AC-10 / AC-11 : identifiants inconnus, `Retry-After: 240`, borne exacte. **#10** `dummyVerify` = sémaphore + poivre + Argon2 (RG-03), AC-SEC-12 étendu (identifiant inconnu → 500). **#11** `TransitJwtKeyProvider` : constructeur public, règles de cache, AC-57 (IT). **#12** définition du verrou, remise à `null` d'un verrou expiré (RG-04, AC-52). **#13** bornes de fenêtre et causes comptées (RG-05, AC-11). **#14** hachage mal formé (regex) vs version de poivre refusée (RG-14, AC-18). **#15** critère `upgradeEncoding`, échec du re-hachage toléré (RG-14, AC-55). **#16** nom du starter Boot 4 corrigé, Q1 tranchée (acceptée par l'utilisateur), mise à jour de `CLAUDE.md` prévue. **#17** notes Nimbus (§ 5.8). **#18** `Retry-After` exposé en CORS (§ 5.1, AC-59). **#19** tableau des événements de sécurité (SEC-10), AC-SEC-08 étendu. **#20** IPv6 /64 et table bornée (RG-05, AC-53, AC-54, propriété `ip-max-tracked`), risque résiduel. **#21** `AuthCsrfFilter` délègue au `HandlerExceptionResolver`. **#22** sessions classées S1 (§ 7.5, SD-04). **#23** mots de passe blancs → § 8 / Q2. **#24** premier cookie lu (RG-08, AC-58, § 7.4). **#25** `AuthMapper.toTokenResponse` ; compte sans rôle = échec RG-03 / révocation au refresh (AC-56, AC-SEC-02). **#26** les doubles de test (`InMemoryPasswordPepper`, `InMemoryJwtKeyProvider`) relèvent de `src/test/`, illisible par l'analyste : le contrat fixe la capacité requise (rotation simulée, `currentKeyId()` = `"1"` au départ) sans imposer de nom de méthode (§ 4, conventions). |
| 2026-11-17 | Mise à jour 3 après la deuxième relecture (0 bloquant, 5 majeurs, 10 mineurs), tous les points traités. **#1** bornes JWT tranchées : tolérance 0, `exp` exclusif (`now < exp`), `iat <= now` ; `JwtTimestampValidator` proscrit, validateur dédié, vérificateur de claims Nimbus neutralisé, pas de `JwtValidators.createDefault*` (§ 5.8) ; `iat` = secondes tronquées ; `T0` à la seconde entière (§ 4). **#2** plus de `@Transactional` de classe sur `AuthServiceImpl` : `login` non transactionnel, `verify` / `dummyVerify` / `hash` hors transaction, écriture d'échec et étape 5 dans des `TransactionTemplate` courts, `refresh` seul en `noRollbackFor` (RG-24, § 5.5) ; `findByUsername` avec `@EntityGraph(roles)`, famille créée via `getReferenceById` (§ 5.4) ; SEC-11 étendue, **AC-SEC-13** (aucune transaction active pendant le poivre). **#3** hachage factice calculé dans le constructeur par `Argon2PasswordEncoder.encode` d'une chaîne aléatoire de 44 caractères, sans poivre ; le constructeur n'appelle jamais `PasswordPepper` (§ 5.5, RG-03). **#4** `token_hash` = SHA-256 des octets US-ASCII de la chaîne base64url de 43 caractères, formule Java écrite (RG-08, AC-02). **#5** `publicKey` ne lit que le cache (aucun appel OpenBao par requête, kid inconnu → `401`) ; rechargement uniquement via `currentKeyId()` (chemin de signature) et au démarrage ; toute exception de `publicKey` capturée par le sélecteur de clé → `401` (RG-07, § 5.5, § 5.7, § 5.8, SEC-12, AC-57 précisé, **AC-60**). **#6** `Max-Age` = `expires_at` du jeton − `now` (RG-08, `IssuedTokens`). **#7** AC-44 : `"elevenchars"` (11) refusé, `"twelve-chars"` (12) accepté. **#8** `findLockedUntilById` lu dans la même transaction après `registerFailedLogin` = 1 → `ACCOUNT_LOCKED` exactement une fois ; 5ᵉ échec = `LOGIN_FAILURE BAD_PASSWORD` puis `ACCOUNT_LOCKED` (§ 5.4, SEC-10, AC-SEC-08, AC-49). **#9** `currentUser()` sans transaction (plus d'annotation de classe). **#10** § 5.1 : `405`, `500` sur logout, `401` anonyme sur méthode non `POST`. **#11** `verify` : un appel `pepper(raw)`, second appel `pepper(raw, versionStockée)` seulement si les versions diffèrent (RG-14). **#12** analyse de l'IP par `InetAddress.ofLiteral`, jamais de DNS (RG-05, § 5.5). **#13** AC-49 : `max-concurrent-hashes=10` et Argon2 au plancher. **#14** AC-47 : jeton déjà utilisé dans une famille expirée → `401`, pas de révocation. **#15** voir #1 (`T0` à la seconde, instants dérivés par secondes entières). |
| 2026-11-17 | Mise à jour 4 après la troisième relecture (0 bloquant, 2 majeurs, 11 mineurs), tous les points traités. **#1** (majeur) RG-24 couverte : **AC-61** (`400` / `415` avant `429`, le limiteur n'est pas un filtre servlet), **AC-62** (échec de signature au login : compteur, `last_login_at`, hachage `p1$` inchangés, aucune famille, aucun échec IP), **AC-64** (exception du poivre : `failed_login_count` inchangé, aucun échec IP, `500` / `503`) ; AC-SEC-02, AC-SEC-03, AC-SEC-13 rattachés à RG-24. **#2** (majeur) chaîne vérifiée dans `haproxy.cfg`, `docker-compose.test-prod.yml`, `application-prod.yml` et le modèle nginx de l'image WAF : la chaîne test-prod transmet l'IP réelle (`<client>, <client>` résolu par `RemoteIpValve`), la prod réelle n'est pas garantie → **§ 5.9 / PRE-01** (prérequis explicite), `TRUSTED_PROXIES` test-prod élargi à HAProxy + WAF, contrôles **CHK-01 / CHK-02** dans `check.sh`, **SEC-13**, **AC-66 / AC-67** (Tomcat réel, relais de confiance / non fiable), **AC-SEC-14**, risque au § 7.4, **Q11** (topologie prod du WAF) ; pas de contrôle au démarrage (topologie inconnaissable par l'API, argumenté au § 5.9). **#3** identifiant hors format → chemin « inconnu », pas de `@Pattern` (RG-02, § 5.2, AC-04). **#4** décision : un succès vérifié l'emporte sur un verrou concurrent (RG-04, § 7.4). **#5** écart temporel du chemin `MISMATCH` accepté (§ 7.4). **#6** `PASSWORD_REHASHED` seulement si `updatePasswordHash` = 1, après commit (RG-14, SEC-10, AC-SEC-08). **#7** **AC-63** (échec de signature au refresh : `used_at` null, aucun `Set-Cookie`, cookie réutilisable). **#8** signature **hors transaction** au login (famille `assignId()` d'avance) et au refresh (phases A / B / C) ; RG-22 étapes 8 à 10 ; RG-24 étape 5 ; § 5.5 ; SEC-11 ; AC-SEC-13 étendu à `sign`. **#9** règle d'exploitation sur `min_decryption_version` de `password-pepper` (§ 7.4, report dans `OPENBAO.md` par l'utilisateur). **#10** AC-54 avec adresses complètes. **#11** observation par `@MockitoSpyBean LoginRateLimiter` (`recordFailure` jamais appelé) dans AC-SEC-12, AC-62, AC-64. **#12** **AC-65** (un succès ne remet pas le compteur IP à zéro). **#13** priorité `DISABLED` > `NO_ROLE` > `LOCKED` (RG-24, SEC-10, AC-SEC-08). |
| 2026-11-17 | Mise à jour 5 après la quatrième relecture (0 bloquant, 2 majeurs, 8 mineurs), tous les points traités, périmètre inchangé. **#1** (majeur) `registerSuccessfulLogin(id, verifiedHash, now)` conditionnelle (`password_hash = verifiedHash`, `enabled = true`, `deleted_at is null`), première écriture de la transaction de succès ; `0` → transaction annulée, `401` `Invalid credentials.` sans famille ni `Set-Cookie`, `LOGIN_FAILURE reason=CREDENTIALS_CHANGED` (nouvelle raison), échec IP compté, `failed_login_count` non incrémenté — nouvelle **RG-25**, RG-04 (décision limitée au verrou), RG-24 étape 5, § 5.4, § 5.5 étapes 7–8, SEC-07, SEC-10 ; **contrainte imposée à 002** (mise à jour de `app_user` puis `revokeAllSessions` dans la même transaction, `READ COMMITTED`) dans RG-16, § 2 et § 7.4 ; **AC-68** (hachage changé / désactivation / suppression pendant `verify`), rattaché à AC-SEC-07 ; retrait concurrent des rôles consigné au § 7.4. **#2** (majeur) ordre unique des journaux : § 5.5 étapes 3 et 4 réécrites (commit → `LOGIN_FAILURE` → `ACCOUNT_LOCKED` → `recordFailure` → `401`, aucune journalisation dans le bloc), RG-24 étapes 3–4, SEC-10 (tableau et paragraphe) alignés sur AC-SEC-08 / AC-49. **#3** contrôle de format avant le sémaphore (RG-14, RG-21) ; AC-45 avec un hachage bien formé. **#4** écarts temporels (a) hachage mal formé, (b) double poivre en rotation consignés au § 7.4 comme résiduels acceptés (AC-18 inchangé). **#5** conventions § 4 : aucun `@Transactional` sur les tests d'intégration de 001, nettoyage SQL explicite. **#6** décision : `SEED_DEMO_PASSWORD` → `app.seed.demo-password`, lue seulement par `UserSeeder` (profil `seed`, dev, comptes absents en prod), **exception documentée à ADR 0001 D9**, aucune mécanique KV (RG-18, § 5.5, § 7.5). **#7** WAF paranoïa 2 : pas d'exclusion CRS dans 001, risque au § 7.4 et **Q12**. **#8** § 5.1 : `403` CORS (corps texte, non `ProblemDetail`) sur login / `me`, comportement existant ni testé ni modifié. **#9** `SESSIONS_REVOKED` via `TransactionSynchronization.afterCommit` de la transaction physique englobante (RG-16, § 5.5, SEC-10), AC-41 étendu (log `count=2`, aucune ligne si annulation). **#10** conventions § 4 : tout contexte Spring de test importe `TestcontainersConfig` ou fournit `PasswordPepper` et `JwtKeyProvider`. |
| 2026-11-17 | Mise à jour 6 après la cinquième relecture (0 bloquant, 1 majeur, 5 mineurs), tous les points traités, corrections minimales, périmètre inchangé. **#1** (majeur) `RefreshTokenFamilyRepository.revokeAllActiveByUserId` déclarée `@Modifying(flushAutomatically = true)` : les modifications en attente de la transaction englobante (dirty checking, `SoftDeleter`) sont envoyées en base avant la révocation, le verrou `app_user` précède donc la révocation quel que soit le mode d'écriture de 002 (RG-16, § 5.4, § 5.5 `revokeAllSessions`, § 7.4 « Garantie de RG-25 ») ; **AC-69** (désactivation / suppression logique par entité gérée sans flush, puis `revokeAllSessions`, lecture `JdbcTemplate` dans la même transaction), rattaché à AC-SEC-07. **#2** SEC-10 : `userId` de `LOGIN_FAILURE` = compte trouvé à l'étape 2 de RG-24, y compris pour `CREDENTIALS_CHANGED` (aligné sur AC-68 (c)). **#3** `BearerTokenResolver` : `null` ssi méthode `POST` **et** chemin exact de RG-20 ; autre méthode → jeton lu (AC-40 → `405`) (§ 5.5, RG-20). **#4** `LOGOUT` journalisé par `TransactionSynchronization.afterCommit` comme `SESSIONS_REVOKED` ; `REFRESH_REJECTED` / `REFRESH_REUSE_DETECTED` écrits après le commit de la phase A ou C (§ 5.5, SEC-10). **#5** refresh concurrent d'un `revokeAllSessions` (jeton d'accès ≤ 5 min émis après la révocation) consigné comme risque accepté au § 7.4, sans condition supplémentaire sur la phase C ; SEC-07 nuancé. **#6** AC-SEC-12 : décision unique — `@MockitoBean PasswordPepper` dès le démarrage, hachage d'alice pré-calculé par un `Argon2PasswordHasher` distinct construit avec une instance propre de `InMemoryPasswordPepper` (ni stub tardif, ni hachage en dur, ni SQL). |
| 2026-11-17 | Revue 6 : **OK** (0 bloquant, 0 majeur, 2 mineurs). Statut → `validée`. Mineur 1 traité : `User` annoté `@DynamicUpdate` (§ 5). Mineur 2 (exceptions de flush propagées par `revokeAllSessions`) : à reprendre dans l'analyse 002. |
