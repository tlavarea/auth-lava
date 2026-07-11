-- =========================================================
-- Auth schema: email/password + OAuth (Google/GitHub) + MFA + roles
-- Target: PostgreSQL 14+
-- =========================================================

-- ---------------------------------------------------------
-- USERS
-- password_hash is nullable: a user who only ever signs in
-- via a social provider will have no local password.
-- Case-insensitive email matching is handled in application
-- code, not the database.
-- ---------------------------------------------------------
CREATE TABLE users (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email           TEXT NOT NULL UNIQUE,
    password_hash   TEXT,                          -- NULL if social-login-only
    email_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    status          TEXT NOT NULL DEFAULT 'active' -- active | suspended | deleted
                        CHECK (status IN ('active', 'suspended', 'deleted')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_login_at   TIMESTAMPTZ
);

-- ---------------------------------------------------------
-- OAUTH_ACCOUNTS
-- Links a user to one or more external identity providers.
-- A user can have multiple linked providers; a given
-- provider account can only ever map to one user.
-- ---------------------------------------------------------
CREATE TABLE oauth_accounts (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider            TEXT NOT NULL CHECK (provider IN ('google', 'github')),
    provider_user_id    TEXT NOT NULL,     -- the "sub"/id the provider gives you
    access_token_enc    TEXT,              -- store encrypted, only if you need to call the provider's API later
    refresh_token_enc   TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (provider, provider_user_id)
);

CREATE INDEX idx_oauth_accounts_user_id ON oauth_accounts(user_id);

-- ---------------------------------------------------------
-- MFA_METHODS
-- A user can enroll multiple MFA methods (e.g. TOTP + backup codes).
-- Secrets must be encrypted at rest, not just hashed
-- (you need to decrypt a TOTP secret to verify a live code).
-- ---------------------------------------------------------
CREATE TABLE mfa_methods (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type              TEXT NOT NULL CHECK (type IN ('totp', 'sms', 'backup_codes')),
    secret_encrypted  TEXT,               -- TOTP seed, encrypted
    phone_number      TEXT,               -- only used when type = 'sms'
    is_enabled        BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at       TIMESTAMPTZ,        -- set once the user proves they control this method
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_mfa_methods_user_id ON mfa_methods(user_id);

-- Backup codes get their own table: each code is single-use,
-- so this is a one-to-many, not a column on mfa_methods.
CREATE TABLE mfa_backup_codes (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash   TEXT NOT NULL,   -- hash, not encrypt -- you only ever compare, never display again
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_mfa_backup_codes_user_id ON mfa_backup_codes(user_id);

-- ---------------------------------------------------------
-- ROLES + USER_ROLES
-- Many-to-many: a user can hold multiple roles, a role
-- applies to many users. Seed a few sensible defaults.
-- ---------------------------------------------------------
CREATE TABLE roles (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        TEXT NOT NULL UNIQUE,   -- e.g. 'admin', 'member', 'billing_manager'
    description TEXT
);

CREATE TABLE user_roles (
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id     BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (user_id, role_id)
);

INSERT INTO roles (name, description) VALUES
    ('admin',  'Full administrative access'),
    ('member', 'Standard authenticated user');

-- ---------------------------------------------------------
-- PERMISSIONS + ROLE_PERMISSIONS
-- Fine-grained access control units, e.g. 'users:read'.
-- A role is just a named bundle of permissions; your app
-- code checks permissions, not role names, so you can
-- reshuffle who-has-what without touching business logic.
-- ---------------------------------------------------------
CREATE TABLE permissions (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        TEXT NOT NULL UNIQUE,   -- e.g. 'users:read', 'billing:write'
    description TEXT
);

CREATE TABLE role_permissions (
    role_id       BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,

    PRIMARY KEY (role_id, permission_id)
);

-- Seed a few example permissions and wire them to the default roles
INSERT INTO permissions (name, description) VALUES
    ('users:read',   'View user accounts'),
    ('users:write',  'Create, update, or deactivate user accounts'),
    ('roles:manage', 'Assign or revoke roles and permissions');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'admin';  -- admin gets every seeded permission

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'member' AND p.name = 'users:read';

-- ---------------------------------------------------------
-- REFRESH_TOKEN
-- Opaque, high-entropy tokens issued alongside a JWT access
-- token. Only a SHA-256 hash of the token is stored, never
-- the raw value. Rotated on every use; presenting an
-- already-revoked token is treated as reuse of a
-- rotated-away token and revokes all of that user's
-- active sessions.
-- ---------------------------------------------------------
CREATE TABLE refresh_token (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      TEXT NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    replaced_by_id  BIGINT REFERENCES refresh_token(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_token_user_id ON refresh_token(user_id);

-- ---------------------------------------------------------
-- Helpful trigger: keep updated_at current on users
-- ---------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
