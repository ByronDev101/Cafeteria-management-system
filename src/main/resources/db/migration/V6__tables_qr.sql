-- Sprint 2 / Slice B: cafeteria tables and QR tokens (FR-009 to FR-011, SDS 6.5).
-- Only a SHA-256 hash of each token is stored, so a leaked database cannot be used to rebuild a table's QR code.

CREATE TABLE cafeteria_tables (
    id              BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id       VARCHAR(36) NOT NULL,
    label           VARCHAR(40) NOT NULL,
    token_hash      VARCHAR(64) NOT NULL,
    active          BOOLEAN     NOT NULL DEFAULT TRUE,
    token_issued_at DATETIME(6) NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    CONSTRAINT uq_cafeteria_tables_public_id  UNIQUE (public_id),
    CONSTRAINT uq_cafeteria_tables_label      UNIQUE (label),
    CONSTRAINT uq_cafeteria_tables_token_hash UNIQUE (token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
