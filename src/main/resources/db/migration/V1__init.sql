-- V1 : point de départ du schéma (vide). Les tables métier arrivent avec les features (V2, V3…).
-- Ne jamais modifier une migration déjà commitée.
--
-- Les migrations s'exécutent avec un utilisateur temporaire OpenBao en SET ROLE formation_owner : propriétaire du
-- schéma public, mais PAS de la base. Les réglages de niveau base (ALTER DATABASE …, fuseau horaire UTC, droits de
-- connexion) sont appliqués par docker/db/roles.sh avec le superutilisateur.
SELECT 1;
