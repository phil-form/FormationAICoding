-- Feature 001 — Authentification : utilisateur minimal, rôles, familles et jetons de rafraîchissement.

CREATE TABLE app_user (
    id                 uuid         PRIMARY KEY,
    created_at         timestamptz  NOT NULL,
    updated_at         timestamptz  NOT NULL,
    version            bigint       NOT NULL,
    deleted_at         timestamptz,
    deleted_by         uuid,
    username           varchar(50)  NOT NULL CHECK (username ~ '^[a-z0-9][a-z0-9._-]{2,49}$'),
    password_hash      varchar(255) NOT NULL,
    enabled            boolean      NOT NULL DEFAULT true,
    depot_id           uuid,                                   -- FK vers depot : feature 003
    failed_login_count integer      NOT NULL DEFAULT 0 CHECK (failed_login_count >= 0),
    locked_until       timestamptz,
    last_login_at      timestamptz
);
CREATE UNIQUE INDEX ux_app_user_username ON app_user (username) WHERE deleted_at IS NULL;

-- Table de collection (@ElementCollection) : pas de colonnes BaseEntity.
CREATE TABLE app_user_role (
    user_id uuid        NOT NULL REFERENCES app_user (id),
    role    varchar(20) NOT NULL CHECK (role IN ('DRIVER', 'FLEET_MANAGER', 'ADMIN')),
    PRIMARY KEY (user_id, role)
);

CREATE TABLE refresh_token_family (
    id                  uuid        PRIMARY KEY,
    created_at          timestamptz NOT NULL,
    updated_at          timestamptz NOT NULL,
    version             bigint      NOT NULL,
    user_id             uuid        NOT NULL REFERENCES app_user (id),
    absolute_expires_at timestamptz NOT NULL,
    revoked_at          timestamptz,
    revocation_reason   varchar(30) CHECK (revocation_reason IN
                        ('LOGOUT', 'REUSE_DETECTED', 'USER_DISABLED', 'PASSWORD_CHANGED', 'ADMIN_REVOKED')),
    CHECK ((revoked_at IS NULL) = (revocation_reason IS NULL))
);
CREATE INDEX ix_refresh_token_family_user ON refresh_token_family (user_id) WHERE revoked_at IS NULL;

CREATE TABLE refresh_token (
    id         uuid        PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version    bigint      NOT NULL,
    family_id  uuid        NOT NULL REFERENCES refresh_token_family (id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    expires_at timestamptz NOT NULL,
    used_at    timestamptz
);
CREATE INDEX ix_refresh_token_family ON refresh_token (family_id);
