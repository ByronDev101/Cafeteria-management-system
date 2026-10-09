-- Sprint 1 / Slice B: forced password change and append-only audit log (FR-003, FR-033, FR-037, NFR-11)

ALTER TABLE users
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE AFTER active;

CREATE TABLE audit_events (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    occurred_at    DATETIME(6)  NOT NULL,
    actor_username VARCHAR(50)  NOT NULL,
    action         VARCHAR(50)  NOT NULL,
    target_type    VARCHAR(30)  NOT NULL,
    target_id      VARCHAR(64)  NOT NULL,
    outcome        VARCHAR(20)  NOT NULL,
    correlation_id VARCHAR(64)  NULL,
    details        VARCHAR(500) NULL      -- never passwords, session IDs or tokens
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_audit_target ON audit_events (target_type, target_id, occurred_at);
CREATE INDEX idx_audit_time   ON audit_events (occurred_at);
