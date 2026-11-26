# SRS & DETAIL DESIGN: DANH MỤC SẢN PHẨM & TÌM KIẾM HIỆU NĂNG CAO
> **Vai trò:** BA (Mary) – tài liệu cho Dev đọc và Test đối chiếu.
> **Mã:** SRS-CATALOG-001 · **Trạng thái:** CHỜ GATE 1 · **Kiến trúc:** xem `sa_architecture_phase7.md` (ADR-6, 7, 8)

---

## 1. MỤC TIÊU & PHẠM VI
**Mục tiêu:** khách duyệt sản phẩm theo danh mục, tìm và lọc nhanh; Admin quản lý danh mục và gán sản phẩm vào danh mục.

**Trong phạm vi:** danh mục 2 cấp, gán danh mục cho sản phẩm, tìm kiếm + lọc + sắp xếp, cache.
**Ngoài phạm vi:** thuộc tính động (màu, size), xếp hạng liên quan/gợi ý, đa ngôn ngữ, Elasticsearch.

**Tác nhân:** Khách/Public (đọc), Admin (`ROLE_ADMIN`, ghi).

## 2. USE CASE
| Mã | Use case | Tác nhân |
|---|---|---|
| UC-K01 | Xem cây danh mục | Public |
| UC-K02 | Tạo / sửa / xóa danh mục | Admin |
| UC-K03 | Gán / đổi danh mục cho sản phẩm | Admin |
| UC-K04 | Tìm kiếm & lọc sản phẩm | Public |
| UC-K05 | Xem chi tiết sản phẩm (kèm danh mục) | Public |

## 3. QUY TẮC NGHIỆP VỤ
- BR-K1: Danh mục có tối đa **2 cấp** (gốc → con). Tạo con cho một danh mục đã là con ⇒ `CATEGORY_DEPTH_EXCEEDED`.
- BR-K2: Tên danh mục duy nhất **trong cùng cha**, không phân biệt hoa thường; `slug` duy nhất toàn hệ thống, sinh từ tên (bỏ dấu, nối gạch ngang), trùng thì thêm hậu tố số.
- BR-K3: Không xóa danh mục còn danh mục con hoặc còn sản phẩm ⇒ `CATEGORY_NOT_EMPTY`. Xóa là soft delete.
- BR-K4: Sản phẩm thuộc **không quá 1** danh mục, `categoryId` tùy chọn (sản phẩm cũ để trống). Chỉ gán được vào danh mục tồn tại và `ACTIVE`.
- BR-K5: Lọc theo danh mục cha bao gồm cả sản phẩm của các danh mục con.
- BR-K6: Tìm kiếm chỉ trả sản phẩm `ACTIVE`; `keyword` khớp một phần tên, không phân biệt hoa thường; bỏ khoảng trắng đầu/cuối, độ dài 2–100 (rỗng = không lọc theo từ khóa).
- BR-K7: `minPrice ≤ maxPrice`, đều ≥ 0; sai ⇒ `INVALID_REQUEST`. `inStock=true` chỉ lấy `totalStock > 0`.
- BR-K8: `sort` ∈ {`newest` (mặc định), `priceAsc`, `priceDesc`, `nameAsc`}; giá trị khác ⇒ `INVALID_REQUEST`. `size` mặc định 10, **tối đa 50** (vượt thì cắt về 50). Thứ tự phụ luôn `id` để phân trang ổn định.
- BR-K9: Tương thích ngược: tham số cũ `search` vẫn dùng được như `keyword`; `GET /api/v1/products` không tham số trả như hiện nay.
- BR-K10: Dữ liệu cache có thể cũ tối đa: danh sách 60 s, chi tiết 10 phút (nhưng được xóa ngay khi Admin sửa/xóa), cây danh mục 1 giờ (xóa ngay khi Admin đổi). Tồn kho hiển thị chỉ mang tính tham khảo.

## 4. THIẾT KẾ DỮ LIỆU (Flyway `V2__catalog.sql`)
- Bật extension: `CREATE EXTENSION IF NOT EXISTS pg_trgm;`
- **`categories`**: `id` BIGSERIAL PK · `name` VARCHAR(100) NOT NULL · `slug` VARCHAR(120) UNIQUE NOT NULL · `parent_id` BIGINT FK categories NULL · `sort_order` INT DEFAULT 0 · `status` VARCHAR(20) DEFAULT 'ACTIVE' · `is_deleted` BOOLEAN DEFAULT FALSE · `created_at`, `updated_at`.
  - UNIQUE `(parent_id, lower(name))` (cha NULL xử lý bằng index riêng cho gốc).
- **`products`** (sửa): thêm `category_id` BIGINT NULL FK categories.
- **Index:**
  - `idx_products_name_trgm` – `GIN (lower(name) gin_trgm_ops)`
  - `idx_products_category_status_price` – `(category_id, status, original_price)`
  - `idx_products_status_created` – `(status, created_at DESC)`
  - `idx_categories_parent` – `(parent_id)`

**Gói/lớp dự kiến:** `modules/category` (Category, CategoryRepository, CategoryService, CategoryController, AdminCategoryController, dto) · `product`: `ProductSpecifications`, `ProductSearchRequest`, cập nhật `ProductResponse/CreateProductRequest/UpdateProductRequest` (thêm `categoryId`, `categoryName`), `CacheConfig`, `CacheErrorHandler`.

## 5. HỢP ĐỒNG API

### 5.1. Danh mục
**`GET /api/v1/categories`** – Public, trả cây (đã cache):
```json
{ "code": 1000, "message": "Lấy danh mục thành công",
  "data": [ { "id": 1, "name": "Điện tử", "slug": "dien-tu", "productCount": 120,
              "children": [ { "id": 4, "name": "Bàn phím", "slug": "ban-phim",
                              "productCount": 35, "children": [] } ] } ] }
```
`productCount` đếm sản phẩm `ACTIVE`; của cha gồm cả con.

| API (Admin, `hasRole('ADMIN')`) | Request | Ghi chú |
|---|---|---|
| `POST /api/v1/admin/categories` | `{ "name": "Bàn phím", "parentId": 1, "sortOrder": 0 }` | 201; `parentId` null = gốc |
| `PUT /api/v1/admin/categories/{id}` | `{ "name": "...", "sortOrder": 1, "status": "ACTIVE" }` | Không đổi cha ở phase này |
| `DELETE /api/v1/admin/categories/{id}` | – | Theo BR-K3 |

Validation: `name` `@NotBlank` `@Size(max=100)`.

### 5.2. Gán danh mục cho sản phẩm
`POST /api/v1/products` và `PUT /api/v1/products/{id}` (Admin) nhận thêm `"categoryId": 4` (tùy chọn; `null` khi PUT = bỏ danh mục).

### 5.3. Tìm kiếm & lọc – `GET /api/v1/products` (Public)
Tham số: `keyword`, `categoryId`, `minPrice`, `maxPrice`, `inStock`, `sort`, `page` (từ 0), `size`.
Ví dụ: `/api/v1/products?keyword=ban phim&categoryId=1&minPrice=200000&maxPrice=800000&inStock=true&sort=priceAsc&page=0&size=20`

Response `PageResponse<ProductResponse>` – mỗi phần tử thêm `categoryId`, `categoryName`.

`GET /api/v1/products/{id}` trả thêm `categoryId`, `categoryName`.

### 5.4. Mã lỗi mới (dải 5xxx)
| Mã | Tên | HTTP |
|---|---|---|
| 5001 | CATEGORY_NOT_FOUND | 404 |
| 5002 | CATEGORY_NAME_DUPLICATED | 409 |
| 5003 | CATEGORY_NOT_EMPTY | 400 |
| 5004 | CATEGORY_DEPTH_EXCEEDED | 400 |

## 6. LUỒNG XỬ LÝ (cho Dev)
**Tìm kiếm:** chuẩn hóa tham số (BR-K6..K8) ⇒ tạo khóa cache từ bộ tham số đã chuẩn hóa ⇒ cache hit trả ngay; miss ⇒ dựng `Specification` (status=ACTIVE, keyword ILIKE, categoryId IN {id + id con}, khoảng giá, tồn) ⇒ truy vấn phân trang ⇒ map DTO ⇒ lưu cache 60 s.
**Sửa/xóa sản phẩm, đổi danh mục:** `@CacheEvict` `product:detail` theo id; `category:tree` xóa khi Admin đổi danh mục hoặc đổi danh mục của sản phẩm (vì `productCount`).
**Redis lỗi:** `CacheErrorHandler` ghi log cảnh báo rồi đọc thẳng DB; API vẫn 200.

## 7. TIÊU CHÍ NGHIỆM THU & MA TRẬN KIỂM THỬ (cho Tester)
| # | Kịch bản | Kết quả mong đợi |
|---|---|---|
| K01 | Admin tạo danh mục gốc | 201, có `slug` |
| K02 | Tạo danh mục con dưới gốc | 201 |
| K03 | Tạo cấp 3 | 400 `CATEGORY_DEPTH_EXCEEDED` |
| K04 | Trùng tên cùng cha (khác hoa/thường) | 409 `CATEGORY_NAME_DUPLICATED` |
| K05 | Cùng tên nhưng khác cha | 201 |
| K06 | Xóa danh mục còn con / còn sản phẩm | 400 `CATEGORY_NOT_EMPTY` |
| K07 | Xóa danh mục rỗng | 200; không còn trong cây |
| K08 | User thường gọi API admin danh mục | 403; chưa đăng nhập 401 |
| K09 | Gán sản phẩm vào danh mục tồn tại / không tồn tại | 200 / 404 `CATEGORY_NOT_FOUND` |
| K10 | Lọc theo danh mục cha | Gồm sản phẩm của con |
| K11 | `keyword` hoa/thường, có khoảng trắng đầu cuối | Kết quả như nhau |
| K12 | `keyword` 1 ký tự hoặc >100 ký tự | 400 `INVALID_REQUEST` |
| K13 | `minPrice > maxPrice`, giá âm | 400 `INVALID_REQUEST` |
| K14 | `inStock=true` | Không có sản phẩm tồn 0 |
| K15 | Mỗi giá trị `sort` hợp lệ | Đúng thứ tự; giá trị lạ ⇒ 400 |
| K16 | `size=1000` | Trả tối đa 50 |
| K17 | Phân trang: trang n và n+1 | Không trùng/mất phần tử khi giá bằng nhau |
| K18 | Sản phẩm `INACTIVE` | Không xuất hiện trong tìm kiếm |
| K19 | Gọi cũ `?search=abc`, và không tham số | Hoạt động như trước |
| K20 | Admin sửa tên sản phẩm rồi GET chi tiết | Thấy ngay giá trị mới (cache đã xóa) |
| K21 | Admin gán sản phẩm sang danh mục khác | `productCount` trong cây cập nhật |
| K22 | Gọi cùng truy vấn 2 lần | Lần 2 từ cache (kiểm bằng log/số câu SQL) |
| K23 | Tắt Redis | API đọc vẫn 200, có log cảnh báo (NFR-6) |
| K24 | 100.000 sản phẩm seed, tìm theo keyword | p95 < 100 ms miss, < 15 ms hit; `EXPLAIN` dùng `idx_products_name_trgm` (NFR-2) |
| K25 | `GET /categories` khi có 1 danh mục bị soft delete | Không xuất hiện |

---

## Kết quả triển khai & kiểm thử (Dev/Test)

**Trạng thái Gate 2:** đã chạy `mvnw test`: 99/100 pass; 1 lỗi có sẵn ở `FlashSaleEngineServiceTest.placeOrder_NotWarmedUp`, không thuộc phạm vi nhánh này. Chưa kiểm tra trên DB có dữ liệu cũ.

**Test đã viết:** `ProductSearchCriteriaTest` (K11–K16, K19), `CategoryServiceTest` (K01–K07).
Chưa có test tích hợp cho truy vấn tìm kiếm thật (pg_trgm, lọc giá, inStock) và cache (K08–K10, K17–K18, K20–K25).

**Ghi chú:** cache dùng `RedisCacheService` tùy biến; Flyway baseline-version=1 để DB đã có sẵn bỏ qua V1.
