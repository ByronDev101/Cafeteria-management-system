-- Sprint 2 / Slice A: digital menu (FR-006 to FR-008). Prices are DECIMAL in KSh, never negative.

CREATE TABLE menu_categories (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id     VARCHAR(36)  NOT NULL,
    name          VARCHAR(80)  NOT NULL,
    description   VARCHAR(255) NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order INT          NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    CONSTRAINT uq_menu_categories_public_id UNIQUE (public_id),
    CONSTRAINT uq_menu_categories_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE menu_items (
    id          BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id   VARCHAR(36)    NOT NULL,
    category_id BIGINT         NOT NULL,
    name        VARCHAR(100)   NOT NULL,
    description VARCHAR(500)   NULL,
    price       DECIMAL(12,2)  NOT NULL,
    available   BOOLEAN        NOT NULL DEFAULT TRUE,   -- manager toggle: sold out or not
    active      BOOLEAN        NOT NULL DEFAULT TRUE,   -- soft delete: inactive items are hidden from students
    created_at  DATETIME(6)    NOT NULL,
    updated_at  DATETIME(6)    NOT NULL,
    CONSTRAINT uq_menu_items_public_id UNIQUE (public_id),
    CONSTRAINT uq_menu_items_category_name UNIQUE (category_id, name),
    CONSTRAINT fk_menu_items_category FOREIGN KEY (category_id) REFERENCES menu_categories (id),
    CONSTRAINT chk_menu_items_price CHECK (price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
