# ĐẶC TẢ KỸ THUẬT: MODULE QUẢN LÝ SẢN PHẨM (PRODUCT SPEC)
> **Tài liệu lưu vết theo BMAD AI-DLC (Step 3: Operations & Memory)**

---

## 1. MỤC TIÊU NGHIỆP VỤ (BA - Mary)
- Quản lý danh mục sản phẩm tiêu chuẩn trong sàn thương mại điện tử MultiMart.
- Khách hàng (Public/Guest) có thể tìm kiếm, xem danh sách sản phẩm phân trang và chi tiết sản phẩm.
- Quản trị viên (`ROLE_ADMIN`) có quyền tạo mới, cập nhật thông tin và xóa mềm (Soft Delete) sản phẩm để tránh gãy liên kết dữ liệu với các đơn hàng lịch sử.

---

## 2. KIẾN TRÚC & MÔ HÌNH DỮ LIỆU (Architect - Winston)

### 2.1. Cấu trúc Bảng Database (PostgreSQL)
- **`products`**:
  - `id`: `BIGSERIAL PRIMARY KEY`
  - `name`: `VARCHAR(255) NOT NULL`
  - `description`: `TEXT`
  - `price`: `NUMERIC(15, 2) NOT NULL`
  - `total_stock`: `INT NOT NULL`
  - `image_url`: `VARCHAR(500)`
  - `is_deleted`: `BOOLEAN DEFAULT FALSE` (Soft Delete flag)
  - `created_at`, `updated_at`: `TIMESTAMP` (JPA Auditing)

### 2.2. Quy tắc nghiệp vụ (Business Rules)
- Giá `price` và tồn kho `total_stock` phải `>= 0`.
- Khi xóa sản phẩm (`DELETE`), không dùng lệnh `DELETE FROM products WHERE id = ?` mà cập nhật `is_deleted = true`.
- Các câu truy vấn danh sách thông thường chỉ lấy bản ghi có `is_deleted = false`.

---

## 3. DANH SÁCH RESTful API CONTRACT (Architect - Winston)

### 3.1. Lấy danh sách sản phẩm phân trang (`GET /api/v1/products`)
- **Phân quyền**: Public
- **Query Params**: `page` (default: 0), `size` (default: 10), `sort` (default: `createdAt,desc`)
- **Response (200 OK)**:
  ```json
  {
    "code": 1000,
    "message": "Get products successfully",
    "data": {
      "content": [
        {
          "id": 1,
          "name": "Bàn phím cơ không dây",
          "price": 1200000.00,
          "totalStock": 50,
          "imageUrl": "https://...",
          "createdAt": "2026-09-03T10:00:00"
        }
      ],
      "pageNo": 0,
      "pageSize": 10,
      "totalElements": 1,
      "totalPages": 1,
      "last": true
    }
  }
  ```

### 3.2. Xem chi tiết sản phẩm (`GET /api/v1/products/{id}`)
- **Phân quyền**: Public
- **Response (200 OK)**: Chi tiết thông tin sản phẩm.
- **Errors**: `404 PRODUCT_NOT_FOUND` nếu ID không tồn tại hoặc đã bị xóa mềm.

### 3.3. Tạo mới sản phẩm (`POST /api/v1/products`)
- **Phân quyền**: `@PreAuthorize("hasRole('ADMIN')")`
- **Request Body**:
  ```json
  {
    "name": "Bàn phím cơ không dây",
    "description": "Switch Brown gõ êm",
    "price": 1200000,
    "totalStock": 50,
    "imageUrl": "https://..."
  }
  ```
- **Response (201 Created)**

### 3.4. Cập nhật sản phẩm (`PUT /api/v1/products/{id}`)
- **Phân quyền**: `@PreAuthorize("hasRole('ADMIN')")`
- **Response (200 OK)**

### 3.5. Xóa mềm sản phẩm (`DELETE /api/v1/products/{id}`)
- **Phân quyền**: `@PreAuthorize("hasRole('ADMIN')")`
- **Response (200 OK)**

---

## 4. KẾT QUẢ THỰC THI & KIỂM THỬ (Amelia & Bob)
- Các file triển khai: `Product`, `ProductRepository`, `ProductService`, `ProductController`, DTOs (`CreateProductRequest`, `UpdateProductRequest`, `ProductResponse`).
- Đã kiểm thử tự động tại `ProductControllerTest.java` (Pass 100%).
