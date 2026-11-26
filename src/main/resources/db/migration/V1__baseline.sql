-- =====================================================================
-- V1: BASELINE - Schema hiện có của MultiMart (Phase 1-6)
-- Với DB đã có sẵn bảng (tạo bởi ddl-auto=update trước đây), Flyway sẽ BỎ QUA file này
-- (spring.flyway.baseline-version=1). Với DB trống, file này tạo toàn bộ schema nền.
-- =====================================================================

CREATE TABLE roles (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE users (
    id         BIGSERIAL PRIMARY KEY,
    email      VARCHAR(100) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    full_name  VARCHAR(100) NOT NULL,
    phone      VARCHAR(20),
    points     INTEGER      NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6)
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE products (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(200)   NOT NULL,
    description    TEXT,
    image_url      VARCHAR(255),
    original_price NUMERIC(15, 2) NOT NULL,
    total_stock    INTEGER        NOT NULL DEFAULT 0,
    status         VARCHAR(30)    NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMP(6)   NOT NULL,
    updated_at     TIMESTAMP(6)
);

CREATE TABLE flash_sale_events (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    start_time TIMESTAMP(6) NOT NULL,
    end_time   TIMESTAMP(6) NOT NULL,
    status     VARCHAR(30)  NOT NULL DEFAULT 'UPCOMING',
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6)
);

CREATE TABLE flash_sale_products (
    id                      BIGSERIAL PRIMARY KEY,
    event_id                BIGINT         NOT NULL REFERENCES flash_sale_events (id),
    product_id              BIGINT         NOT NULL REFERENCES products (id),
    flash_price             NUMERIC(15, 2) NOT NULL,
    total_allocated         INTEGER        NOT NULL,
    available_stock         INTEGER        NOT NULL,
    purchase_limit_per_user INTEGER        NOT NULL DEFAULT 1,
    version                 BIGINT,
    created_at              TIMESTAMP(6)   NOT NULL,
    updated_at              TIMESTAMP(6)
);

CREATE TABLE flash_sale_orders (
    id                    BIGSERIAL PRIMARY KEY,
    order_tracking_id     VARCHAR(64) UNIQUE,
    user_id               BIGINT         NOT NULL,
    flash_sale_event_id   BIGINT         NOT NULL,
    flash_sale_product_id BIGINT         NOT NULL,
    quantity              INTEGER        NOT NULL,
    unit_price            NUMERIC(15, 2) NOT NULL,
    total_price           NUMERIC(15, 2) NOT NULL,
    status                VARCHAR(50)    NOT NULL,
    created_at            TIMESTAMP(6)   NOT NULL,
    updated_at            TIMESTAMP(6)
);
