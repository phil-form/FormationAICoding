# Plan de correction — dépréciations du 16/11/2026

Source : `/deprecations` → `.claude/state/audit/deprecations/latest/findings.md` (log de `run-tests.sh --docker`,
98 tests verts, + compilation `-Xlint:deprecation,removal` de main et tests). **9 dépréciations distinctes, 4 causes.**
**Exécuté le 16/11/2026** (décision D1 : Lombok en veille, avertissement conservé). Résultat vérifié : 98 tests verts ;
`/deprecations` → **9 → 2 messages**, seuls restent ceux de Lombok (cause 4, sans correctif amont).

## Synthèse

| # | Cause | Messages | Origine | Échéance | Correction proposée | Effort | Priorité |
|---|---|---|---|---|---|---|---|
| 1 | Spring Vault 4 abandonne `RestTemplate` | 2 (compilateur, main) | **notre code** : `OpenBaoConfig` | API dépréciée de Spring Vault 4.x (suppression attendue à une version majeure) | `new AppRoleAuthentication(options, vaultClient())` | S | Haute |
| 2 | Mockito s'attache lui-même comme agent (inline mock maker) | 4 (Mockito + 2 JVM « agent loaded dynamically ») | outillage de test (Mockito 5.23 / Byte Buddy) | Chargement dynamique d'agents **interdit par défaut** dans un futur JDK (JEP 451) | déclarer Mockito comme `-javaagent` (Surefire, Failsafe, PIT) | S | Haute |
| 3 | JNA (Testcontainers) charge du code natif sans autorisation | 2 (JVM « restricted methods ») | dépendance de test (Testcontainers → JNA 5.18.1) | Méthodes restreintes **bloquées** dans un futur JDK (JEP 472) | `--enable-native-access=ALL-UNNAMED` pour les JVM de test | S | Moyenne |
| 4 | Lombok utilise `sun.misc.Unsafe` pendant la compilation | 2 (JVM, build) | dépendance de build : Lombok 1.18.48 (dernière version) | Accès mémoire de `sun.misc.Unsafe` **retiré** dans un futur JDK (JEP 471/498) | **veille** : aucune version corrigée ; ne pas masquer l'avertissement | — | Basse (Java 25 LTS jusqu'à Java 29) |

Les causes 2 et 3 relèvent de la même modification (`pom.xml`, options des JVM de test) : à traiter ensemble.

## Détail

### 1. `OpenBaoConfig` — API `RestTemplate` dépréciée (notre code)

- **Constat** : `AppRoleAuthentication(AppRoleAuthenticationOptions, RestOperations)` et
  `AbstractVaultConfiguration.restOperations()` sont `@Deprecated` dans Spring Vault 4.1.1 (vérifié dans le jar :
  `restTemplateBuilder`, `restTemplateFactory`, `restOperations` le sont aussi). Spring Vault 4 bascule sur
  `VaultClient` / `RestClient`.
- **Correction** : dans `clientAuthentication()`, utiliser le constructeur `AppRoleAuthentication(options, VaultClient)`
  avec le `vaultClient()` fourni par `AbstractVaultConfiguration` — même endpoint, même TLS (`sslConfiguration()`).
- **Risque** : faible ; le chemin d'authentification est couvert par `OpenBaoIntegrationIT` (AppRole réel) et le
  démarrage dev/prod.
- **Vérification** : plus aucun avertissement `-Xlint` ; `OpenBaoIntegrationIT` vert ; `./start.sh` (dev) et
  `docker/test-prod/check.sh` OK.

### 2. Mockito / Byte Buddy — agent chargé dynamiquement (outillage de test)

- **Constat** : « Mockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in
  future releases of the JDK » + « A Java agent has been loaded dynamically (byte-buddy-agent) » + « Dynamic loading
  of agents will be disallowed by default in a future release ». Même cause : Mockito 5 attache Byte Buddy à chaud.
- **Correction** (méthode documentée par Mockito) :
  1. `maven-dependency-plugin` → goal `properties` (expose `${org.mockito:mockito-core:jar}`) ;
  2. `maven-surefire-plugin` et `maven-failsafe-plugin` → `<argLine>@{argLine} -javaagent:${org.mockito:mockito-core:jar}</argLine>`
     (`@{argLine}` préserve un futur agent JaCoCo) ;
  3. `pitest-maven` → même option dans `<jvmArgs>` (les mutants tournent dans des JVM séparées).
- **Risque** : faible (configuration de test uniquement, aucune incidence sur le jar de production).
- **Vérification** : plus aucun message Mockito / « agent loaded dynamically » ; 98 tests verts ; `/mutation-test`
  fonctionne.

### 3. JNA — accès natif non déclaré (dépendance de test)

- **Constat** : « java.lang.System::load has been called by com.sun.jna.Native in an unnamed module (jna-5.18.1) » /
  « Restricted methods will be blocked in a future release unless native access is enabled ». JNA est utilisé par
  Testcontainers (accès au socket Docker) — **tests uniquement**, absent du jar de production.
- **Correction** : ajouter `--enable-native-access=ALL-UNNAMED` à l'`argLine` de Surefire et Failsafe (même
  modification que 2).
- **Variante plus stricte** : cibler le module JNA plutôt que `ALL-UNNAMED` — non applicable ici (JNA est chargé sur le
  classpath, donc dans le module sans nom).
- **Vérification** : plus aucun avertissement « restricted methods » ; tests d'intégration (Testcontainers) verts.

### 4. Lombok — `sun.misc.Unsafe` (dépendance de build, sans correctif disponible)

- **Constat** : « sun.misc.Unsafe::objectFieldOffset has been called by lombok.permit.Permit » pendant la compilation
  (javac + processeur d'annotations). Lombok 1.18.48 est la **dernière** version publiée et celle gérée par
  Spring Boot 4.1.2 : pas de mise à jour corrective.
- **Échéance** : Java 25 émet un avertissement ; un JDK ultérieur interdira ces accès. Le projet reste sur
  **Java 25 LTS** jusqu'au prochain LTS (Java 29, sept. 2027) → pas de risque immédiat, mais **bloquant pour la
  montée de JDK** si Lombok n'a pas évolué d'ici là.
- **Proposition** :
  - veille sur les versions Lombok (à vérifier à chaque montée de Spring Boot et avant tout changement de JDK) ;
  - **ne pas masquer** l'avertissement (`--sun-misc-unsafe-memory-access=allow` dans `.mvn/jvm.config` le ferait
    disparaître sans rien corriger et ferait perdre le signal) ;
  - plan B si Lombok ne suit pas avant la migration vers Java 29 : `delombok` (code généré explicite) — à évaluer
    le moment venu.
- **Vérification** : `/deprecations` après chaque montée de version.

## Ordre d'exécution proposé

1. **1** — `OpenBaoConfig` (notre code, indépendant).
2. **2 + 3** — une seule modification du `pom.xml` (agent Mockito, accès natif, PIT).
3. `/run-tests` puis `/deprecations` : il ne doit rester que les 2 messages Lombok (cause 4).
4. **4** — veille, consignée dans `Next.md` (prérequis de toute montée de JDK).

Effort total : environ ½ heure à 1 heure, vérification comprise.

## Décision attendue

| # | Question | Recommandation |
|---|---|---|
| D1 | Lombok : veille sans masquer l'avertissement, ou le masquer (`--sun-misc-unsafe-memory-access=allow`) ? | **Décidé : veille sans masquer** |
