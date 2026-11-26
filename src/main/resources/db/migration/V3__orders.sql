-- =====================================================================
-- V3: ĐƠN HÀNG THƯỜNG (SRS-CART-001)
-- =====================================================================

CREATE TABLE orders (
    id               BIGSERIAL PRIMARY KEY,
    order_code       VARCHAR(20)    NOT NULL UNIQUE,
    user_id          BIGINT         NOT NULL REFERENCES users (id),
    status           VARCHAR(30)    NOT NULL,
    payment_method   VARCHAR(10)    NOT NULL,
    total_amount     NUMERIC(15, 2) NOT NULL,
    receiver_name    VARCHAR(100)   NOT NULL,
    receiver_phone   VARCHAR(20)    NOT NULL,
    shipping_address VARCHAR(500)   NOT NULL,
    note             VARCHAR(255),
    idempotency_key  VARCHAR(64)    NOT NULL,
    paid_at          TIMESTAMP(6),
    cancelled_at     TIMESTAMP(6),
    cancel_reason    VARCHAR(30),
    version          BIGINT         NOT NULL DEFAULT 0,
    is_deleted       BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP(6)   NOT NULL,
    updated_at       TIMESTAMP(6),
    CONSTRAINT uq_orders_user_idempotency UNIQUE (user_id, idempotency_key)
);

CREATE INDEX idx_orders_user_created ON orders (user_id, created_at DESC);
CREATE INDEX idx_orders_status_created ON orders (status, created_at);

CREATE TABLE order_items (
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id   BIGINT         NOT NULL REFERENCES products (id),
    product_name VARCHAR(200)   NOT NULL,
    unit_price   NUMERIC(15, 2) NOT NULL,
    quantity     INTEGER        NOT NULL,
    line_total   NUMERIC(15, 2) NOT NULL,
    created_at   TIMESTAMP(6)   NOT NULL,
    updated_at   TIMESTAMP(6)
);

CREATE INDEX idx_order_items_order ON order_items (order_id);
