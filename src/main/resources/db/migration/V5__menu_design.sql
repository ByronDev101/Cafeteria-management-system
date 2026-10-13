-- Sprint 2 / design slice: prep time, vegetarian flag, "today's special" flag and menu photos.

ALTER TABLE menu_items
    ADD COLUMN prep_minutes  INT     NULL                  AFTER price,
    ADD COLUMN vegetarian    BOOLEAN NOT NULL DEFAULT FALSE AFTER prep_minutes,
    ADD COLUMN featured      BOOLEAN NOT NULL DEFAULT FALSE AFTER vegetarian,
    ADD COLUMN image_version BIGINT  NULL                  AFTER featured,   -- NULL means no photo
    ADD CONSTRAINT chk_menu_items_prep CHECK (prep_minutes IS NULL OR (prep_minutes BETWEEN 1 AND 180));

-- Photos live in their own table so menu list queries never load image bytes.
CREATE TABLE menu_item_images (
    item_id      BIGINT      NOT NULL PRIMARY KEY,
    content_type VARCHAR(30) NOT NULL,
    size_bytes   INT         NOT NULL,
    width        INT         NOT NULL,
    height       INT         NOT NULL,
    data         MEDIUMBLOB  NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    CONSTRAINT fk_menu_item_images_item FOREIGN KEY (item_id) REFERENCES menu_items (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
