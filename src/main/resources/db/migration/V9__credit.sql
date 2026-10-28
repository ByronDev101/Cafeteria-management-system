-- Sprint 5, slice B: controlled student credit (FR-027 to FR-032).
-- credit_accounts: one row per student, holding the limit a manager set. No row means no credit.
-- credit_entries: the append-only ledger. Lines are only ever added (the application has no update or delete for
-- them); held and owed amounts are always worked out from the lines. Amounts are prototype records: no real money.

CREATE TABLE credit_accounts (
    id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    student_id   BIGINT        NOT NULL,
    credit_limit DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    updated_by   VARCHAR(50)   NOT NULL,
    version      BIGINT        NOT NULL DEFAULT 0,
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    CONSTRAINT uq_credit_accounts_student UNIQUE (student_id),
    CONSTRAINT fk_credit_accounts_student FOREIGN KEY (student_id) REFERENCES users (id),
    CONSTRAINT chk_credit_accounts_limit  CHECK (credit_limit >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE credit_entries (
    id         BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id  VARCHAR(36)   NOT NULL,
    student_id BIGINT        NOT NULL,
    order_id   BIGINT        NULL,                 -- null for a payment recorded against the balance
    entry_type VARCHAR(20)   NOT NULL,             -- RESERVE, RELEASE, POST, REVERSAL, SETTLEMENT
    amount     DECIMAL(12,2) NOT NULL,             -- always above zero; the type says what the line does
    actor      VARCHAR(50)   NOT NULL,
    note       VARCHAR(200)  NULL,
    created_at DATETIME(6)   NOT NULL,
    CONSTRAINT uq_credit_entries_public_id UNIQUE (public_id),
    CONSTRAINT fk_credit_entries_student   FOREIGN KEY (student_id) REFERENCES users (id),
    CONSTRAINT fk_credit_entries_order     FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT chk_credit_entries_amount   CHECK (amount > 0),
    CONSTRAINT chk_credit_entries_type     CHECK (entry_type IN ('RESERVE', 'RELEASE', 'POST', 'REVERSAL', 'SETTLEMENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_credit_entries_student ON credit_entries (student_id, id);
CREATE INDEX idx_credit_entries_order   ON credit_entries (order_id);
