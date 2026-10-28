-- Sprint 5, slice A: offline payment records (FR-023 to FR-026).
-- One record per order. Staff write down what they receive and hand back; no real money moves through the system,
-- so every record is a prototype record. A refund is a status step on the same record, not a new row.
-- Statuses: PENDING, VERIFIED, VOIDED, REFUND_DUE, REFUNDED. The application enforces the allowed moves.

CREATE TABLE payment_records (
    id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id   VARCHAR(36)   NOT NULL,
    order_id    BIGINT        NOT NULL,
    method_code VARCHAR(20)   NOT NULL,
    amount      DECIMAL(12,2) NOT NULL,
    status      VARCHAR(20)   NOT NULL,
    reference   VARCHAR(40)   NULL,                   -- optional, for example an M-Pesa code written down by staff
    prototype   BOOLEAN       NOT NULL DEFAULT TRUE,  -- always true in this version: no real money
    verified_by VARCHAR(50)   NULL,
    verified_at DATETIME(6)   NULL,
    version     BIGINT        NOT NULL DEFAULT 0,     -- stops two people changing one record at the same moment
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    CONSTRAINT uq_payment_records_public_id UNIQUE (public_id),
    CONSTRAINT uq_payment_records_order     UNIQUE (order_id),
    CONSTRAINT fk_payment_records_order     FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT chk_payment_records_amount   CHECK (amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_payment_records_status ON payment_records (status, updated_at);

-- Backfill: one record for every order that already exists. Cancelled orders start VOIDED, the rest PENDING.
INSERT INTO payment_records
    (public_id, order_id, method_code, amount, status, reference, prototype, verified_by, verified_at, version,
     created_at, updated_at)
SELECT UUID(), o.id, o.payment_method, o.total,
       CASE WHEN o.status = 'CANCELLED' THEN 'VOIDED' ELSE 'PENDING' END,
       NULL, TRUE, NULL, NULL, 0, o.created_at, o.updated_at
FROM orders o;
