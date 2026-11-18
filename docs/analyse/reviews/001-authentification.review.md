# Revue — 001-authentification
Date : 2026-11-17 · Analyse relue : brouillon, dernière modification 2026-11-17 (mise à jour 6, après la revue 5)
**Verdict : OK** (0 bloquant, 0 majeur, 2 mineurs)

Sources croisées : analyse client `delvaux-gestion-de-flotte.md` (via l'en-tête), ADR 0001 / 0002, `CLAUDE.md`, `INDEX.md`,
`src/main` (`SoftDeleter`, `SoftDeletableEntity`, `BaseEntity`, `PasswordPepper`, `CorsConfig`, `GlobalExceptionHandler`,
`application.yml`).

J'ai vérifié les 6 points de la revue 5, et ils sont tous traités. Pour AC-69, j'ai contrôlé dans le code ce qui
pouvait le rendre faux :
- `SoftDeleter.delete` ne fait que `markDeleted(...)` sur l'entité gérée, sans requête ni flush. La première lecture
  `JdbcTemplate` renvoie donc bien `null` avant `revokeAllSessions`.
- `JdbcTemplate` partage la connexion liée par `JpaTransactionManager` et voit les écritures non validées de la
  transaction.
- `flushAutomatically = true` provoque un `em.flush()` avant la mise à jour en masse.

L'AC est réalisable tel qu'il est écrit. J'ai aussi revérifié la sérialisation 002 / login sous `READ COMMITTED`, et
elle tient :
- **002 flushe d'abord** : `registerSuccessfulLogin` attend le verrou de ligne, puis réévalue sa clause `WHERE` et
  renvoie 0.
- **Le login valide d'abord** : la révocation en masse s'exécute après le flush, avec un nouvel instantané, et voit la
  famille déjà validée.

Les deux points restants sont mineurs et concernent surtout l'interface avec 002. Ils n'empêchent pas d'écrire les
tests ni le code de 001.

| # | Sévérité | Section | Problème | Correction proposée |
|---|---|---|---|---|
| 1 | Mineur | RG-23, § 5.3 (`User`), § 7.4 | **Mise à jour perdue entre 001 et 002 sur `app_user`.** RG-23 impose que les écritures de 001 (`registerFailedLogin`, `registerSuccessfulLogin`, `updatePasswordHash`) n'incrémentent pas `version`. Or `BaseEntity` / `User` n'ont pas `@DynamicUpdate` (vérifié : aucune occurrence dans `src/main`). Quand 002 modifie une entité `User` chargée (dirty checking ou `SoftDeleter`), Hibernate réécrit **toutes** les colonnes avec les valeurs lues au chargement, et le contrôle `version` ne détecte rien. Exemple : un admin qui modifie le profil pendant une attaque peut effacer un `locked_until` posé entre-temps, ce qui contourne RG-04 sur cette fenêtre. Il peut aussi remettre `failed_login_count` / `last_login_at` à des valeurs périmées. La fenêtre est étroite, mais l'effet est silencieux. | Au choix : (a) annoter `User` `@DynamicUpdate` (§ 5.3) pour que 002 n'écrive que les colonnes réellement modifiées (pas d'impact sur les tests de 001) ; (b) consigner le cas au § 7.4 comme risque accepté et le reporter dans les contraintes imposées à 002 (RG-16). (a) est préférable : une ligne, et le contrat de 002 n'a plus besoin d'y penser. |
| 2 | Mineur | RG-16, § 5.5 `revokeAllSessions`, § 7.4 « Garantie de RG-25 » | Avec `flushAutomatically = true`, le flush des modifications en attente de 002 a lieu **dans** `revokeAllSessions`. Une erreur de ce flush sort donc de cet appel et pas d'un `save` de 002, par exemple `ObjectOptimisticLockingFailureException` (→ `409` existant), une violation de contrainte (→ `DataIntegrityViolationException`) ou `EntityNotFoundException`. Le contrat de `revokeAllSessions` (« idempotent ») ne le dit pas. L'analyste de 002 pourrait croire que cet appel ne lève jamais d'exception. | Ajouter une phrase à la javadoc de `AuthService.revokeAllSessions` (§ 5.5) : « peut propager les exceptions du flush des modifications en attente de la transaction englobante (ex. `ObjectOptimisticLockingFailureException`) ; la transaction est alors annulée et rien n'est journalisé ». Aucun AC supplémentaire n'est requis dans 001. |

## Points des revues précédentes
| # | Statut (traité / non traité) |
|---|---|
| R5-1 | traité : `@Modifying(flushAutomatically = true)` sur `revokeAllActiveByUserId` (§ 5.4), RG-16 « Flush garanti côté 001 », § 5.5, § 7.4 ; AC-69 réalisable (`SoftDeleter` n'écrit qu'au flush, vérifié) et rattaché à AC-SEC-07 |
| R5-2 | traité : `userId` de `LOGIN_FAILURE` = compte trouvé à l'étape 2, y compris `CREDENTIALS_CHANGED` (SEC-10, aligné sur AC-68 (c)) |
| R5-3 | traité : `BearerTokenResolver` → `null` ssi `POST` **et** chemin exact (§ 5.5, RG-20, AC-40) |
| R5-4 | traité : `LOGOUT` par `afterCommit` ; `REFRESH_REJECTED` / `REFRESH_REUSE_DETECTED` après le commit de la phase A / C (§ 5.5, SEC-10) |
| R5-5 | traité : refresh concurrent d'un `revokeAllSessions` consigné au § 7.4, SEC-07 nuancé |
| R5-6 | traité : AC-SEC-12, décision unique (`@MockitoBean` dès le démarrage, hachage pré-calculé par un `Argon2PasswordHasher` distinct) |
| R4-1 à R4-10, R3-1 à R3-13, R2-1 à R2-15, R1-1 à R1-26 | traités (voir revues 2 à 5) |

## Historique
| Date | Verdict | Résumé |
|---|---|---|
| 2026-11-17 | À corriger | Première relecture : 1 bloquant (construction de `UserSeeder`), 10 majeurs (ordre des contrôles du refresh, verrou optimiste/409, effacement du cookie sur 401, `Origin: null` vs `CorsFilter`, `char(64)` vs `validate`, `@Builder.Default`, état du limiteur entre tests, piège de données AC-10/11, `dummyVerify`, absence de test de `TransitJwtKeyProvider`), 15 mineurs. |
| 2026-11-17 | À corriger | Deuxième relecture : les 26 points précédents sont traités (#17 partiellement). 0 bloquant, 5 majeurs (borne `exp` / vérificateur Nimbus / horloge à la seconde, connexion DB retenue pendant Argon2 → saturation du pool, hachage factice qui appelle le poivre dans le constructeur, entrée du SHA-256 du jeton de rafraîchissement, erreur OpenBao pendant la vérification JWT), 10 mineurs. |
| 2026-11-17 | À corriger | Troisième relecture : les 15 points de la revue 2 sont traités. 0 bloquant, 2 majeurs (RG-24 sans AC — ordre 400/429, compteur et hachage intacts après un échec de signature ; `TRUSTED_PROXIES` / WAF : le limiteur IP peut devenir global), 11 mineurs. |
| 2026-11-17 | À corriger | Quatrième relecture : les 13 points de la revue 3 sont traités. 0 bloquant, 2 majeurs (session créée par un login concurrent d'un changement de mot de passe / d'une désactivation, qui survit à `revokeAllSessions` ; ordre `LOGIN_FAILURE` / `ACCOUNT_LOCKED` contradictoire entre le § 5.5 et SEC-10), 8 mineurs. |
| 2026-11-17 | À corriger | Cinquième relecture : les 10 points de la revue 4 sont traités. 0 bloquant, 1 majeur (l'`UPDATE app_user` de 002 fait par dirty checking ou `SoftDeleter` n'est flushé qu'au commit, après `revokeAllSessions` : RG-25 ne tient pas ; correctif `@Modifying(flushAutomatically = true)` + AC-69), 5 mineurs. |
| 2026-11-17 | **OK** | Sixième relecture : les 6 points de la revue 5 sont traités ; AC-69 vérifié contre `SoftDeleter` / `JdbcTemplate`. 0 bloquant, 0 majeur, 2 mineurs (mise à jour perdue par un `UPDATE` complet de 002 sans `@DynamicUpdate` ; exceptions de flush propagées par `revokeAllSessions`, à documenter pour 002). |
