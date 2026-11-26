-- =====================================================================
-- V2: DANH MỤC SẢN PHẨM & TÌM KIẾM (SRS-CATALOG-001)
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE categories (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    slug       VARCHAR(120) NOT NULL UNIQUE,
    parent_id  BIGINT       REFERENCES categories (id),
    sort_order INTEGER      NOT NULL DEFAULT 0,
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    is_deleted BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6)
);

-- Tên danh mục duy nhất trong cùng cha (không phân biệt hoa thường), chỉ xét bản ghi chưa xóa
CREATE UNIQUE INDEX uq_categories_parent_name
    ON categories (parent_id, lower(name)) WHERE is_deleted = FALSE AND parent_id IS NOT NULL;
CREATE UNIQUE INDEX uq_categories_root_name
    ON categories (lower(name)) WHERE is_deleted = FALSE AND parent_id IS NULL;
CREATE INDEX idx_categories_parent ON categories (parent_id);

ALTER TABLE products ADD COLUMN category_id BIGINT REFERENCES categories (id);

-- Tìm kiếm LIKE '%kw%' không phân biệt hoa thường: GIN trigram trên lower(name)
CREATE INDEX idx_products_name_trgm ON products USING GIN (lower(name) gin_trgm_ops);
-- Lọc theo danh mục + trạng thái + khoảng giá / sắp xếp giá
CREATE INDEX idx_products_category_status_price ON products (category_id, status, original_price);
-- Danh sách mới nhất theo trạng thái
CREATE INDEX idx_products_status_created ON products (status, created_at DESC);
