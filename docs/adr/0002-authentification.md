# ADR 0002 — Authentification : transport des tokens et stockage des mots de passe

| | |
|---|---|
| Statut | **Accepté** — 16/11/2026 |
| Décideurs | Philippe Stons (+ Claude Code) |
| Complète | ADR 0001 (D4 signature ES256 via Transit, D7 durées : accès 5 min, refresh 8 h glissant / 24 h max) |
| Résout | Questions ouvertes Q1 et Q2 de l'ADR 0001 |

## Contexte

Application haute sécurité (critique). Front Angular et API sur **le même site** mais des **origines différentes**
(ex. `app.exemple.be` / `api.exemple.be`). Références : OWASP Cheat Sheets *JSON Web Token*, *Session Management*,
*Cross-Site Request Forgery Prevention*, *Password Storage* ; ASVS V2 (authentification), V3 (sessions).

## Décisions

### D1 — Token d'accès : en mémoire

JWT ES256 (5 min) renvoyé dans le **corps** de la réponse, conservé **en mémoire** par Angular (jamais
`localStorage` / `sessionStorage`), envoyé en `Authorization: Bearer`. Perdu au rechargement de page → un appel
`/auth/refresh` le régénère silencieusement.

### D2 — Refresh token : cookie `HttpOnly` (Q1)

```
Set-Cookie: __Secure-refresh=<256 bits aléatoires, base64url>; HttpOnly; Secure; SameSite=Strict;
            Path=/api/v1/auth; Max-Age=<fin de session>
```

- **Jamais lisible par JavaScript** : un XSS ne peut pas l'exfiltrer.
- Préfixe `__Secure-` (Secure obligatoire) ; pas `__Host-`, qui imposerait `Path=/` alors qu'on limite le cookie aux
  seules routes d'authentification. Pas d'attribut `Domain` : cookie limité à l'hôte de l'API.
- Opaque (pas un JWT), stocké **haché** (SHA-256) en base, à usage unique, organisé en **familles** : chaque refresh
  émet un nouveau token et invalide le précédent ; la **réutilisation** d'un token déjà utilisé révoque toute la
  famille (vol détecté). Session : 8 h d'inactivité, 24 h au total.

### D3 — CSRF sur `/auth/refresh` et `/auth/logout` (seules routes utilisant le cookie)

Défense en profondeur, sans jeton synchronisé (le mécanisme XSRF intégré d'Angular ne fonctionne qu'en même origine) :

1. `SameSite=Strict` : le cookie n'est jamais envoyé par une requête initiée depuis un autre site ;
2. **vérification de `Origin`** : obligatoire et égal à une origine CORS autorisée, sinon 403 ;
3. **en-tête personnalisé obligatoire** (ex. `X-Requested-With: XMLHttpRequest`) : impose un preflight CORS, que
   seules les origines autorisées passent ;
4. méthode `POST` uniquement.

Conséquence CORS : `allow-credentials: true` **uniquement** sur `/api/v1/auth/**` ; le reste de l'API reste sans
credentials (le token d'accès voyage dans l'en-tête `Authorization`). Le mode strict (prod) continue d'exiger des
origines HTTPS explicites.

**Prérequis de déploiement** : front et API sur le même site enregistré. Sinon, cette décision est à revoir
(`SameSite=None` + jeton anti-CSRF).

### D4 — Mots de passe : Argon2id (Q2)

- `Argon2PasswordEncoder` (Spring Security) — dépendance **Bouncy Castle** (`bcprov-jdk18on`).
- Paramètres initiaux : **m = 64 Mio, t = 3, p = 1**, sel 16 octets, hachage 32 octets ; à **calibrer** sur le
  serveur de production (cible ≈ 250 ms par hachage). Plancher OWASP : m = 19 Mio, t = 2.
- Mise à niveau transparente : à chaque connexion réussie, si les paramètres stockés sont inférieurs aux paramètres
  courants, le hachage est recalculé (`upgradeEncoding`).
- Le coût mémoire impose la limitation de débit (HAProxy + API) et un nombre maximal de hachages simultanés.

### D5 — Poivre dans OpenBao

Avant hachage : `poivre = Transit HMAC-SHA256(clé password-pepper, mot de passe)`, puis Argon2id sur le poivre.

- Clé `password-pepper` (type `hmac`) **non exportable** : une fuite de la base seule ne permet **aucune** attaque
  hors ligne, même sur un mot de passe faible.
- Le hachage stocké mémorise la **version** de la clé de poivre (ex. `p1$` + chaîne Argon2) : après rotation de la
  clé, vérification avec l'ancienne version puis re-hachage avec la nouvelle à la connexion suivante.
- Politique `api` : `transit/hmac/password-pepper` uniquement. Coût : un appel OpenBao par connexion / changement de
  mot de passe (OpenBao est déjà requis pour signer le JWT).

## Conséquences pour l'implémentation (phase 4)

- Infra (avant la feature) : clé `password-pepper` dans `configure.sh` + droit dans `policies/api.hcl` ; dépendance
  Bouncy Castle ; CORS avec credentials limité à `/api/v1/auth/**` ; type `SensitiveDataType` ou service dédié pour
  le poivre.
- Feature (workflow `/analyse-feature` → … → `/mutation-test`) : endpoints login / refresh / logout, familles de
  refresh tokens, détection de réutilisation, verrouillage et limitation des tentatives, journalisation des
  événements de sécurité, réponses génériques (pas d'énumération de comptes).

## Alternatives écartées

| Alternative | Raison |
|---|---|
| Refresh token dans le corps + `sessionStorage` | Exfiltrable par XSS (8 h de validité) |
| Refresh token dans le corps + mémoire seule | Reconnexion à chaque rechargement de page |
| Jeton anti-CSRF synchronisé (`XSRF-TOKEN`) | Non pris en charge nativement par Angular en origines différentes ; les défenses D3 suffisent avec `SameSite=Strict` |
| bcrypt | Moins résistant aux GPU, troncature à 72 octets ; Argon2id est le premier choix OWASP |
| Sans poivre | Une fuite de la base permettrait des attaques hors ligne |
