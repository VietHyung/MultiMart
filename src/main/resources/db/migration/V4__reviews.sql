-- =====================================================================
-- V4: ĐÁNH GIÁ & XẾP HẠNG SẢN PHẨM (SRS-REVIEW-001)
-- =====================================================================

-- Tổng hợp điểm đánh giá lưu trên sản phẩm (chỉ tính đánh giá VISIBLE, chưa xóa)
ALTER TABLE products ADD COLUMN rating_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE products ADD COLUMN rating_sum   INTEGER NOT NULL DEFAULT 0;

CREATE TABLE product_reviews (
    id         BIGSERIAL PRIMARY KEY,
    product_id BIGINT       NOT NULL REFERENCES products (id),
    user_id    BIGINT       NOT NULL REFERENCES users (id),
    order_id   BIGINT       NOT NULL REFERENCES orders (id),
    rating     SMALLINT     NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment    VARCHAR(1000),
    anonymous  BOOLEAN      NOT NULL DEFAULT TRUE,
    status     VARCHAR(20)  NOT NULL DEFAULT 'VISIBLE',
    edited     BOOLEAN      NOT NULL DEFAULT FALSE,
    is_deleted BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6)
);

-- Mỗi dòng sản phẩm của mỗi đơn có tối đa một đánh giá còn hiệu lực
CREATE UNIQUE INDEX uq_review_order_product
    ON product_reviews (order_id, product_id) WHERE is_deleted = FALSE;

CREATE INDEX idx_review_product_status_created
    ON product_reviews (product_id, status, created_at DESC) WHERE is_deleted = FALSE;

CREATE INDEX idx_review_product_rating
    ON product_reviews (product_id, rating) WHERE is_deleted = FALSE AND status = 'VISIBLE';

CREATE INDEX idx_review_user_created ON product_reviews (user_id, created_at DESC);

-- Hỗ trợ kiểm tra "đơn đã giao có chứa sản phẩm"
CREATE INDEX IF NOT EXISTS idx_order_items_product_order ON order_items (product_id, order_id);
