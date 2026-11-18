# Triage 001-authentification — 2026-11-17

## Verdicts ANALYSE

Aucun retour. Aucun critère gelé.

## Remarque non bloquante sur le § 5.4 (requêtes JPQL indicatives)

Les deux défauts constatés au triage (verdict CODE) viennent de requêtes **indicatives** du § 5.4 reprises telles
quelles par l'implémentation. Le contrat (signatures, conditions, valeurs de retour) reste juste ; seuls les exemples
sont piégeux. Correction suggérée à la prochaine mise à jour de l'analyse (et à garder en tête pour 002) :

1. **`UserRepository.registerFailedLogin`** — `u.lockedUntil = case when … then :lockUntil else null end` : aucune
   branche n'étant typée en SQL, PostgreSQL résout le `CASE` en `text` et refuse l'affectation à une colonne
   `timestamptz` (SQLState 42804). L'exemple devrait typer l'expression (ou être remplacé par une formulation qui ne
   repose pas sur un `CASE` non typé).
2. **`RefreshTokenFamilyRepository.revokeAllActiveByUserId`** — `where f.user.id = :userId` : `User` portant
   `@SQLRestriction(deleted_at IS NULL)`, la condition peut être traduite avec le filtre de suppression logique et ne
   plus rien révoquer pour un utilisateur supprimé dans la transaction (cas exact d'AC-69 (b) et de la contrainte RG-16
   imposée à 002). L'exemple devrait préciser que la condition porte sur la colonne `user_id` sans dépendre de la
   visibilité de l'utilisateur (ex. requête native), `flushAutomatically = true` conservé.

Détails (traces, tests) : rapport d'audit local `.claude/state/audit/triage-2026-11-17-1605.md`.
