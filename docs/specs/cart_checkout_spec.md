# SRS & DETAIL DESIGN: GIỎ HÀNG VÀ ĐẶT HÀNG THƯỜNG (CART & STANDARD CHECKOUT)
> **Vai trò:** BA (Mary) – tài liệu cho Dev đọc và Test đối chiếu.
> **Mã:** SRS-CART-001 · **Trạng thái:** CHỜ GATE 1 · **Kiến trúc:** xem `sa_architecture_phase7.md` (ADR-1..5)

---

## 1. MỤC TIÊU & PHẠM VI
**Mục tiêu:** cho phép khách mua sản phẩm thường (ngoài Flash Sale): gom vào giỏ, đặt hàng, thanh toán (giả lập), theo dõi và hủy đơn; Admin xử lý vận chuyển.

**Trong phạm vi:** giỏ hàng, checkout, thanh toán giả lập, hủy đơn, danh sách/chi tiết đơn, Admin cập nhật trạng thái.
**Ngoài phạm vi (phase sau):** mã giảm giá, phí vận chuyển, thanh toán thật (VNPay/Momo), áp giá Flash Sale trong giỏ, nhiều kho, hoàn tiền.

**Tác nhân:** Khách (`ROLE_USER`), Admin (`ROLE_ADMIN`). Mọi API bên dưới yêu cầu đăng nhập.

## 2. DANH SÁCH USE CASE
| Mã | Use case | Tác nhân |
|---|---|---|
| UC-C01 | Thêm sản phẩm vào giỏ | Khách |
| UC-C02 | Đổi số lượng dòng trong giỏ | Khách |
| UC-C03 | Xóa một dòng / xóa cả giỏ | Khách |
| UC-C04 | Xem giỏ (giá, tồn hiện tại, tổng tiền) | Khách |
| UC-O01 | Đặt hàng (checkout) | Khách |
| UC-O02 | Thanh toán đơn ONLINE (giả lập) | Khách |
| UC-O03 | Xem danh sách / chi tiết đơn của tôi | Khách |
| UC-O04 | Hủy đơn | Khách |
| UC-O05 | Admin xem danh sách đơn, lọc theo trạng thái | Admin |
| UC-O06 | Admin chuyển trạng thái đơn (giao hàng, hoàn tất, hủy) | Admin |
| UC-O07 | Hệ thống tự hủy đơn ONLINE quá hạn thanh toán | Hệ thống |

## 3. QUY TẮC NGHIỆP VỤ
**Giỏ hàng**
- BR-C1: Mỗi dòng số lượng 1–99; tối đa 50 dòng/giỏ.
- BR-C2: Chỉ thêm sản phẩm `ACTIVE` và còn tồn (`totalStock > 0`). Thêm cộng dồn vào dòng đã có; tổng dòng sau cộng không vượt tồn hiện tại và không vượt 99.
- BR-C3: Giỏ không lưu giá. Khi xem giỏ, mỗi dòng trả giá hiện tại và cờ `available` (sản phẩm còn `ACTIVE` và đủ tồn). Tổng tiền chỉ tính các dòng `available = true`.
- BR-C4: Giỏ hết hạn sau 7 ngày không hoạt động.

**Đặt hàng**
- BR-O1: Checkout lấy toàn bộ giỏ, hoặc tập con nếu truyền `productIds`. Giỏ/tập chọn rỗng ⇒ lỗi `CART_EMPTY`.
- BR-O2: Mọi sản phẩm phải `ACTIVE` và đủ tồn. **Tất cả hoặc không gì cả:** thiếu một sản phẩm thì không tạo đơn, lỗi trả danh sách `productId` bị thiếu.
- BR-O3: Giá chốt tại thời điểm đặt (đọc DB), lưu snapshot `product_name`, `unit_price` vào `order_items`. `total_amount = Σ(unit_price × quantity)`.
- BR-O4: Phương thức thanh toán: `COD` ⇒ trạng thái đầu `CONFIRMED`; `ONLINE` ⇒ `PENDING_PAYMENT`.
- BR-O5: Tồn kho bị trừ ngay khi đặt thành công. Sau khi commit, chỉ các dòng đã đặt bị xóa khỏi giỏ.
- BR-O6: Header `Idempotency-Key` bắt buộc; cùng key của cùng user ⇒ trả lại đơn cũ, không tạo mới.
- BR-O7: Mã đơn `MM` + `yyyyMMdd` + 6 ký tự chữ/số ngẫu nhiên in hoa, duy nhất.
- BR-O8: Thông tin nhận hàng bắt buộc: tên người nhận, số điện thoại (10 số bắt đầu bằng 0), địa chỉ; ghi chú tùy chọn (≤ 255 ký tự).

**Vòng đời trạng thái**
```
ONLINE: PENDING_PAYMENT ──pay──► CONFIRMED ──admin──► SHIPPING ──admin──► DELIVERED
              │ timeout/user            │ user/admin (trước SHIPPING)
              ▼                          ▼
          CANCELLED                  CANCELLED
COD:    (tạo) CONFIRMED ──► SHIPPING ──► DELIVERED
```
- BR-S1: Chuyển trạng thái ngoài sơ đồ ⇒ `INVALID_ORDER_STATUS_TRANSITION`.
- BR-S2: Hủy đơn (bất kỳ nguyên nhân) ⇒ hoàn tồn kho đúng số lượng từng dòng, đúng 1 lần (idempotent).
- BR-S3: Khách chỉ hủy được khi `PENDING_PAYMENT` hoặc `CONFIRMED`; Admin hủy được khi `PENDING_PAYMENT`/`CONFIRMED`. `SHIPPING`/`DELIVERED` không hủy được.
- BR-S4: Thanh toán chỉ cho đơn `ONLINE` đang `PENDING_PAYMENT`: đã `CONFIRMED` ⇒ `ORDER_ALREADY_PAID`; đã hủy/quá hạn ⇒ `ORDER_ALREADY_CANCELLED`.
- BR-S5: Quá `order.payment-timeout-ms` (mặc định 30 phút) mà còn `PENDING_PAYMENT` ⇒ hệ thống hủy (UC-O07). Đơn đã `CONFIRMED` thì bỏ qua.
- BR-S6: Khách chỉ thấy/thao tác đơn của chính mình, sai chủ ⇒ `ORDER_ACCESS_DENIED_STD`.

## 4. THIẾT KẾ DỮ LIỆU
**Redis (giỏ):** `cart:{userId}` Hash `{productId → quantity}`, TTL 7 ngày. Khóa chống trùng: `idem:order:{userId}:{key}` TTL 24h.

**PostgreSQL** (Flyway `V3__orders.sql`)
- **`orders`**: `id` BIGSERIAL PK · `order_code` VARCHAR(20) UNIQUE NOT NULL · `user_id` BIGINT FK users NOT NULL · `status` VARCHAR(30) NOT NULL · `payment_method` VARCHAR(10) NOT NULL · `total_amount` NUMERIC(15,2) NOT NULL · `receiver_name` VARCHAR(100) · `receiver_phone` VARCHAR(20) · `shipping_address` VARCHAR(500) · `note` VARCHAR(255) · `idempotency_key` VARCHAR(64) NOT NULL · `paid_at` TIMESTAMP · `cancelled_at` TIMESTAMP · `cancel_reason` VARCHAR(30) (`USER`, `ADMIN`, `PAYMENT_TIMEOUT`) · `version` BIGINT · `is_deleted` BOOLEAN · `created_at`, `updated_at`.
  - UNIQUE `(user_id, idempotency_key)`; INDEX `(user_id, created_at DESC)`; INDEX `(status, created_at)`.
- **`order_items`**: `id` PK · `order_id` FK orders ON DELETE CASCADE · `product_id` FK products · `product_name` VARCHAR(200) · `unit_price` NUMERIC(15,2) · `quantity` INT · `line_total` NUMERIC(15,2). INDEX `(order_id)`.

**Gói/lớp dự kiến:** `modules/cart` (CartController, CartService, dto) · `modules/order` (Order, OrderItem, OrderStatus, PaymentMethod, OrderRepository, OrderService, OrderController, AdminOrderController, mq: `OrderPaymentTimeoutProducer/Consumer`, job dự phòng `OrderExpirationScheduler`).

## 5. HỢP ĐỒNG API (RESTful, bọc `ApiResponse<T>`)

### 5.1. Giỏ hàng – `@PreAuthorize("isAuthenticated()")`
| API | Mô tả |
|---|---|
| `GET /api/v1/cart` | Xem giỏ |
| `POST /api/v1/cart/items` | Thêm: `{ "productId": 5, "quantity": 2 }` |
| `PUT /api/v1/cart/items/{productId}` | Đặt lại số lượng: `{ "quantity": 3 }` (0 không hợp lệ, dùng DELETE) |
| `DELETE /api/v1/cart/items/{productId}` | Xóa một dòng |
| `DELETE /api/v1/cart` | Xóa cả giỏ |

`GET /api/v1/cart` – 200:
```json
{ "code": 1000, "message": "Lấy giỏ hàng thành công",
  "data": {
    "items": [ { "productId": 5, "productName": "Bàn phím cơ", "imageUrl": "...",
                 "unitPrice": 499000.00, "quantity": 2, "lineTotal": 998000.00,
                 "availableStock": 12, "available": true } ],
    "totalItems": 1, "totalAmount": 998000.00 } }
```

### 5.2. Đặt hàng – khách
**`POST /api/v1/orders`** · Header `Idempotency-Key: <uuid>` (bắt buộc)
```json
{ "paymentMethod": "ONLINE", "productIds": [5, 7],
  "receiverName": "Nguyen Van A", "receiverPhone": "0901234567",
  "shippingAddress": "12 Le Loi, Q1, TP.HCM", "note": "Giao giờ hành chính" }
```
- `201 Created` (đơn mới) hoặc `200 OK` (trùng key, trả lại đơn cũ). `data` = `OrderResponse`:
```json
{ "orderId": 31, "orderCode": "MM20261125A1B2C3", "status": "PENDING_PAYMENT",
  "paymentMethod": "ONLINE", "totalAmount": 1297000.00,
  "paymentDeadline": "2026-11-25T17:30:00",
  "items": [ { "productId": 5, "productName": "Bàn phím cơ", "unitPrice": 499000.00,
               "quantity": 2, "lineTotal": 998000.00 } ],
  "receiverName": "Nguyen Van A", "receiverPhone": "0901234567",
  "shippingAddress": "12 Le Loi, Q1, TP.HCM", "createdAt": "2026-11-25T17:00:00" }
```
- `paymentDeadline` chỉ có với `PENDING_PAYMENT`.
- Lỗi thiếu hàng – `400 INSUFFICIENT_STOCK` kèm `data: { "productIds": [7] }`.

| API | Mô tả |
|---|---|
| `POST /api/v1/orders/{id}/pay` | Thanh toán giả lập ⇒ `CONFIRMED`, ghi `paid_at` |
| `POST /api/v1/orders/{id}/cancel` | Body `{ "reason": "..." }` tùy chọn; hủy + hoàn kho |
| `GET /api/v1/orders?page=0&size=10&status=` | Danh sách của tôi, mới nhất trước, `PageResponse<OrderSummaryResponse>` |
| `GET /api/v1/orders/{id}` | Chi tiết (đúng chủ) |

### 5.3. Admin – `@PreAuthorize("hasRole('ADMIN')")`
| API | Mô tả |
|---|---|
| `GET /api/v1/admin/orders?status=&page=&size=` | Danh sách mọi đơn |
| `PUT /api/v1/admin/orders/{id}/status` | Body `{ "status": "SHIPPING" }` – theo sơ đồ §3; `CANCELLED` ⇒ lý do `ADMIN` |

### 5.4. Mã lỗi mới (`ErrorCode`, dải 4xxx, thông báo tiếng Việt)
| Mã | Tên | HTTP |
|---|---|---|
| 4001 | CART_ITEM_LIMIT_EXCEEDED | 400 |
| 4002 | CART_EMPTY | 400 |
| 4003 | PRODUCT_NOT_AVAILABLE | 400 |
| 4004 | INSUFFICIENT_STOCK | 400 |
| 4005 | STANDARD_ORDER_NOT_FOUND | 404 |
| 4006 | INVALID_ORDER_STATUS_TRANSITION | 400 |
| 4007 | ORDER_CANNOT_CANCEL | 400 |
| 4008 | ORDER_ACCESS_DENIED_STD | 403 |
| 4009 | IDEMPOTENCY_KEY_REQUIRED | 400 |
| 4010 | CART_UNAVAILABLE | 503 |
Tái sử dụng: `ORDER_ALREADY_PAID` (3011), `ORDER_ALREADY_CANCELLED` (3010), `PRODUCT_NOT_FOUND` (3001), `INVALID_REQUEST` (1001).

## 6. LUỒNG CHÍNH – CHECKOUT (cho Dev)
1. Kiểm tra header `Idempotency-Key`; nếu đã có đơn cùng `(userId,key)` ⇒ trả đơn cũ (200).
2. Đọc giỏ Redis, lọc theo `productIds` nếu có; rỗng ⇒ `CART_EMPTY`.
3. Tải sản phẩm từ DB theo danh sách id, **sắp xếp theo `productId` tăng dần**; sản phẩm không `ACTIVE` ⇒ gom vào danh sách lỗi.
4. `@Transactional`: với từng sản phẩm chạy UPDATE trừ kho có điều kiện (ADR-2); 0 dòng ảnh hưởng ⇒ gom lỗi; nếu có lỗi ⇒ ném `AppException(INSUFFICIENT_STOCK)` (rollback toàn bộ).
5. Tạo `Order` + `OrderItem` (snapshot giá), sinh mã đơn.
6. `afterCommit`: xóa các dòng đã đặt khỏi giỏ; ghi khóa idempotency; nếu `ONLINE` ⇒ gửi message hẹn hủy (ADR-3).
7. Trả `OrderResponse`.

## 7. TIÊU CHÍ NGHIỆM THU & MA TRẬN KIỂM THỬ (cho Tester)
| # | Kịch bản | Kết quả mong đợi |
|---|---|---|
| T01 | Thêm sản phẩm còn hàng vào giỏ | 200, giỏ có dòng đúng số lượng |
| T02 | Thêm cùng sản phẩm lần 2 | Cộng dồn |
| T03 | Thêm vượt tồn / vượt 99 | 400 `INSUFFICIENT_STOCK` / `INVALID_REQUEST` |
| T04 | Thêm sản phẩm `INACTIVE` hoặc không tồn tại | 400 `PRODUCT_NOT_AVAILABLE` / 404 |
| T05 | Thêm dòng thứ 51 | 400 `CART_ITEM_LIMIT_EXCEEDED` |
| T06 | Xem giỏ sau khi Admin đổi giá | Hiển thị giá mới |
| T07 | Xem giỏ khi một sản phẩm hết hàng | `available=false`, không tính vào tổng |
| T08 | Chưa đăng nhập gọi giỏ/đơn | 401 |
| T09 | Checkout COD | 201, `CONFIRMED`, tồn giảm, giỏ còn dòng chưa chọn |
| T10 | Checkout ONLINE | 201, `PENDING_PAYMENT`, có `paymentDeadline` |
| T11 | Giỏ rỗng | 400 `CART_EMPTY` |
| T12 | Một trong hai sản phẩm thiếu hàng | 400 `INSUFFICIENT_STOCK`, **tồn của sản phẩm còn lại không đổi** |
| T13 | Thiếu `Idempotency-Key` | 400 `IDEMPOTENCY_KEY_REQUIRED` |
| T14 | Gửi cùng key 10 lần song song | Đúng 1 đơn, mọi phản hồi cùng `orderCode` |
| T15 | SĐT sai định dạng, thiếu địa chỉ | 400 + chi tiết validation |
| T16 | Thanh toán đơn ONLINE đang chờ | 200, `CONFIRMED`, có `paid_at` |
| T17 | Thanh toán 2 lần | 400 `ORDER_ALREADY_PAID` |
| T18 | Thanh toán đơn đã hủy | 400 `ORDER_ALREADY_CANCELLED` |
| T19 | Khách hủy đơn `CONFIRMED` | `CANCELLED`, tồn hoàn đúng; hủy lần 2 không hoàn thêm |
| T20 | Hủy đơn `SHIPPING` | 400 `ORDER_CANNOT_CANCEL` |
| T21 | Đơn ONLINE quá hạn (TTL rút ngắn khi test) | Tự `CANCELLED`, hoàn kho; nếu đã trả tiền thì bỏ qua |
| T22 | Khách A xem/hủy/trả tiền đơn của B | 403 `ORDER_ACCESS_DENIED_STD` |
| T23 | Admin chuyển `CONFIRMED→SHIPPING→DELIVERED` | 200 từng bước; nhảy cóc ⇒ 400 `INVALID_ORDER_STATUS_TRANSITION` |
| T24 | User thường gọi API admin | 403 |
| T25 | 200 luồng checkout song song, tồn = 50 | Đúng 50 đơn thành công, tồn = 0, không âm (NFR-3) |
| T26 | Hai luồng checkout cùng bộ sản phẩm thứ tự ngược | Không deadlock (NFR-5) |
| T27 | Tắt Redis khi thao tác giỏ | 503 `CART_UNAVAILABLE` |
| T28 | Tắt RabbitMQ khi checkout ONLINE | Đơn vẫn tạo; job dự phòng hủy khi quá hạn |
| T29 | Danh sách đơn của tôi có 20 đơn × 5 dòng | Số câu SQL không tăng theo số đơn (không N+1) |
| T30 | Hồi quy Flash Sale: thêm sản phẩm vào sự kiện đồng thời với checkout thường | Tồn không âm, không lost update |

---

## Kết quả triển khai & kiểm thử (Dev/Test)

**Trạng thái Gate 2:** đã chạy `mvnw test` trên Postgres 16 + Redis trống (không có RabbitMQ): 100 test, 99 pass. Migration Flyway V1–V3 chạy thành công trên DB trống. 1 test lỗi có sẵn từ trước, không do nhánh này: `FlashSaleEngineServiceTest.placeOrder_NotWarmedUp` (NPE `savedOrder` ở `FlashSaleEngineService.placeOrder`, file không bị sửa). Chưa kiểm tra trên DB có dữ liệu cũ (baseline).

**Test đã viết:** `CartServiceTest` (T01–T07, T27), `OrderServiceTest` (T09–T14, T16–T23).
Các case đồng thời (T15, T24–T26, T28–T30) cần kiểm thử tích hợp với DB/Redis thật, chưa có.

**Sai lệch so với SA/thiết kế:**
- Cache dùng `RedisCacheService` tự viết (khóa striped chống stampede, null-marker, fallback DB khi Redis lỗi) thay vì `@Cacheable`.
- Idempotency chỉ dựa vào UNIQUE(user_id, idempotency_key) trong DB, không dùng khóa Redis.
- `ddl-auto=none` (Flyway quản lý schema); nên đổi sang `validate` sau lần chạy thành công đầu tiên.
- FlashSaleService chuyển sang `reserveStock` nguyên tử (sửa race read-modify-write).
