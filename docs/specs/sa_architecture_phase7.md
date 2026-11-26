# TÀI LIỆU KIẾN TRÚC (SA): PHASE 7 – GIỎ HÀNG, ĐẶT HÀNG THƯỜNG & DANH MỤC/TÌM KIẾM
> **Vai trò:** SA (Solution Architect) – khung kiến trúc, công nghệ, pattern, tối ưu hiệu năng.
> **Trạng thái:** CHỜ GATE 1 (chưa có dòng code Java nào được sinh).
> **Liên quan:** `cart_checkout_spec.md` (BA), `catalog_search_spec.md` (BA).

---

## 1. HIỆN TRẠNG & KHOẢNG TRỐNG
| # | Quan sát trên code hiện tại | Hệ quả |
|---|---|---|
| 1 | Chỉ có `FlashSaleOrder`; sản phẩm thường không có luồng mua | Sàn không bán được hàng ngoài Flash Sale |
| 2 | `Product` chưa có danh mục; tìm kiếm là `findByNameContainingIgnoreCaseAndStatus` (`LIKE '%kw%'`) | Seq-scan, chậm khi >50k sản phẩm |
| 3 | Chưa bật Spring Cache; mọi GET đều chạm Postgres | Tải đọc cao đẩy hết vào DB |
| 4 | `ddl-auto: update` | Không tạo được index GIN/extension, không kiểm soát migration |
| 5 | `Product.totalStock` được sửa bằng read-modify-write (ring-fencing Flash Sale) và không có `@Version` | Nếu thêm checkout thường sẽ có race / lost update (xem ADR-2) |
| 6 | Vai trò `ROLE_USER`, `ROLE_ADMIN`; spec cũ ghi `CUSTOMER/VENDOR` | Chuẩn hóa theo code: dùng `ROLE_USER`, `ROLE_ADMIN` |

## 2. NGUYÊN TẮC THIẾT KẾ (tuân `PROJECT_RULES.md`)
- Giữ kiến trúc 5 tầng, module mới: `modules/cart`, `modules/order`, `modules/category`; `product` mở rộng.
- Không rò rỉ Entity ra Controller; `ApiResponse<T>` / `PageResponse<T>`; lỗi qua `AppException(ErrorCode)`.
- **Đường tiền (giá, tồn kho, tổng tiền) luôn đọc từ PostgreSQL, KHÔNG đọc từ cache.** Cache chỉ phục vụ hiển thị.
- Mọi phụ thuộc hạ tầng phụ (Redis/RabbitMQ) phải có hành vi khi sập, nhất quán với cách Flash Sale đang làm (fallback hoặc lỗi 503 rõ ràng).

## 3. CÁC QUYẾT ĐỊNH KIẾN TRÚC (ADR)

### ADR-1: Giỏ hàng lưu trên Redis Hash
- Key `cart:{userId}`, field `{productId}`, value `{quantity}`; TTL trượt 7 ngày (gia hạn mỗi lần ghi).
- **Không lưu giá** trong giỏ: giá/tên/tồn lấy từ DB lúc xem giỏ và lúc checkout → tránh giá cũ.
- Vì sao Redis: ghi nhiều, dữ liệu tạm, không cần ràng buộc quan hệ; thao tác `HINCRBY/HSET` O(1), mục tiêu < 20 ms.
- Khi Redis sập: trả `503 CART_UNAVAILABLE` (giỏ không thể fallback DB mà không thêm bảng; chấp nhận vì là dữ liệu tạm). **Cần bạn xác nhận (Q2).**
- Giới hạn: tối đa 50 dòng/giỏ, 1–99 mỗi dòng (chặn phình bộ nhớ Redis).

### ADR-2: Trừ tồn kho đặt hàng thường bằng UPDATE có điều kiện nguyên tử
```sql
UPDATE products SET total_stock = total_stock - :qty
WHERE id = :id AND status = 'ACTIVE' AND total_stock >= :qty
```
- `rows affected = 0` ⇒ hết hàng ⇒ rollback toàn bộ giao dịch.
- Ưu điểm so với `@Version`: không retry storm khi nhiều người cùng mua một sản phẩm, không cần đọc trước.
- **Chống deadlock:** khi checkout nhiều sản phẩm, luôn sắp xếp theo `productId` tăng dần rồi mới UPDATE.
- **Việc kèm theo (dev phải xử lý):** phần ring-fencing của `FlashSaleService` (trừ `totalStock` để cấp cho Flash Sale) phải chuyển sang cùng câu UPDATE có điều kiện này, nếu không sẽ race với checkout thường. Dev cần đọc `FlashSaleService.addProductToEvent` để xác nhận trước khi sửa.
- Hoàn kho khi hủy: `UPDATE ... SET total_stock = total_stock + :qty` (cũng nguyên tử).

### ADR-3: Checkout đồng bộ trong 1 giao dịch DB, hủy quá hạn bất đồng bộ qua RabbitMQ
- Lưu lượng đơn thường thấp hơn Flash Sale ⇒ **không** cần hàng đợi cho bước tạo đơn; một `@Transactional` (trừ kho + ghi `orders` + `order_items`) là đủ và dễ bảo đảm nhất quán.
- Đơn `ONLINE` ở `PENDING_PAYMENT` được hẹn hủy: **tái sử dụng pattern TTL + DLX** đã có (`flashsale.order.delay.queue`), thêm bộ queue riêng `order.payment.delay.queue → order.payment.cancel.queue`, TTL = `order.payment-timeout-ms` (mặc định 30 phút).
- Consumer hủy phải **idempotent**: chỉ hủy khi trạng thái vẫn `PENDING_PAYMENT`; đã `CONFIRMED`/`CANCELLED` thì bỏ qua.
- Gửi message hẹn giờ **sau khi commit** (`TransactionSynchronization.afterCommit`) để không có tin nhắn "ma" cho đơn chưa lưu. Nếu RabbitMQ sập: đơn vẫn tạo; một job `@Scheduled` quét đơn `PENDING_PAYMENT` quá hạn mỗi 1 phút làm cơ chế dự phòng (nhất quán với fallback async worker vừa thêm).

### ADR-4: Chống đặt trùng (Idempotency)
- Header bắt buộc `Idempotency-Key` (UUID) cho `POST /orders`.
- Redis `SET idem:order:{userId}:{key} {orderCode} NX EX 86400`. Trùng key ⇒ trả lại đơn đã tạo (HTTP 200) thay vì tạo mới.
- Ràng buộc chốt ở DB: unique `(user_id, idempotency_key)` – đây mới là chốt chặn cuối khi Redis sập.

### ADR-5: Snapshot dữ liệu đơn hàng
- `order_items` lưu `product_name`, `unit_price` tại thời điểm mua; đổi giá/tên sản phẩm sau đó **không** ảnh hưởng đơn cũ.
- Đơn hàng soft delete (`is_deleted`) + `@Version` theo quy tắc entity.

### ADR-6: Tìm kiếm sản phẩm – index đúng chỗ, chưa cần Elasticsearch
- Postgres extension `pg_trgm` + `GIN (lower(name) gin_trgm_ops)` cho `ILIKE '%kw%'` – đủ tới ~1 triệu sản phẩm; chỉ cân nhắc Elasticsearch khi cần xếp hạng liên quan/ngữ nghĩa (ghi nhận làm phase sau).
- Index B-tree tổng hợp `(category_id, status, original_price)` cho lọc danh mục + khoảng giá + sắp xếp giá.
- Truy vấn động bằng **JPA Specification** (keyword, categoryId, minPrice, maxPrice, inStock); trả về trang bằng `Pageable`.
- **Whitelist sort** (`createdAt`, `originalPrice`, `name`) và **giới hạn `size ≤ 50`** để chặn sort theo cột tùy ý và quét trang khổng lồ.
- Danh mục: cây 2 cấp (`parent_id`), truy vấn "sản phẩm của danh mục cha" lấy luôn id con.

### ADR-7: Cache-aside bằng Spring Cache + Redis
| Cache | Key | TTL | Xóa khi |
|---|---|---|---|
| `product:detail` | `{id}` | 10 phút | Admin sửa/xóa sản phẩm (`@CacheEvict`) |
| `product:list` | hash(bộ lọc+trang+sort) | 60 giây | Hết TTL (không xóa chính xác – chấp nhận hơi cũ) |
| `category:tree` | `all` | 1 giờ | Admin thêm/sửa/xóa danh mục |

- `sync = true` chống cache stampede; cache giá trị rỗng 60 s cho id không tồn tại (chống cache penetration).
- Serialize JSON (`GenericJackson2JsonRedisSerializer`) và **chỉ cache DTO**, không cache Entity.
- `CacheErrorHandler` tùy biến: lỗi Redis ⇒ log cảnh báo và đọc DB (không làm hỏng API đọc).
- Lưu ý: `stock` trong cache chỉ mang tính hiển thị; checkout luôn kiểm tra DB (nguyên tắc §2).

### ADR-8: Migration có kiểm soát bằng Flyway
- Thêm Flyway; `ddl-auto` chuyển `validate` cho môi trường có Flyway. Script: `V1__baseline.sql` (schema hiện tại), `V2__catalog.sql`, `V3__orders.sql`.
- Lý do: `ddl-auto=update` không tạo được extension/index GIN và không an toàn khi đổi schema. **Cần bạn xác nhận (Q3)** vì ảnh hưởng quy trình chạy local (DB cũ cần `baseline-on-migrate`).

## 4. MỤC TIÊU PHI CHỨC NĂNG (NFR) – Tester dùng để nghiệm thu
| Mã | Chỉ tiêu | Điều kiện |
|---|---|---|
| NFR-1 | Thêm vào giỏ p95 < 20 ms | Redis local |
| NFR-2 | Tìm kiếm p95 < 100 ms (cache miss), < 15 ms (cache hit) | 100.000 sản phẩm seed |
| NFR-3 | **Không bán vượt tồn** | 200 luồng checkout song song, tồn = 50 ⇒ đúng 50 đơn thành công |
| NFR-4 | Checkout p95 < 300 ms | giỏ 10 dòng |
| NFR-5 | Không deadlock | 2 luồng checkout cùng bộ sản phẩm theo thứ tự ngược nhau |
| NFR-6 | Redis sập: API đọc sản phẩm vẫn 200 | tắt Redis |
| NFR-7 | Không đơn trùng | gửi cùng `Idempotency-Key` 10 lần song song ⇒ 1 đơn |

## 5. VIỆC NẶNG ẢNH HƯỞNG HIỆU NĂNG & CÁCH XỬ LÝ
| Việc | Rủi ro | Xử lý |
|---|---|---|
| Hủy đơn quá hạn | Quét DB định kỳ nặng | Dùng TTL/DLX của RabbitMQ; job quét chỉ là dự phòng, có index `(status, created_at)` |
| Danh sách đơn của tôi | N+1 `orders → items` | `@EntityGraph`/`JOIN FETCH`, phân trang trên `orders`, tải items bằng `IN` theo batch |
| Tìm kiếm LIKE | Seq-scan | pg_trgm GIN + cache 60 s |
| Cây danh mục | Gọi nhiều lần | Cache 1 giờ, 1 truy vấn nạp toàn bộ |
| Logging | Log thân request lớn | Giữ `@TrackExecutionTime` chỉ trên service mới, không log payload giỏ hàng |

## 6. KẾ HOẠCH THỰC THI (sau khi qua Gate 1)
1. **Nền tảng:** Flyway + Spring Cache + `CacheErrorHandler` + ErrorCode mới (Dev).
2. **Feature B – Danh mục & Tìm kiếm** (nhỏ hơn, dựng hạ tầng cache) (Dev → Test).
3. **Feature A – Giỏ hàng & Đặt hàng** (Dev → Test), gồm sửa ring-fencing của FlashSale theo ADR-2.
4. Hồi quy toàn bộ `./mvnw test` + kiểm thử tải NFR-2/3/7 (Test).
5. Cập nhật `frontend_integration_spec.md` (lệch phiên bản: ghi Spring Boot 3, Vue CDN) và README.

## 7. CÂU HỎI CẦN CHỐT Ở GATE 1
- **Q1:** Hai nghiệp vụ đề xuất (Giỏ hàng & Đặt hàng; Danh mục & Tìm kiếm) – duyệt hay đổi?
- **Q2:** Redis sập thì giỏ hàng trả lỗi 503 (đề xuất) hay thêm bảng DB để dự phòng?
- **Q3:** Đồng ý đưa Flyway vào dự án không?
