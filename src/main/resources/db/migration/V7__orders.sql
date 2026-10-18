-- Sprint 3: orders, price snapshots and status history (FR-013 to FR-022, SDS section 5).
-- Item names and prices are copied into order_items, so later menu edits never rewrite past orders.

CREATE TABLE orders (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id       VARCHAR(36)   NOT NULL,
    order_number    VARCHAR(20)   NOT NULL,
    student_id      BIGINT        NOT NULL,
    table_id        BIGINT        NOT NULL,
    table_label     VARCHAR(40)   NOT NULL,          -- the label at the time of the order
    status          VARCHAR(20)   NOT NULL,
    payment_method  VARCHAR(20)   NOT NULL,
    total           DECIMAL(12,2) NOT NULL,
    idempotency_key VARCHAR(64)   NULL,              -- guards against a double tap creating two orders
    version         BIGINT        NOT NULL DEFAULT 0, -- stops two people changing one order at the same moment
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    CONSTRAINT uq_orders_public_id    UNIQUE (public_id),
    CONSTRAINT uq_orders_number       UNIQUE (order_number),
    CONSTRAINT uq_orders_student_key  UNIQUE (student_id, idempotency_key),
    CONSTRAINT fk_orders_student FOREIGN KEY (student_id) REFERENCES users (id),
    CONSTRAINT fk_orders_table   FOREIGN KEY (table_id)   REFERENCES cafeteria_tables (id),
    CONSTRAINT chk_orders_total  CHECK (total >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_orders_status_created  ON orders (status, created_at);
CREATE INDEX idx_orders_student_created ON orders (student_id, created_at);
CREATE INDEX idx_orders_table_created   ON orders (table_id, created_at);

CREATE TABLE order_items (
    id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id     BIGINT        NOT NULL,
    menu_item_id BIGINT        NULL,
    item_name    VARCHAR(100)  NOT NULL,
    unit_price   DECIMAL(12,2) NOT NULL,
    quantity     INT           NOT NULL,
    line_total   DECIMAL(12,2) NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id)     REFERENCES orders (id),
    CONSTRAINT fk_order_items_menu  FOREIGN KEY (menu_item_id) REFERENCES menu_items (id),
    CONSTRAINT chk_order_items_qty   CHECK (quantity > 0),
    CONSTRAINT chk_order_items_price CHECK (unit_price >= 0 AND line_total >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_order_items_order ON order_items (order_id);

-- Append-only: rows are only ever inserted.
CREATE TABLE order_status_history (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id       BIGINT       NOT NULL,
    from_status    VARCHAR(20)  NULL,
    to_status      VARCHAR(20)  NOT NULL,
    actor_username VARCHAR(50)  NOT NULL,
    reason         VARCHAR(255) NULL,
    created_at     DATETIME(6)  NOT NULL,
    CONSTRAINT fk_order_history_order FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_order_history_order ON order_status_history (order_id, created_at);
