# Checklist OWASP — API REST Spring Boot

Points à examiner. Ne retenir que ceux qui s'appliquent à la feature analysée.

## Autorisation (API1 BOLA, API3 BOPLA, API5 BFLA — Broken Access Control)

- Chaque accès à une ressource par id vérifie que l'utilisateur courant y a droit (propriété / rôle), côté service.
- Réponse `404` plutôt que `403` quand révéler l'existence de la ressource est un problème.
- Champs modifiables explicitement listés dans le DTO de requête (pas de mass assignment : `role`, `ownerId`,
  `status`… jamais acceptés du client s'ils ne doivent pas l'être).
- Champs exposés explicitement listés dans le DTO de réponse (pas de hash, token, données internes).
- Endpoints d'administration protégés par rôle, refus par défaut (`deny by default`).

## Authentification (API2 — Identification & Authentication Failures)

- Mots de passe : hachage adaptatif (Argon2id / bcrypt) via `PasswordEncoder`, longueur min 8 (12+ recommandé),
  max ≥ 64, pas de règles de composition arbitraires, vérification contre une liste de mots de passe compromis.
- Messages d'échec de connexion génériques (pas d'énumération de comptes), y compris sur « mot de passe oublié ».
- Limitation des tentatives (par compte et par IP), délai progressif ou verrouillage temporaire.
- Tokens (JWT) : algorithme fixé côté serveur (pas de `none`, pas de confusion d'algorithme), durée de vie courte,
  vérification `exp`/`iss`/`aud`, rotation des refresh tokens, révocation possible, secret ≥ 256 bits hors du code.
- Réinitialisation de mot de passe : token aléatoire à usage unique, durée courte, invalidation des sessions.

## Validation des entrées & injection (Injection)

- Validation Jakarta sur tous les DTO (type, taille, format, liste blanche pour les énumérations).
- Requêtes paramétrées uniquement (Spring Data / JPQL avec paramètres) ; jamais de concaténation dans
  `@Query(nativeQuery = true)` ni dans les tris dynamiques (liste blanche des champs de `Sort`).
- Pas de commande système ni d'évaluation d'expression construite à partir d'une entrée utilisateur.

## Consommation de ressources (API4 — Unrestricted Resource Consumption)

- Pagination obligatoire avec taille max (`size` ≤ 100 par ex.).
- Limites de taille des requêtes, des uploads, des champs texte.
- Rate limiting sur les endpoints coûteux ou sensibles (login, inscription, envoi d'e-mail, export).

## Flux métier sensibles (API6)

- Anti-automatisation sur les flux abusables (création de comptes, réservations, achats) : quotas, délais, captcha côté front.

## SSRF (API7)

- Toute URL fournie par l'utilisateur et appelée par le serveur : liste blanche de domaines/schémas, blocage des
  IP privées/link-local/metadata cloud, pas de suivi de redirections non contrôlé.

## Configuration (API8 — Security Misconfiguration)

- CORS : origine(s) explicite(s) du front Angular, pas de `*` avec credentials.
- En-têtes de sécurité (HSTS, `X-Content-Type-Options`, `Cache-Control: no-store` sur données sensibles).
- Erreurs : `ProblemDetail` sans stack trace, sans message SQL, sans détail d'implémentation.
- Actuator restreint (`health`, `info`) en prod ; Swagger désactivé en prod.

## Inventaire (API9)

- Endpoints versionnés (`/api/v1`), anciennes versions retirées ou documentées.

## Consommation d'API tierces (API10)

- Réponses d'API externes validées comme des entrées utilisateur ; timeouts ; TLS vérifié.

## Données sensibles & cryptographie (Cryptographic Failures)

- Classification des données ; données personnelles minimisées (RGPD : finalité, durée de conservation, droit à l'effacement).
- Chiffrement en transit (TLS) ; secrets via variables d'environnement / gestionnaire de secrets, jamais commités.
- Aucune donnée sensible dans les logs (mots de passe, tokens, données personnelles complètes).

## Fichiers (upload / download)

- Liste blanche d'extensions et vérification du type réel (magic bytes), taille max, nom de fichier régénéré
  côté serveur, stockage hors webroot, pas de path traversal sur le download.

## Journalisation & alerte (Logging & Alerting Failures)

- Journaliser les événements de sécurité (échecs d'authentification, refus d'autorisation, changements de droits)
  avec horodatage et identifiant utilisateur, sans données sensibles.

## Intégrité & dépendances (Software Supply Chain Failures)

- Dépendances ajoutées pour la feature : versions maintenues, sans CVE connue (voir skill audit-deps).
