-- Sprint 0 baseline: proves Flyway runs against MySQL.
-- The real schema (users, roles, tables, menu, orders, ...) arrives in Sprint 1 as V2+.
CREATE TABLE app_info (
    id          TINYINT UNSIGNED NOT NULL PRIMARY KEY,
    schema_note VARCHAR(120)     NOT NULL,
    created_at  TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO app_info (id, schema_note) VALUES (1, 'Smart Cafeteria baseline (Sprint 0)');
