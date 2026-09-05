# ĐẶC TẢ KỸ THUẬT: MODULE FLASH SALE (PHASE 3 SPEC)
> **Tài liệu lưu vết theo BMAD AI-DLC (Step 3: Operations & Memory)**

---

## 1. MỤC TIÊU NGHIỆP VỤ (BA - Mary)
- Hỗ trợ tổ chức các sự kiện giảm giá chớp nhoáng (Flash Sale) trong khung giờ cố định.
- **Cơ chế Ring-fencing (Cô lập tồn kho)**: Khi phân bổ sản phẩm vào sự kiện Flash Sale, hệ thống trích trừ ngay lập tức từ `Product.totalStock` sang `FlashSaleProduct.availableStock`, ngăn chặn triệt để tình trạng bán vượt tồn kho (Overselling) giữa luồng mua thông thường và luồng Flash Sale.
- Mỗi người dùng chỉ được mua tối đa một số lượng quy định (`purchaseLimitPerUser`) trong mỗi sự kiện.
- Cung cấp dữ liệu đồng hồ đếm ngược `remainingSeconds` phục vụ giao diện người dùng.

---

## 2. KIẾN TRÚC & MÔ HÌNH DỮ LIỆU (Architect - Winston)

### 2.1. Cấu trúc Bảng Database (PostgreSQL)
- **`flash_sale_events`**:
  - `id`: `BIGSERIAL PRIMARY KEY`
  - `name`: `VARCHAR(255) NOT NULL`
  - `start_time`: `TIMESTAMP NOT NULL`
  - `end_time`: `TIMESTAMP NOT NULL`
  - `status`: `VARCHAR(50) NOT NULL` (`UPCOMING`, `ACTIVE`, `ENDED`)
  - `created_at`, `updated_at`: `TIMESTAMP`
- **`flash_sale_products`**:
  - `id`: `BIGSERIAL PRIMARY KEY`
  - `flash_sale_event_id`: `BIGINT REFERENCES flash_sale_events(id) ON DELETE CASCADE`
  - `product_id`: `BIGINT REFERENCES products(id)`
  - `flash_sale_price`: `NUMERIC(15, 2) NOT NULL`
  - `allocated_quantity`: `INT NOT NULL` (Tổng số lượng admin trích xuất ban đầu)
  - `available_stock`: `INT NOT NULL` (Số lượng còn lại có thể bán trong sự kiện)
  - `purchase_limit_per_user`: `INT NOT NULL DEFAULT 1`
  - `version`: `BIGINT NOT NULL DEFAULT 0` (Khóa lạc quan `@Version`)
  - `created_at`, `updated_at`: `TIMESTAMP`

### 2.2. Quy tắc nghiệp vụ Ring-fencing
1. Kiểm tra sự kiện tồn tại và chưa kết thúc (`status != ENDED`).
2. Kiểm tra `product.totalStock >= allocatedQuantity`.
3. Trừ `product.totalStock -= allocatedQuantity`.
4. Tạo mới hoặc cập nhật `flash_sale_products` với `availableStock = allocatedQuantity`.
5. Nếu tồn kho gốc không đủ: Ném `AppException(ErrorCode.OUT_OF_STOCK)`.

---

## 3. DANH SÁCH RESTful API CONTRACT (Architect - Winston)

### 3.1. Admin tạo sự kiện Flash Sale (`POST /api/v1/admin/flash-sales`)
- **Phân quyền**: `@PreAuthorize("hasRole('ADMIN')")`
- **Request Body**:
  ```json
  {
    "name": "Super Sale 12h Trưa",
    "startTime": "2026-09-04T12:00:00",
    "endTime": "2026-09-04T14:00:00"
  }
  ```
- **Response (201 Created)**

### 3.2. Admin phân bổ sản phẩm vào sự kiện (`POST /api/v1/admin/flash-sales/{eventId}/products`)
- **Phân quyền**: `@PreAuthorize("hasRole('ADMIN')")`
- **Request Body**:
  ```json
  {
    "productId": 1,
    "flashSalePrice": 499000,
    "allocatedQuantity": 20,
    "purchaseLimitPerUser": 1
  }
  ```
- **Response (201 Created)**

### 3.3. Người dùng xem các sự kiện Flash Sale đang diễn ra (`GET /api/v1/flash-sales/active`)
- **Phân quyền**: Public
- **Response (200 OK)**:
  ```json
  {
    "code": 1000,
    "message": "Get active flash sale events successfully",
    "data": [
      {
        "id": 1,
        "name": "Super Sale 12h Trưa",
        "startTime": "2026-09-04T12:00:00",
        "endTime": "2026-09-04T14:00:00",
        "status": "ACTIVE",
        "remainingSeconds": 3600,
        "products": [
          {
            "id": 1,
            "productId": 1,
            "productName": "Bàn phím cơ không dây",
            "flashSalePrice": 499000,
            "allocatedQuantity": 20,
            "availableStock": 20,
            "purchaseLimitPerUser": 1
          }
        ]
      }
    ]
  }
  ```

---

## 4. KẾT QUẢ THỰC THI & KIỂM THỬ (Amelia & Bob)
- Các file triển khai: `FlashSaleEvent`, `FlashSaleProduct`, `FlashSaleEventRepository`, `FlashSaleProductRepository`, `FlashSaleService`, `AdminFlashSaleController`, `PublicFlashSaleController`.
- Đã kiểm thử tự động tại `FlashSaleControllerTest.java` (Pass 100%).
