-- Tables de sonde des tests du socle (src/test uniquement, jamais en dev / prod) : suppression logique (RM10),
-- cloisonnement (RM9), période + contrainte d'exclusion btree_gist (modèle de RM1).
-- Migration répétable : rejouée si elle change, toujours après les migrations versionnées.
DROP TABLE IF EXISTS probe_booking_note;
DROP TABLE IF EXISTS probe_booking;

CREATE TABLE probe_booking (
    id         uuid         PRIMARY KEY,
    created_at timestamptz  NOT NULL,
    updated_at timestamptz  NOT NULL,
    version    bigint       NOT NULL,
    deleted_at timestamptz,
    deleted_by uuid,
    owner_id   uuid         NOT NULL,
    depot_id   uuid         NOT NULL,
    code       varchar(20)  NOT NULL,
    start_at   timestamptz  NOT NULL,
    end_at     timestamptz  NOT NULL,
    CONSTRAINT probe_booking_period_ck CHECK (end_at > start_at),
    -- Modèle de RM1 : pas de chevauchement pour une même ressource (ici le dépôt), lignes supprimées exclues.
    CONSTRAINT probe_booking_no_overlap EXCLUDE USING gist (
        depot_id WITH =, tstzrange(start_at, end_at) WITH &&) WHERE (deleted_at IS NULL)
);
-- Unicité partielle : un code supprimé peut être réutilisé.
CREATE UNIQUE INDEX probe_booking_code_uk ON probe_booking (code) WHERE deleted_at IS NULL;

CREATE TABLE probe_booking_note (
    id         uuid         PRIMARY KEY,
    created_at timestamptz  NOT NULL,
    updated_at timestamptz  NOT NULL,
    version    bigint       NOT NULL,
    booking_id uuid         NOT NULL REFERENCES probe_booking (id),
    text       varchar(100) NOT NULL
);
