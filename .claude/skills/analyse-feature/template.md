# NNN — <Nom de la feature>

| Champ | Valeur |
|---|---|
| Statut | brouillon \| validée |
| Source | `docs/analyse/client/<fichier>` (§ concernés) |
| Sécurité | oui \| non — <raison courte> |
| Dépend de | NNN-autre-feature, … |

## 1. Contexte et objectif

Pourquoi cette feature existe, pour qui, quel problème elle résout (3-6 lignes).

## 2. Acteurs et rôles

| Acteur | Rôle applicatif | Ce qu'il peut faire |
|---|---|---|

## 3. Règles métier

- **RG-01** — énoncé précis, vérifiable.
- **RG-02** — …

## 4. Critères d'acceptation

Format Given/When/Then, observable depuis l'API (code HTTP, corps, état en base).

- **AC-01** (RG-01) — *Given* … *When* `POST /api/v1/...` avec … *Then* `201`, header `Location`, corps …
- **AC-02** (RG-01) — cas d'erreur : … *Then* `400` `ProblemDetail` avec `errors[].field = "email"`.

## 5. Contrat technique

> Doit suffire pour écrire les tests sans voir le code, et le code sans voir les tests.

### 5.1 Endpoints

| Méthode | Route | Requête | Réponse succès | Erreurs |
|---|---|---|---|---|
| POST | `/api/v1/xxx` | `XxxCreateRequest` | 201 `XxxResponse` | 400, 409 |

### 5.2 DTO (`be.bstorm.formation.dto.request` / `.response`)

```java
public record XxxCreateRequest(
    @NotBlank @Size(max = 100) String name,
    @NotNull @Email String email
) {}
```

### 5.3 Entités (`be.bstorm.formation.entity`)

| Entité | Champ | Type Java | Colonne | Contraintes |
|---|---|---|---|---|

### 5.4 Repository (`be.bstorm.formation.repository`)

```java
public interface XxxRepository extends JpaRepository<Xxx, Long> {
    boolean existsByEmailIgnoreCase(String email);
}
```

### 5.5 Service (`be.bstorm.formation.service`)

```java
public interface XxxService {
    XxxResponse create(XxxCreateRequest request); // RG-01 ; lève ConflictException si email existe (AC-03)
}
```

### 5.6 Mapper (`be.bstorm.formation.mapper`)

Méthodes MapStruct attendues et champs ignorés.

### 5.7 Erreurs

| Cas | Exception (`framework.exception`) | HTTP | `title` du ProblemDetail |
|---|---|---|---|

## 6. Modèle de données / migration

Tables, colonnes, index, contraintes d'unicité, FK → future migration `V<n>__<description>.sql`.

## 7. Sécurité

- Base (toujours) : validation des entrées, messages d'erreur sans fuite, contrôle d'accès des endpoints.
- Si `Sécurité : oui` → section complète produite par **owasp-check** (`SEC-xx`, `AC-SEC-xx`, références OWASP).

## 8. Hors périmètre

Ce que la feature ne fait explicitement pas.

## 9. Questions ouvertes

| # | Question | Hypothèse par défaut | Bloquante ? |
|---|---|---|---|

## Historique

| Date | Modification |
|---|---|
