-- Fichiers téléversés (framework/storage/FileStorage) : métadonnées. Le contenu, chiffré par enveloppe
-- (clé de données AES-256-GCM chiffrée par OpenBao Transit, clé « file »), est sur le volume uploads : <id>.bin.
-- Suppression logique (RM10) : deleted_at / deleted_by.
CREATE TABLE stored_file (
    id            uuid         PRIMARY KEY,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    version       bigint       NOT NULL,
    deleted_at    timestamptz,
    deleted_by    uuid,
    original_name varchar(255) NOT NULL,
    content_type  varchar(100) NOT NULL,
    size_bytes    bigint       NOT NULL CHECK (size_bytes > 0),
    sha256        varchar(64)  NOT NULL,
    wrapped_key   text         NOT NULL
);
