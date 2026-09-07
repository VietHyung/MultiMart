# ĐẶC TẢ KỸ THUẬT: HỦY ĐƠN QUÁ HẠN & HOÀN TRẢ TỒN KHO TỰ ĐỘNG (RABBITMQ TTL/DLX + REDIS LUA ROLLBACK)
> **Tài liệu lưu vết theo BMAD AI-DLC (Phase 6 - Order Expiration & Stock Rollback)**

---

## 1. MỤC TIÊU NGHIỆP VỤ (BA - Mary)
- **Xử lý bài toán "Giữ chỗ ảo" (Ghost Inventory)**: Khi người dùng săn Flash Sale thành công, đơn hàng ở trạng thái `PENDING`. Nếu người dùng không thanh toán (bỏ quên giỏ hàng, đầu cơ hoặc bot giữ hàng), tồn kho bị khóa vô thời hạn khiến khách hàng thật không mua được.
- **Thời hạn thanh toán giới hạn (15 phút)**: Khách hàng có 15 phút (cấu hình qua `flashsale.order.timeout-ms`) để hoàn tất thanh toán.
- **Tự động hủy đơn và hoàn trả kho (Stock Rollback)**:
  1. Hết 15 phút nếu đơn vẫn ở trạng thái `PENDING`: Hệ thống tự động chuyển trạng thái đơn sang `CANCELLED`.
  2. Hoàn trả tồn kho nguyên tử trên RAM Redis (`flashsale:stock:{eventId}:{productId}`) bằng đúng số lượng `quantity`.
  3. Hoàn trả hạn mức đã mua trong Hash người mua (`flashsale:buyers:{eventId}:{productId}`), cho phép khách hàng tiếp tục mua lại nếu sự kiện còn hiệu lực.
  4. Hoàn trả số lượng tồn kho khả dụng `availableStock` trong cơ sở dữ liệu PostgreSQL (`flash_sale_products`).
- **Chống Race Condition tại thời điểm hết hạn**: Đảm bảo tính nhất quán (Consistency) tuyệt đối:
  - Đơn hàng đã bị hủy (`CANCELLED`) thì không thể thanh toán tiếp (báo lỗi `3010: ORDER_ALREADY_CANCELLED`).
  - Đơn hàng đã thanh toán (`CONFIRMED`) thì Worker hủy đơn khi nhận tin nhắn hết hạn sẽ bỏ qua (Skip rollback).

---

## 2. KIẾN TRÚC KỸ THUẬT (Architect - Winston)

### 2.1 Topology RabbitMQ (Native Dead Letter Exchange + Message TTL)
Thay vì sử dụng plugin bên ngoài, hệ thống dùng mô hình chuẩn công nghiệp:
- **Delay Queue (`flashsale.order.delay.queue`)**:
  - `x-dead-letter-exchange`: `flashsale.order.cancel.exchange`
  - `x-dead-letter-routing-key`: `flashsale.order.cancel`
  - Không có Consumer nào lắng nghe trực tiếp trên queue này. Tin nhắn được giữ trong queue với thuộc tính `expiration: 900000` (15 phút).
- **Cancel Exchange (`flashsale.order.cancel.exchange`)**: Direct Exchange tiếp nhận tin nhắn đã hết hạn từ Delay Queue.
- **Cancel Queue (`flashsale.order.cancel.queue`)**: Hàng đợi tiếp nhận các yêu cầu hủy đơn quá hạn.
- **Consumer (`FlashSaleOrderCancelConsumer`)**: Lắng nghe `flashsale.order.cancel.queue` và kích hoạt luồng rollback.

### 2.2 Redis Lua Script: `flash_sale_stock_rollback.lua`
Vị trí: `src/main/resources/scripts/flash_sale_stock_rollback.lua`
- Thực thi nguyên tử trên RAM Redis:
  1. Tăng tồn kho: `redis.call('INCRBY', stockKey, rollbackQty)` nếu key tồn tại.
  2. Giảm số lượng đã mua của user: `currentBought - rollbackQty`. Nếu `<= 0` thì xóa hẳn field `userId` khỏi hash (`HDEL`), ngược lại cập nhật số mới (`HSET`).

### 2.3 Cập nhật Luồng Xử Lý (End-to-End Flow)
1. Sau khi `FlashSaleOrderConsumer` lưu thành công đơn hàng vào PostgreSQL (`savedOrder`), hệ thống tự động đẩy `FlashSaleOrderTimeoutMessage` vào `flashsale.order.delay.queue` với TTL 15 phút.
2. Sau 15 phút, RabbitMQ tự động đẩy message sang `flashsale.order.cancel.queue`.
3. `FlashSaleOrderCancelConsumer` tiêu thụ message, gọi `FlashSaleEngineService.rollbackOrderStock()`:
   - Nếu đơn hàng là `PENDING`: Cập nhật `CANCELLED`, hoàn kho PostgreSQL, chạy Lua script hoàn kho Redis, cập nhật tracking key `CANCELLED_TIMEOUT`.
   - Nếu đơn hàng đã là `CONFIRMED`: Bỏ qua (đơn đã thanh toán hợp lệ).
4. Khách hàng có thể thanh toán trước khi hết hạn qua API `POST /api/v1/flash-sales/orders/{id}/pay`.

---

## 3. DANH SÁCH RESTful API MỚI

### 3.1. Thanh toán / Xác nhận đơn hàng Flash Sale (`POST /api/v1/flash-sales/orders/{id}/pay`)
- **Phân quyền**: `@PreAuthorize("isAuthenticated()")`
- **Request URL**: `/api/v1/flash-sales/orders/10/pay`
- **Response (200 OK - Thanh toán thành công)**:
  ```json
  {
    "code": 1000,
    "message": "Thanh toán đơn hàng Flash-Sale thành công",
    "data": {
      "orderId": 10,
      "orderTrackingId": "e1f9a8b2-4c3d-4e5f-8a9b-0c1d2e3f4a5b",
      "status": "CONFIRMED",
      "totalPrice": 15000000.00,
      "paidAt": "2026-09-07T13:17:10",
      "message": "Thanh toán đơn hàng Flash Sale thành công"
    }
  }
  ```
- **Response Lỗi (400 Bad Request - Đơn đã hết hạn)**:
  ```json
  {
    "code": 3010,
    "message": "Đơn hàng đã hết hạn thanh toán hoặc đã bị hủy"
  }
  ```

---

## 4. BÁO CÁO KIỂM THỬ CHẤT LƯỢNG (QA - Bob)
- **Tổng số ca kiểm thử toàn dự án**: 43/43 tests PASS (0 failure, 0 error).
- **Các ca kiểm thử trọng tâm của Phase 6**:
  1. `FlashSaleOrderCancelConsumerTest`:
     - `processOrderCancelMessage_PendingOrder_Success`: Hủy đơn và rollback kho khi đơn ở trạng thái PENDING.
     - `processOrderCancelMessage_ConfirmedOrNotFound_Skipped`: Bỏ qua khi đơn đã thanh toán hoặc không tồn tại.
     - `processOrderCancelMessage_ServiceException_Rethrown`: Ném lại ngoại lệ để RabbitMQ kích hoạt Retry.
  2. `FlashSaleEngineServiceTest`:
     - `confirmPayment_Success`: Thanh toán thành công, chuyển `CONFIRMED` và cập nhật tracking cache.
     - `confirmPayment_OrderNotFound_ThrowsException`: Báo lỗi 404 khi đơn không tồn tại.
     - `confirmPayment_AccessDenied_ThrowsException`: Chặn truy cập trái phép IDOR khi user cố thanh toán đơn của người khác.
     - `confirmPayment_AlreadyCancelled_ThrowsException`: Báo lỗi 400 khi thanh toán đơn đã bị hủy.
     - `confirmPayment_AlreadyPaid_ThrowsException`: Báo lỗi 400 khi thanh toán đơn đã thanh toán.
     - `rollbackOrderStock_PendingOrder_Success`: Hoàn kho DB + Redis Lua script thành công.
     - `rollbackOrderStock_ConfirmedOrder_Skipped`: Bỏ qua rollback khi đơn đã `CONFIRMED`.
  3. `FlashSaleOrderControllerTest`:
     - `payOrder_Success_Returns200`: HTTP 200 OK.
     - `payOrder_Unauthenticated_Returns401`: HTTP 401 Unauthorized.
     - `payOrder_OrderAlreadyCancelled_Returns400`: HTTP 400 Bad Request.