# 01 — Structure du projet (spec à implémenter)

> ✅ **Implémenté** (socle du 16/11/2026). Spec conservée comme référence de conception : en cas d'écart, le code fait foi.

## Initialisation

Générer le projet via Spring Initializr (start.spring.io) ou équivalent :

- Project : **Maven**, Language : **Java**, Java : **25**, Spring Boot : **4.1.x** (dernière GA)
- Group : `be.bstorm`, Artifact : `formation`, Package : `be.bstorm.formation`, Packaging : **jar**
- Dépendances :
  - Spring Web
  - Spring Data JPA (Hibernate)
  - PostgreSQL Driver
  - Flyway Migration (+ `flyway-database-postgresql`)
  - Validation
  - Spring Boot Actuator
  - Spring Boot DevTools (scope dev / optional)
  - **Lombok**
  - Testcontainers (PostgreSQL) + Spring Boot Testcontainers
  - Ajouts manuels : **MapStruct** (`mapstruct` + `mapstruct-processor`),
    `lombok-mapstruct-binding`, `springdoc-openapi-starter-webmvc-ui` (version compatible Boot 4)

### Annotation processors (pom.xml)

Déclarer explicitement dans `maven-compiler-plugin` → `annotationProcessorPaths`, **dans cet ordre** :

1. `lombok`
2. `lombok-mapstruct-binding`
3. `mapstruct-processor`

Arguments du compilateur :

```
-Amapstruct.defaultComponentModel=spring
-Amapstruct.unmappedTargetPolicy=ERROR
```

`lombok.config` à la racine :

```
config.stopBubbling = true
lombok.addLombokGeneratedAnnotation = true
lombok.anyConstructor.addConstructorProperties = false
```

## Arborescence cible

Organisation **par couche** directement sous le package racine (pas de package par feature).

```
.
├── CLAUDE.md
├── README.md
├── lombok.config
├── .env.example
├── .gitignore
├── docker-compose.yml
├── docker/
│   ├── dev/      # Dockerfile + fichiers propres au dev
│   ├── prod/     # Dockerfile + fichiers propres à la prod
│   └── test/     # Dockerfile + fichiers propres aux tests
├── start.sh
├── restore.sh
├── init_db/                     # dumps à restaurer (*.sql, *.sql.gz, *.dump) — non versionnés
│   └── .gitkeep
├── mvnw, mvnw.cmd, .mvn/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/be/bstorm/formation/
    │   │   ├── FormationApplication.java
    │   │   ├── controller/
    │   │   │   └── CourseController.java
    │   │   ├── service/
    │   │   │   ├── CourseService.java            # interface
    │   │   │   └── impl/
    │   │   │       └── CourseServiceImpl.java
    │   │   ├── repository/
    │   │   │   └── CourseRepository.java
    │   │   ├── entity/
    │   │   │   └── Course.java
    │   │   ├── dto/
    │   │   │   ├── request/
    │   │   │   │   ├── CourseCreateRequest.java
    │   │   │   │   └── CourseUpdateRequest.java
    │   │   │   └── response/
    │   │   │       └── CourseResponse.java
    │   │   ├── mapper/
    │   │   │   └── CourseMapper.java             # interface MapStruct
    │   │   └── framework/                        # transverse / socle technique
    │   │       ├── config/
    │   │       │   ├── OpenApiConfig.java
    │   │       │   ├── CorsConfig.java
    │   │       │   ├── JpaAuditingConfig.java    # @EnableJpaAuditing
    │   │       │   └── MapStructConfig.java      # @MapperConfig partagé
    │   │       ├── exception/
    │   │       │   ├── GlobalExceptionHandler.java   # @RestControllerAdvice → ProblemDetail
    │   │       │   ├── NotFoundException.java
    │   │       │   └── BusinessException.java
    │   │       ├── entity/
    │   │       │   └── BaseEntity.java           # id, createdAt, updatedAt, version (@MappedSuperclass)
    │   │       └── dto/
    │   │           └── PageResponse.java
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       ├── application-prod.yml
    │       └── db/
    │           ├── migration/           # V1__init.sql, V2__...sql
    │           └── seed/                # R__seed_*.sql (dev uniquement, activé par SEED=true)
    └── test/
        ├── java/be/bstorm/formation/
        │   ├── TestcontainersConfig.java   # @ServiceConnection PostgreSQLContainer("postgres:18-alpine")
        │   ├── controller/
        │   │   └── CourseControllerIT.java
        │   └── service/
        │       └── CourseServiceTest.java
        └── resources/
            └── application-test.yml
```

> `Course` n'est qu'un exemple pour illustrer le découpage ; le domaine métier réel reste à définir.

## Responsabilités par couche

| Couche | Rôle | Interdit |
|---|---|---|
| `controller` | HTTP : routes, validation `@Valid`, codes retour, appel du service | Logique métier, accès repository, exposer une entité |
| `service` | Logique métier, transactions, orchestration (interface + `impl`) | Connaître HTTP (`HttpServletRequest`, `ResponseEntity`) |
| `repository` | Accès données (Spring Data JPA, requêtes JPQL/natives) | Logique métier |
| `entity` | Mapping JPA | Annotations JSON / validation d'API |
| `dto` | Contrats d'entrée/sortie de l'API | Dépendre des entités |
| `mapper` | Conversion entité ↔ DTO (MapStruct) | Accès base, logique métier |
| `framework` | Socle transverse : config, exceptions, classes de base | Code métier |

Flux : `Controller → Service → Repository → DB`, conversion DTO ↔ entité dans le service via le mapper.

## Lombok & MapStruct — usages attendus

### Entités

```java
@Entity
@Table(name = "course")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Course extends BaseEntity { ... }
```

- **Interdit sur les entités** : `@Data`, `@EqualsAndHashCode`, `@ToString` non maîtrisé
  (chargement LAZY déclenché, boucles infinies sur les associations bidirectionnelles).
  Si besoin : `@ToString(onlyExplicitlyIncluded = true)` + `@ToString.Include`.
- `equals/hashCode` : définis une seule fois dans `BaseEntity` (basés sur l'id, sûrs avec les proxys Hibernate).
- Auditing : `@CreatedDate`, `@LastModifiedDate`, `@Version` dans `BaseEntity`
  (`@EntityListeners(AuditingEntityListener.class)`).

### DTO

- `record` Java + Jakarta Validation ; `@Builder` Lombok sur le record si la construction est verbeuse.

### Services / Controllers / composants

- `@RequiredArgsConstructor` (injection par constructeur via champs `private final`).
- `@Slf4j` pour les logs.

### Mappers (MapStruct)

```java
@Mapper(config = MapStructConfig.class)
public interface CourseMapper {
    CourseResponse toResponse(Course entity);
    Course toEntity(CourseCreateRequest request);
    void update(CourseUpdateRequest request, @MappingTarget Course entity);
}
```

- `MapStructConfig` : `componentModel = SPRING`, `unmappedTargetPolicy = ERROR`,
  `nullValuePropertyMappingStrategy = IGNORE` (mises à jour partielles).
- Les champs techniques (`id`, `createdAt`, `version`…) sont explicitement ignorés avec `@Mapping(target = ..., ignore = true)`.
- Aucun mapping écrit à la main sauf cas impossible à exprimer en MapStruct (`default` method dans l'interface).

## Configuration (application.yml — points clés)

```yaml
spring:
  application.name: formation
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  jpa:
    open-in-view: false
    hibernate.ddl-auto: validate
  flyway:
    enabled: true
    locations: classpath:db/migration
server:
  port: 8080
  error.include-stacktrace: never
```

- `application-dev.yml` : logs SQL, springdoc activé,
  `spring.flyway.locations` étendu à `classpath:db/seed` si `SEED=true`.
- `application-prod.yml` : springdoc désactivé, `management.endpoints.web.exposure.include=health,info`, logs structurés JSON.

## Conventions API

- Préfixe `/api/v1`, ressources au pluriel, kebab-case.
- `GET /api/v1/courses?page=0&size=20&sort=name,asc` → page
- `GET /api/v1/courses/{id}` → 200 / 404
- `POST /api/v1/courses` → 201 + header `Location`
- `PUT /api/v1/courses/{id}` → 200
- `DELETE /api/v1/courses/{id}` → 204
- Erreurs : `ProblemDetail` (`application/problem+json`), 400 pour validation avec la liste des champs en erreur.
- Swagger UI (dev) : `http://localhost:8080/swagger-ui.html`
- Healthcheck : `GET /actuator/health`

## .gitignore (à prévoir)

`target/`, `.env`, `init_db/*` (sauf `.gitkeep`), `.idea/workspace.xml`, `*.log`, `tmp/` *(optionnel une fois les specs implémentées)*.
