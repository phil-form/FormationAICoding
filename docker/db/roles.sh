#!/bin/sh
# Rôles PostgreSQL pour les identifiants dynamiques OpenBao (ADR 0001, « Modèle de droits PostgreSQL »).
# Idempotent : exécutable à chaque démarrage (dev : service compose db-roles ; prod : bootstrap OpenBao).
#
#   formation_owner  NOLOGIN  propriétaire du schéma public et de tous les objets (Flyway via SET ROLE)
#   formation_rw     NOLOGIN  DML uniquement (SELECT/INSERT/UPDATE/DELETE + séquences), aucun DDL
#   openbao_admin    LOGIN    CREATEROLE, administre les rôles ci-dessus ; mot de passe connu d'OpenBao seul en prod
#
# Variables : PGHOST, PGUSER (superutilisateur), PGPASSWORD, PGDATABASE ;
#             OPENBAO_DB_ADMIN_PASSWORD (optionnel : défini seulement s'il est fourni — en prod, OpenBao le fait
#             ensuite tourner avec rotate-root, il ne faut donc plus le réinitialiser).
set -eu

until pg_isready -q; do sleep 1; done

psql -v ON_ERROR_STOP=1 -q -v db="$PGDATABASE" <<'SQL'
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'formation_owner') THEN
        CREATE ROLE formation_owner NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'formation_rw') THEN
        CREATE ROLE formation_rw NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'openbao_admin') THEN
        CREATE ROLE openbao_admin LOGIN CREATEROLE NOINHERIT;
    END IF;
END
$$;

-- OpenBao peut accorder ces rôles aux utilisateurs dynamiques (PG16+ : ADMIN OPTION requise).
GRANT formation_owner TO openbao_admin WITH ADMIN OPTION;
GRANT formation_rw    TO openbao_admin WITH ADMIN OPTION;
-- Couper les sessions d'un utilisateur dynamique révoqué (pas celles d'un superutilisateur).
GRANT pg_signal_backend TO openbao_admin;

-- Réglages de niveau base (le migrateur n'est pas propriétaire de la base, il ne peut pas les faire).
ALTER DATABASE :"db" SET timezone TO 'UTC';

-- Extensions (superutilisateur) : btree_gist → contraintes d'exclusion mêlant égalité et chevauchement de
-- périodes (RM1 : EXCLUDE USING gist (vehicle_id WITH =, tstzrange(start_at, end_at) WITH &&)).
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Base : seuls les rôles applicatifs s'y connectent (en plus du superutilisateur).
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE :"db" TO formation_owner, formation_rw;
GRANT CONNECT ON DATABASE :"db" TO openbao_admin;

-- Schéma : propriété de formation_owner, lecture d'usage pour formation_rw, rien pour PUBLIC.
ALTER SCHEMA public OWNER TO formation_owner;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO formation_rw;

-- Objets existants (créés avant ce modèle, ex. par le superutilisateur) → formation_owner.
DO $$
DECLARE r record;
BEGIN
    FOR r IN SELECT c.relname, c.relkind FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
             WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p', 'v', 'm', 'S', 'f')
               AND pg_get_userbyid(c.relowner) <> 'formation_owner'
    LOOP
        EXECUTE format('ALTER %s public.%I OWNER TO formation_owner',
            CASE r.relkind WHEN 'S' THEN 'SEQUENCE' WHEN 'v' THEN 'VIEW' WHEN 'm' THEN 'MATERIALIZED VIEW'
                           WHEN 'f' THEN 'FOREIGN TABLE' ELSE 'TABLE' END, r.relname);
    END LOOP;
END
$$;

-- Droits DML sur l'existant et sur tout ce que formation_owner créera.
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO formation_rw;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO formation_rw;
ALTER DEFAULT PRIVILEGES FOR ROLE formation_owner IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO formation_rw;
ALTER DEFAULT PRIVILEGES FOR ROLE formation_owner IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO formation_rw;
ALTER DEFAULT PRIVILEGES FOR ROLE formation_owner IN SCHEMA public
    REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
SQL

# Ménage : utilisateurs dynamiques EXPIRÉS (VALID UNTIL dépassé, donc inutilisables) qu'OpenBao ne révoquera plus
# — typiquement en dev, où OpenBao est en mémoire et oublie ses baux à chaque recréation. Sans effet sur les
# utilisateurs valides ; en prod, OpenBao révoque lui-même à l'expiration.
psql -v ON_ERROR_STOP=1 -q <<'SQL'
DO $$
DECLARE r record;
BEGIN
    FOR r IN SELECT rolname FROM pg_roles
             WHERE rolname LIKE 'v-approle-%' AND rolvaliduntil IS NOT NULL AND rolvaliduntil < now()
    LOOP
        PERFORM pg_terminate_backend(pid) FROM pg_stat_activity WHERE usename = r.rolname;
        EXECUTE format('DROP ROLE IF EXISTS %I', r.rolname);
        RAISE NOTICE 'Utilisateur dynamique expiré supprimé : %', r.rolname;
    END LOOP;
END
$$;
SQL

if [ -n "${OPENBAO_DB_ADMIN_PASSWORD:-}" ]; then
  # Transmis par la variable psql (jamais interpolé dans le SQL).
  psql -v ON_ERROR_STOP=1 -q -v pw="$OPENBAO_DB_ADMIN_PASSWORD" <<'SQL'
ALTER ROLE openbao_admin PASSWORD :'pw';
SQL
fi

echo "Rôles PostgreSQL à jour (formation_owner, formation_rw, openbao_admin)."
