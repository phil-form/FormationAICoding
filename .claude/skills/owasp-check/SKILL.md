---
name: owasp-check
description: Vérifie une analyse de feature liée à la sécurité contre les recommandations OWASP (Top 10, API Security Top 10, ASVS, Cheat Sheets) et y ajoute une section « Sécurité » avec exigences SEC-xx et critères d'acceptation testables AC-SEC-xx.
when_to_use: Feature touchant à l'authentification, aux autorisations, aux données personnelles, aux fichiers, aux secrets, au paiement ou à l'administration ; appelé automatiquement par analyse-feature.
argument-hint: <docs/analyse/features/NNN-slug.md>
context: fork
agent: analyst
background: false
---

# Contrôle OWASP d'une analyse de feature

Cible : `$ARGUMENTS` (une analyse dans `docs/analyse/features/`).

## Référentiels

Utilise la dernière version publiée de chaque référentiel. En cas de doute sur un contenu ou une version,
vérifie sur le site officiel avec WebFetch (`owasp.org`, `cheatsheetseries.owasp.org`) plutôt que de te fier
à ta mémoire, et cite la version utilisée dans la section produite.

- **OWASP Top 10** (applications web)
- **OWASP API Security Top 10** — prioritaire : le projet est une API REST consommée par un front Angular
- **OWASP ASVS** — niveau cible **L2** par défaut, à préciser si la feature exige L3
- **OWASP Cheat Sheet Series** — recommandations concrètes d'implémentation

## Démarche

1. Lis l'analyse cible et `CLAUDE.md` (stack : Spring Boot 4, Spring Security si présent, JPA/Hibernate, PostgreSQL).
2. Identifie la surface d'attaque de la feature : endpoints, données manipulées (classification : publique /
   interne / personnelle / sensible), acteurs, frontières de confiance, appels externes.
3. Passe la feature au crible de [checklist.md](checklist.md). Ne retiens que les points **pertinents**
   pour cette feature — pas de copier-coller générique.
4. Pour chaque risque retenu, rédige :
   - **SEC-xx** — exigence précise et implémentable dans la stack (ex. « mots de passe hachés avec
     Argon2id ou bcrypt via `PasswordEncoder` Spring Security, jamais en clair ni chiffrés de façon réversible »),
     avec ses références (`API1:2023`, `ASVS V6.2.1`, cheat sheet).
   - au moins un **AC-SEC-xx** — critère **testable depuis l'API** (code HTTP, absence de donnée dans la réponse,
     comportement après N tentatives…). Ce sont ces critères que l'agent de tests transformera en tests.
5. Remplace la section « 7. Sécurité » de l'analyse par :

```markdown
## 7. Sécurité

Référentiels : OWASP Top 10 <version>, API Security Top 10 <version>, ASVS <version> niveau L2.

### 7.1 Surface d'attaque
| Élément | Détail |
|---|---|

### 7.2 Exigences
- **SEC-01** — … *(réf. : API1:2023, ASVS Vx.y.z, Authorization Cheat Sheet)*

### 7.3 Critères d'acceptation sécurité
- **AC-SEC-01** (SEC-01) — *Given* un utilisateur A authentifié *When* `GET /api/v1/orders/{id}` d'une commande
  de B *Then* `404` (pas `403`, pour ne pas révéler l'existence).

### 7.4 Risques résiduels / hors périmètre
```

6. Mets à jour l'en-tête (`Sécurité : oui — <raison>`), ajoute une ligne d'historique, et ajoute dans
   « Questions ouvertes » toute décision de sécurité qui relève du client (durée de session, politique de mot de
   passe, conservation des données…).

## Réponse finale

Liste des `SEC-xx` ajoutées (une ligne chacune avec référence OWASP) et points nécessitant une décision.
