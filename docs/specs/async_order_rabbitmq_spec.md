# ĐẶC TẢ KỸ THUẬT: XỬ LÝ ĐƠN HÀNG BẤT ĐỒNG BỘ QUA RABBITMQ (MESSAGE QUEUE)
> **Tài liệu lưu vết theo BMAD AI-DLC (Phase 5 - Asynchronous Queue Processing & Reliability)**

---

## 1. MỤC TIÊU NGHIỆP VỤ (BA - Mary)
- **Tối ưu hóa độ trễ phản hồi (Response Latency < 10ms)**: Thay vì bắt người dùng chờ ghi đĩa PostgreSQL (mất 100ms - 500ms dưới tải cao), hệ thống phản hồi ngay HTTP `202 Accepted` kèm mã theo dõi `orderTrackingId` ngay sau khi trừ kho thành công trên Redis RAM (< 2ms).
- **Phân tách luồng xử lý (Decoupling Architecture)**: Tách luồng nhận đơn (Ingestion layer) khỏi luồng ghi cơ sở dữ liệu (Persistence layer), bảo vệ PostgreSQL không bị quá tải hay connection starvation.
- **Đảm bảo tính Idempotency (Chống xử lý trùng lặp)**: Mỗi yêu cầu có một mã UUID `orderTrackingId` duy nhất. Consumer kiểm tra DB trước khi xử lý, đảm bảo tin nhắn gửi lại do retry không tạo thêm đơn hàng hoặc trừ kho trùng.
- **Xử lý lỗi tin cậy (DLX / DLQ Pattern)**: Cấu hình retry 3 lần với exponential backoff. Khi vượt quá số lần thử, tin nhắn tự động chuyển vào Dead Letter Queue (`flashsale.order.dlq`) để đội ngũ vận hành kiểm tra và khắc phục mà không làm mất mát dữ liệu.

---

## 2. KIẾN TRÚC KỸ THUẬT (Architect - Winston)

### 2.1. RabbitMQ Topology & Configuration
- **Exchange chính**:
  - Tên: `flashsale.order.exchange`
  - Kiểu: `DirectExchange` (Durable)
- **Hàng đợi chính (Main Queue)**:
  - Tên: `flashsale.order.queue`
  - Thuộc tính:
    - `durable: true`
    - `x-dead-letter-exchange: flashsale.order.dlx`
    - `x-dead-letter-routing-key: flashsale.order.dlq`
  - Routing Key: `flashsale.order.create`
- **Dead Letter Exchange (DLX)**:
  - Tên: `flashsale.order.dlx`
  - Kiểu: `DirectExchange` (Durable)
- **Dead Letter Queue (DLQ)**:
  - Tên: `flashsale.order.dlq`
  - Routing Key: `flashsale.order.dlq`
- **Message Serialization**:
  - `Jackson2JsonMessageConverter` kết hợp `JavaTimeModule` để tuần tự hóa DTO thành định dạng JSON chuẩn.

### 2.2. Luồng xử lý chi tiết (End-to-End Workflow)
1. **Client** gửi request đặt mua `POST /api/v1/flash-sales/orders`.
2. **FlashSaleEngineService**:
   - Kiểm tra thời gian sự kiện Flash Sale còn hiệu lực.
   - Gọi Redis Lua Script (`flash_sale_stock_deduct.lua`) trừ tồn kho trong RAM và lưu lịch sử người mua.
   - Sinh UUID `orderTrackingId`.
   - Lưu trạng thái tạm thời vào Redis: `flashsale:order:tracking:{orderTrackingId} = "PENDING_PROCESSING"` (TTL 24h).
   - Đẩy `FlashSaleOrderMessage` vào RabbitMQ Exchange.
   - Trả về ngay lập tức cho Client với mã HTTP `202 Accepted` và `orderTrackingId`.
3. **FlashSaleOrderConsumer** (chạy ngầm độc lập):
   - Nhận tin nhắn từ `flashsale.order.queue`.
   - Kiểm tra `orderTrackingId` trong bảng `flash_sale_orders` (Idempotency Guard).
   - Cập nhật kho khả dụng của `FlashSaleProduct` trong PostgreSQL.
   - Lưu bản ghi `FlashSaleOrder` mới với trạng thái `PENDING`.
   - Cập nhật Redis cache: `flashsale:order:tracking:{orderTrackingId} = "SUCCESS:{orderId}"`.
4. **Client Polling**:
   - Client định kỳ gọi `GET /api/v1/flash-sales/orders/tracking/{trackingId}` để cập nhật giao diện người dùng từ "Đang xử lý" sang "Thành công".

### 2.3. Cập nhật Cơ sở Dữ liệu PostgreSQL
- Bổ sung trường `order_tracking_id VARCHAR(64) UNIQUE` vào bảng `flash_sale_orders`:
  ```sql
  ALTER TABLE flash_sale_orders ADD COLUMN order_tracking_id VARCHAR(64) UNIQUE;
  ```

---

## 3. DANH SÁCH RESTful API CONTRACTS

### 3.1. Đặt mua sản phẩm Flash Sale bất đồng bộ (`POST /api/v1/flash-sales/orders`)
- **Phân quyền**: `@PreAuthorize("isAuthenticated()")`
- **Request Body**:
  ```json
  {
    "eventId": 1,
    "productId": 5,
    "quantity": 2
  }
  ```
- **Response (202 Accepted)**:
  ```json
  {
    "code": 1000,
    "message": "Yêu cầu đặt mua Flash-Sale đã được tiếp nhận và đang xử lý",
    "data": {
      "orderTrackingId": "e1f9a8b2-4c3d-4e5f-8a9b-0c1d2e3f4a5b",
      "status": "PENDING_PROCESSING",
      "eventId": 1,
      "productId": 5,
      "quantity": 2,
      "message": "Yêu cầu đặt mua Flash Sale đã được tiếp nhận và đang xử lý"
    }
  }
  ```

### 3.2. Tra cứu trạng thái đơn hàng bất đồng bộ (`GET /api/v1/flash-sales/orders/tracking/{trackingId}`)
- **Phân quyền**: `@PreAuthorize("isAuthenticated()")`
- **Response (200 OK - Đã xử lý xong)**:
  ```json
  {
    "code": 1000,
    "message": "Lấy trạng thái xử lý đơn hàng thành công",
    "data": {
      "orderTrackingId": "e1f9a8b2-4c3d-4e5f-8a9b-0c1d2e3f4a5b",
      "trackingStatus": "SUCCESS",
      "orderId": 42,
      "userId": 10,
      "eventId": 1,
      "productId": 5,
      "quantity": 2,
      "unitPrice": 15000000.00,
      "totalPrice": 30000000.00,
      "orderStatus": "PENDING",
      "createdAt": "2026-09-06T14:02:21"
    }
  }
  ```

### 3.3. Đặt mua đồng bộ trực tiếp DB - Fallback (`POST /api/v1/flash-sales/orders/sync`)
- **Phân quyền**: `@PreAuthorize("isAuthenticated()")`
- **Response (201 Created)**: Trả về đối tượng `FlashSaleOrderResponse` đầy đủ.

---

## 4. BÁO CÁO KIỂM THỬ CHẤT LƯỢNG (QA - Bob)
- **Tổng số ca kiểm thử toàn dự án**: 29/29 tests PASS (0 failure, 0 error).
- **Các ca kiểm thử trọng tâm của Phase 5**:
  1. `FlashSaleOrderProducerTest`: Kiểm tra gửi thành công message serialization tới RabbitTemplate.
  2. `FlashSaleOrderConsumerTest`:
     - Kiểm tra tiêu thụ tin nhắn, trừ kho DB, lưu order và ghi nhận tracking cache thành công.
     - Kiểm tra tính Idempotency: Bỏ qua không xử lý lại khi `orderTrackingId` đã tồn tại trong DB.
  3. `FlashSaleOrderControllerTest`:
     - `placeOrderAsync_Success_Returns202`: Trả về đúng mã HTTP 202 Accepted và `orderTrackingId`.
     - `placeOrder_InvalidQuantity_Returns400`: Bắt lỗi Bean Validation khi số lượng <= 0.
     - `getTrackingStatus_Success_Returns200`: Tra cứu trạng thái đơn hàng thành công.
     - `placeOrder_Unauthenticated_Returns401`: Chặn người dùng chưa đăng nhập.
