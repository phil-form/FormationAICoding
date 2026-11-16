---
name: analyse-feature
description: Génère l'analyse préparatoire d'une feature (règles métier, critères d'acceptation, contrat technique, modèle de données) à partir de l'analyse client dans docs/analyse/client/. Ajoute automatiquement les exigences OWASP si la feature touche à la sécurité.
when_to_use: Avant d'écrire tests ou code pour une nouvelle feature, ou pour mettre à jour l'analyse d'une feature existante.
argument-hint: <fichier analyse client> <nom de la feature>
context: fork
agent: analyst
background: false
---

# Analyse préparatoire de feature

Entrée : `$ARGUMENTS` (fichier d'analyse client dans `docs/analyse/client/` + nom de la feature visée).
Si le fichier n'est pas précisé, liste `docs/analyse/client/` et prends le document le plus pertinent pour la feature.

## Étapes

1. Lis `CLAUDE.md`, `tmp/01-STRUCTURE.md` et l'analyse client.
2. Lis les analyses existantes (`docs/analyse/features/`) pour : réutiliser les entités déjà définies,
   éviter les doublons, déterminer le prochain numéro `NNN`.
3. Lis l'existant utile dans `src/main/` (entités, migrations) pour rester cohérent. **Jamais `src/test/`.**
   Si `docs/analyse/reviews/NNN-<slug>.review.md` ou `NNN-<slug>.triage.md` existent, traite chacun de leurs
   points (correction ou réponse argumentée dans « Historique ») : la mise à jour est faite pour eux.
4. Rédige `docs/analyse/features/NNN-<slug>.md` en suivant **exactement** [template.md](template.md).
   Si le fichier existe déjà : mets-le à jour en conservant les identifiants existants et ajoute une
   ligne dans « Historique ».
5. **Détection sécurité** — la feature est « liée à la sécurité » si elle touche au moins un de ces points :
   authentification, sessions/tokens, mots de passe/secrets, autorisations/rôles/propriété des ressources,
   données personnelles ou sensibles (RGPD), upload/download de fichiers, appels sortants vers une URL fournie,
   paiement, actions d'administration, export de données en masse, recherche/filtre libre construisant des requêtes,
   limitation de débit / anti-automatisation.
   - Si oui : applique le skill **owasp-check** sur le fichier produit (section « Sécurité » complète,
     `SEC-xx` + `AC-SEC-xx`) et passe le champ `Sécurité` de l'en-tête à `oui`.
   - Sinon : section « Sécurité » réduite aux contrôles de base (validation des entrées, erreurs sans fuite
     d'information, autorisation d'accès aux endpoints) et `Sécurité : non`.
6. Auto-contrôle avant de rendre la main :
   - chaque `RG-xx` est couverte par au moins un `AC-xx` ;
   - chaque `AC-xx` est vérifiable de l'extérieur (entrée → sortie attendue précise : code HTTP, corps, état en base) ;
   - le contrat technique permet d'écrire un test **sans voir le code** (packages, classes, signatures,
     champs + contraintes de validation, routes, codes HTTP, `type`/`title` des `ProblemDetail`) ;
   - aucune décision technique ne contredit `CLAUDE.md`.

## Réponse finale

Chemin du fichier, résumé en 5 lignes max, statut sécurité (oui/non et pourquoi), questions ouvertes bloquantes.
