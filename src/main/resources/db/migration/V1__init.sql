-- V1 : initialisation du schéma.
-- Les tables métier arrivent avec les features (V2, V3…). Ne jamais modifier une migration déjà commitée.

-- Fuseau horaire de référence de la base (les Instant Java sont stockés en timestamptz UTC).
DO $$
BEGIN
    EXECUTE format('ALTER DATABASE %I SET timezone TO %L', current_database(), 'UTC');
END
$$;
