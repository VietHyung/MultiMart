# ĐẶC TẢ KỸ THUẬT: ĐỘNG CƠ FLASH SALE CHỊU TẢI CAO (REDIS + LUA SCRIPT)
> **Tài liệu lưu vết theo BMAD AI-DLC (Phase 4 - Operations & Memory)**

---

## 1. MỤC TIÊU NGHIỆP VỤ (BA - Mary)
- **Giải quyết bài toán High-Concurrency Flash Sale**: Ngăn ngừa Database Connection Bottleneck trên PostgreSQL khi có hàng trăm nghìn người dùng tranh mua tại cùng một thời điểm.
- **Chống bán vượt tồn kho (Prevent Overselling)**: Kiểm tra và trừ tồn kho trực tiếp trong bộ nhớ RAM với độ trễ < 5ms.
- **Kiểm soát giới hạn mua (Purchase Limit per User)**: Đảm bảo mỗi tài khoản chỉ được mua tối đa số lượng quy định (`purchaseLimitPerUser`) trong mỗi sự kiện.
- **Cơ chế Cache Warm-up**: Cho phép Quản trị viên nạp trước dữ liệu tồn kho lên Redis trước khi mở bán.

---

## 2. KIẾN TRÚC KỸ THUẬT (Architect - Winston)

### 2.1. Cấu trúc Dữ liệu trên Redis (In-Memory Data Structures)
- **Tồn kho khả dụng**: 
  - Key: `flashsale:stock:{eventId}:{productId}`
  - Type: `String (Integer)`
  - Ý nghĩa: Lưu số lượng hàng có thể bán còn lại trong RAM.
- **Lịch sử mua hàng của User**:
  - Key: `flashsale:buyers:{eventId}:{productId}`
  - Type: `Hash` (Field: `userId`, Value: số lượng đã mua)
  - Ý nghĩa: Kiểm tra số lượng người dùng đã mua trong sự kiện để chặn mua vượt giới hạn.

### 2.2. Mã nguồn Redis Lua Script (Atomic Execution)
Vị trí: `src/main/resources/scripts/flash_sale_stock_deduct.lua`
- Thực thi đơn luồng nguyên tử trên Redis server, đảm bảo không có race condition giữa 2 request cạnh tranh.
- Các mã phản hồi:
  - `>= 0`: Thành công, trả về số lượng tồn kho còn lại.
  - `-1`: Hết hàng (`OUT_OF_STOCK`).
  - `-2`: Đã vượt quá giới hạn mua của tài khoản (`PURCHASE_LIMIT_EXCEEDED`).
  - `-3`: Sự kiện chưa được warm-up trên Redis (`FLASH_SALE_NOT_WARMED_UP`).

### 2.3. Bảng Cơ sở Dữ liệu PostgreSQL
- **`flash_sale_orders`**:
  - `id`: `BIGSERIAL PRIMARY KEY`
  - `user_id`: `BIGINT NOT NULL REFERENCES users(id)`
  - `flash_sale_event_id`: `BIGINT NOT NULL REFERENCES flash_sale_events(id)`
  - `flash_sale_product_id`: `BIGINT NOT NULL REFERENCES flash_sale_products(id)`
  - `quantity`: `INT NOT NULL`
  - `unit_price`: `NUMERIC(15, 2) NOT NULL`
  - `total_price`: `NUMERIC(15, 2) NOT NULL`
  - `status`: `VARCHAR(50) NOT NULL` (`PENDING`, `CONFIRMED`, `CANCELLED`)
  - `created_at`, `updated_at`: `TIMESTAMP` (JPA Auditing)

---

## 3. DANH SÁCH RESTful API CONTRACTS

### 3.1. Admin làm nóng dữ liệu Flash Sale lên Redis (`POST /api/v1/admin/flash-sales/{id}/warm-up`)
- **Phân quyền**: `@PreAuthorize("hasRole('ADMIN')")`
- **Response (200 OK)**:
  ```json
  {
    "code": 1000,
    "message": "Làm nóng dữ liệu Flash Sale thành công",
    "data": {
      "eventId": 1,
      "eventName": "Super Flash Sale 12h",
      "warmedUpProductsCount": 5,
      "message": "Đã nạp thành công dữ liệu 5 sản phẩm lên Redis Cache"
    }
  }
  ```

### 3.2. Đặt mua sản phẩm Flash Sale tốc độ cao (`POST /api/v1/flash-sales/orders`)
- **Phân quyền**: Bắt buộc đăng nhập (`@PreAuthorize("isAuthenticated()")`)
- **Request Body**:
  ```json
  {
    "eventId": 1,
    "productId": 10,
    "quantity": 1
  }
  ```
- **Response (201 Created)**:
  ```json
  {
    "code": 1000,
    "message": "Đặt mua sản phẩm Flash-Sale thành công",
    "data": {
      "orderId": 101,
      "userId": 5,
      "eventId": 1,
      "productId": 10,
      "quantity": 1,
      "unitPrice": 499000.00,
      "totalPrice": 499000.00,
      "status": "PENDING",
      "createdAt": "2026-09-05T17:05:00"
    }
  }
  ```

### 3.3. Xem lịch sử đơn hàng Flash Sale cá nhân (`GET /api/v1/flash-sales/orders/my-orders`)
- **Phân quyền**: Bắt buộc đăng nhập (`@PreAuthorize("isAuthenticated()")`)
- **Query Params**: `page` (default: 0), `size` (default: 10)
- **Response (200 OK)**: Danh sách đơn hàng dạng `PageResponse<FlashSaleOrderResponse>`.

---

## 4. BÁO CÁO THỰC THI & KIỂM THỬ (Amelia & Bob)
- Các file triển khai:
  - `RedisConfig.java`: Cấu hình RedisTemplate, StringRedisTemplate, nạp Lua script.
  - `flash_sale_stock_deduct.lua`: Script trừ tồn kho nguyên tử.
  - `FlashSaleOrder.java`, `FlashSaleOrderStatus.java`, `FlashSaleOrderRepository.java`.
  - `FlashSaleOrderRequest.java`, `FlashSaleOrderResponse.java`, `WarmUpEventResponse.java`.
  - `FlashSaleEngineService.java`: Logic warm-up, xử lý đặt mua, lưu đơn hàng.
  - `FlashSaleOrderController.java`: Endpoint đặt mua và xem lịch sử đơn hàng.
  - `AdminFlashSaleController.java`: Endpoint warm-up cho Admin.
- Kiểm thử tự động:
  - `FlashSaleEngineServiceTest.java`: 7 test cases (Warm-up, đặt mua thành công, kiểm tra giới hạn mua, hết hàng, sự kiện chưa kích hoạt).
  - `FlashSaleOrderControllerTest.java`: 4 test cases (Chặn 401 khi chưa đăng nhập, trả 201 khi thành công, validation @Min, lấy lịch sử đơn).
  - Toàn bộ test suite dự án: **22/22 tests PASS (100%)**.
