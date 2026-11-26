# SRS & DETAIL DESIGN: ĐÁNH GIÁ & XẾP HẠNG SẢN PHẨM (REVIEWS & RATINGS)
> **Vai trò:** SA + BA – tài liệu cho Dev đọc và Test đối chiếu.
> **Mã:** SRS-REVIEW-001 · **Trạng thái:** ĐÃ DUYỆT GATE 1 (17:11 26/11/2026) · **Phụ thuộc:** module `order` (trạng thái `DELIVERED`), `product`, cache `RedisCacheService`.

---

## 1. LÝ DO CHỌN NGHIỆP VỤ
Sau Phase 7, hệ thống đã có danh mục, giỏ hàng và đơn hàng với vòng đời đầy đủ đến `DELIVERED`. Thiếu một bước khép vòng mua sắm: khách đã nhận hàng chưa thể phản hồi, và người mua mới không có tín hiệu tin cậy để quyết định. Đánh giá gắn với **đơn đã giao** (verified purchase) tận dụng trực tiếp dữ liệu đã có, ít rủi ro chạm vào luồng tiền/kho, và là nền cho sắp xếp theo độ phổ biến/đánh giá sau này.

## 2. MỤC TIÊU & PHẠM VI
**Trong phạm vi:** khách đã nhận hàng chấm điểm (mỗi dòng sản phẩm của một đơn đã giao được đánh giá riêng) 1–5 sao + viết nhận xét cho sản phẩm; xem, sửa, xóa đánh giá của mình; mọi người xem danh sách đánh giá và thống kê sao; Admin ẩn/hiện đánh giá vi phạm (không xóa); điểm trung bình hiển thị trên sản phẩm.
**Ngoài phạm vi:** ảnh/video trong đánh giá (đã chốt: không làm ở phiên bản này), trả lời của người bán, bình chọn "hữu ích", lọc từ ngữ cấm tự động, sắp xếp sản phẩm theo điểm (để sau).

**Tác nhân:** Khách/Public (đọc), Người dùng đã đăng nhập (`ROLE_USER`), Admin (`ROLE_ADMIN`).

## 3. USE CASE
| Mã | Use case | Tác nhân |
|---|---|---|
| UC-R01 | Gửi đánh giá cho sản phẩm đã nhận | User |
| UC-R02 | Sửa đánh giá của mình | User |
| UC-R03 | Xóa đánh giá của mình | User |
| UC-R04 | Xem danh sách đánh giá của một sản phẩm (lọc theo sao, phân trang) | Public |
| UC-R05 | Xem thống kê sao của sản phẩm (trung bình, phân bố 1–5) | Public |
| UC-R06 | Xem các đánh giá của tôi | User |
| UC-R07 | Ẩn / hiện đánh giá vi phạm | Admin |

## 4. QUY TẮC NGHIỆP VỤ
- BR-R1 **Chỉ người đã nhận hàng mới được đánh giá:** yêu cầu gửi kèm `orderId`; đơn đó phải thuộc người dùng, ở trạng thái `DELIVERED` và có chứa sản phẩm. Đơn không tồn tại, của người khác, chưa giao hoặc không chứa sản phẩm ⇒ cùng một lỗi `REVIEW_NOT_ELIGIBLE` (không tiết lộ đơn của người khác có tồn tại hay không).
- BR-R2 **Mỗi dòng sản phẩm của mỗi đơn có tối đa một đánh giá** (khóa `order_id` + `product_id`). Người dùng mua lại sản phẩm ở đơn khác thì được đánh giá lại ở đơn đó (ví dụ chất lượng đã cải thiện). Gửi lần hai cho cùng đơn+sản phẩm ⇒ `REVIEW_ALREADY_EXISTS`; muốn thay đổi thì sửa. Đảm bảo bằng ràng buộc UNIQUE ở DB.
- BR-R3 `rating` là số nguyên 1–5 (bắt buộc). `comment` tùy chọn, tối đa 1000 ký tự, trim khoảng trắng; chuỗi rỗng coi như không có. Không cho phép HTML: nội dung lưu nguyên dạng văn bản và phía hiển thị chịu trách nhiệm escape. `anonymous` (boolean, mặc định `true`): người đánh giá tự chọn ẩn danh hay hiện tên.
- BR-R4 Mọi đánh giá của cùng một sản phẩm, kể cả của cùng một người ở các đơn khác nhau, đều được tính vào điểm trung bình.
- BR-R5 Chỉ chủ đánh giá được **sửa** (số sao, nhận xét, `anonymous`), **không giới hạn thời gian**; chỉ chủ được **xóa** đánh giá của mình. Admin **không xóa**, chỉ ẩn/hiện (BR-R7). Người khác sửa/xóa ⇒ `REVIEW_ACCESS_DENIED`. Xóa là soft delete; sau khi xóa, người dùng được gửi lại đánh giá mới cho cùng đơn+sản phẩm.
- BR-R6 Sản phẩm `INACTIVE` hoặc không tồn tại: không gửi đánh giá mới (`PRODUCT_NOT_FOUND` / `PRODUCT_NOT_AVAILABLE`), nhưng đánh giá cũ vẫn xem được nếu sản phẩm còn tồn tại.
- BR-R7 Đánh giá bị Admin **ẩn** (`HIDDEN`) không xuất hiện trong danh sách công khai và **không tính** vào điểm trung bình/phân bố; chủ đánh giá vẫn thấy trong "đánh giá của tôi" kèm trạng thái. Người dùng sửa đánh giá đang bị ẩn không làm nó hiện lại.
- BR-R8 **Điểm trung bình** = tổng sao ÷ số đánh giá `VISIBLE`, làm tròn 1 chữ số thập phân (HALF_UP); chưa có đánh giá ⇒ `ratingAverage = 0`, `ratingCount = 0`.
- BR-R9 Nếu `anonymous = true` ⇒ `reviewerName = "Người dùng ẩn danh"`; nếu `false` ⇒ hiển thị họ tên (`fullName`) của người dùng, rỗng thì cũng là "Người dùng ẩn danh". Không bao giờ lộ email, số điện thoại, `userId`.
- BR-R10 Danh sách đánh giá: mặc định mới nhất trước; `sort` ∈ {`newest`, `ratingDesc`, `ratingAsc`}; lọc `rating` 1–5 tùy chọn; `size` mặc định 10, tối đa 50; thứ tự phụ luôn `id` giảm dần.
- BR-R11 Đánh giá có cờ `edited` = true khi đã được sửa ít nhất một lần.

## 5. THIẾT KẾ KIẾN TRÚC (SA)
**ADR-R1 – Điểm trung bình lưu dạng tổng hợp trên `products`.** Thêm `rating_count` và `rating_sum`; cập nhật bằng câu UPDATE nguyên tử trong cùng giao dịch với việc ghi đánh giá (`rating_count = rating_count + :dc`, `rating_sum = rating_sum + :ds`). Tránh `AVG()` trên bảng đánh giá mỗi lần xem sản phẩm và tránh lỗi mất cập nhật khi nhiều người đánh giá cùng lúc (không đọc-sửa-ghi trong Java). Trung bình tính khi trả response.
**ADR-R2 – Chống trùng bằng DB.** UNIQUE `(order_id, product_id)` kết hợp xóa mềm: dùng **partial unique index** `WHERE is_deleted = false` để người dùng gửi lại được sau khi xóa. Bắt `DataIntegrityViolationException` ⇒ `REVIEW_ALREADY_EXISTS` (xử lý cả trường hợp hai request đồng thời).
**ADR-R3 – Kiểm tra đã nhận hàng bằng truy vấn tồn tại** (`EXISTS` trên `orders` ⨝ `order_items`, lọc `orders.id = :orderId`, `user_id`, `status='DELIVERED'`, `product_id`), có index hỗ trợ. Đọc từ DB, không cache.
**ADR-R4 – Cache:** chi tiết sản phẩm (cache 10 phút) chứa `ratingAverage/ratingCount` nên phải `evictAfterCommit` khi đánh giá thay đổi. Danh sách sản phẩm (cache 60 s) chấp nhận cũ tối đa 60 s. Danh sách đánh giá và thống kê sao **không cache** ở phiên bản này (đọc DB có index).
**ADR-R5 – Ẩn/hiện và xóa phải cập nhật tổng hợp:** đổi trạng thái hoặc xóa đánh giá đang `VISIBLE` trừ khỏi `rating_sum/rating_count`; hiện lại thì cộng lại. Mọi thay đổi trên đánh giá và tổng hợp nằm trong **một giao dịch**.

## 6. THIẾT KẾ DỮ LIỆU (Flyway `V4__reviews.sql`)
- **`product_reviews`**: `id` BIGSERIAL PK · `product_id` BIGINT NOT NULL FK products · `user_id` BIGINT NOT NULL FK users · `order_id` BIGINT NOT NULL FK orders · `anonymous` BOOLEAN NOT NULL DEFAULT TRUE · `rating` SMALLINT NOT NULL CHECK (1–5) · `comment` VARCHAR(1000) NULL · `status` VARCHAR(20) NOT NULL DEFAULT 'VISIBLE' (`VISIBLE`/`HIDDEN`) · `edited` BOOLEAN NOT NULL DEFAULT FALSE · `is_deleted` BOOLEAN NOT NULL DEFAULT FALSE · `created_at`, `updated_at`.
- **Index:**
  - `uq_review_order_product` – UNIQUE `(order_id, product_id)` WHERE `is_deleted = false`
  - `idx_review_product_status_created` – `(product_id, status, created_at DESC)` WHERE `is_deleted = false`
  - `idx_review_product_rating` – `(product_id, rating)` WHERE `is_deleted = false AND status = 'VISIBLE'`
  - `idx_review_user` – `(user_id, created_at DESC)`
  - hỗ trợ kiểm tra đã nhận hàng: `idx_order_items_product_order` – `(product_id, order_id)` (nếu chưa có).
- **`products`** (sửa): thêm `rating_count` INT NOT NULL DEFAULT 0, `rating_sum` INT NOT NULL DEFAULT 0 (dữ liệu cũ mặc định 0, không cần backfill vì chưa có đánh giá).

**Gói/lớp dự kiến:** `modules/review` – `entity/{ProductReview, ReviewStatus}`, `repository/ProductReviewRepository`, `dto/{CreateReviewRequest, UpdateReviewRequest, ReviewResponse, ReviewSummaryResponse, UpdateReviewVisibilityRequest}`, `service/ReviewService`, `controller/{ReviewController, AdminReviewController}`; sửa `ProductRepository` (câu UPDATE tổng hợp), `ProductResponse` (+`ratingAverage`, `ratingCount`), `OrderRepository` (truy vấn đã nhận hàng), `ErrorCode`, `SecurityConfig` (GET công khai).

## 7. HỢP ĐỒNG API
Mọi response theo `ApiResponse`; danh sách theo `PageResponse`.

| Method | Đường dẫn | Quyền | Mô tả |
|---|---|---|---|
| POST | `/api/v1/products/{productId}/reviews` | USER | Gửi đánh giá, trả `201` |
| GET | `/api/v1/products/{productId}/reviews?rating=&sort=&page=&size=` | Public | Danh sách đánh giá `VISIBLE` |
| GET | `/api/v1/products/{productId}/reviews/summary` | Public | Thống kê sao |
| GET | `/api/v1/reviews/me?page=&size=` | USER | Đánh giá của tôi (cả `HIDDEN`) |
| PUT | `/api/v1/reviews/{id}` | USER (chủ) | Sửa số sao/nhận xét/ẩn danh |
| DELETE | `/api/v1/reviews/{id}` | USER (chủ) | Xóa mềm |
| PUT | `/api/v1/admin/reviews/{id}/visibility` | ADMIN | Body `{ "visible": false }` |
| GET | `/api/v1/admin/reviews?status=&page=&size=` | ADMIN | Danh sách để kiểm duyệt |

**Request gửi:** `{ "orderId": 31, "rating": 5, "comment": "Giao nhanh, đóng gói kỹ", "anonymous": false }`
**Request sửa:** `{ "rating": 4, "comment": "...", "anonymous": true }` (các trường tùy chọn, ít nhất một trường).
**ReviewResponse:** `{ "id", "productId", "rating", "comment", "reviewerName", "anonymous", "status", "edited", "createdAt", "updatedAt" }` (không có `userId`, `orderId` ở API công khai; riêng `/reviews/me` có thêm `orderId`).
**Summary:** `{ "ratingAverage": 4.3, "ratingCount": 12, "distribution": { "1": 0, "2": 1, "3": 1, "4": 3, "5": 7 } }`
**ProductResponse** bổ sung `ratingAverage`, `ratingCount`.

### Mã lỗi mới (dải 6xxx)
| Mã | Tên | HTTP | Khi nào |
|---|---|---|---|
| 6001 | REVIEW_NOT_FOUND | 404 | Không có đánh giá (hoặc đã xóa) |
| 6002 | REVIEW_ALREADY_EXISTS | 409 | Đã đánh giá sản phẩm này trong đơn này |
| 6003 | REVIEW_NOT_ELIGIBLE | 403 | Đơn không hợp lệ để đánh giá (không phải của mình, chưa `DELIVERED`, không chứa sản phẩm) |
| 6004 | REVIEW_ACCESS_DENIED | 403 | Không phải chủ đánh giá |
Dùng lại: `PRODUCT_NOT_FOUND`, `PRODUCT_NOT_AVAILABLE`, `INVALID_REQUEST` (rating/comment/sort/size sai).

## 8. LUỒNG XỬ LÝ (cho Dev)
**Gửi đánh giá** (một `TransactionTemplate`/`@Transactional`): kiểm tra sản phẩm tồn tại + `ACTIVE` → kiểm tra đơn `orderId` hợp lệ (BR-R1) → chèn đánh giá (bắt vi phạm UNIQUE ⇒ 6002) → `UPDATE products SET rating_count = rating_count+1, rating_sum = rating_sum+:r` → `evictAfterCommit` cache chi tiết sản phẩm.
**Sửa:** tải đánh giá (chưa xóa) → kiểm tra chủ → đặt `edited=true` → nếu đang `VISIBLE` thì `rating_sum += (mới − cũ)` → evict.
**Xóa:** kiểm tra chủ → soft delete → nếu đang `VISIBLE` thì `count−1`, `sum−rating` → evict.
**Ẩn/hiện:** đổi trạng thái; ẩn: trừ khỏi tổng hợp, hiện: cộng lại; đổi sang đúng trạng thái hiện tại thì không làm gì (không trừ/cộng hai lần).
**Tên hiển thị:** theo BR-R9.

## 9. TIÊU CHÍ NGHIỆM THU & MA TRẬN KIỂM THỬ (cho Tester)
| ID | Kịch bản | Kết quả mong đợi |
|---|---|---|
| R01 | User có đơn DELIVERED chứa SP, gửi 5 sao + nhận xét | 201; `rating_count`+1, `rating_sum`+5; cache chi tiết bị xóa |
| R02 | Gửi không có nhận xét | 201, `comment` null |
| R03 | `orderId` của đơn không phải của mình, không tồn tại, hoặc không chứa SP | 403, 6003 |
| R04 | Đơn của mình chứa SP nhưng ở trạng thái PENDING_PAYMENT/CONFIRMED/SHIPPING/CANCELLED | 403, 6003 |
| R05 | Gửi lần 2 cho cùng đơn + SP | 409, 6002 |
| R05b | Cùng SP mua ở đơn khác (cũng DELIVERED) và gửi đánh giá | 201; tổng hợp cộng thêm |
| R06 | Hai request gửi đồng thời cùng đơn+SP | Một thành công, một 409; tổng hợp chỉ +1 lần |
| R07 | `rating` = 0, 6, null, thiếu | 400 |
| R08 | `comment` dài 1001 ký tự | 400; đúng 1000 ký tự thì chấp nhận |
| R09 | SP không tồn tại / INACTIVE | 404 / lỗi PRODUCT_NOT_AVAILABLE |
| R10 | Chưa đăng nhập gửi/sửa/xóa | 401 |
| R11 | Sửa đánh giá của mình từ 5 → 3 | 200; `edited=true`; `rating_sum` giảm 2, count giữ nguyên |
| R12 | Sửa đánh giá của người khác | 403, 6004 |
| R13 | Xóa đánh giá của mình | 200; count−1, sum−rating; không còn trong danh sách |
| R14 | Admin gọi DELETE đánh giá của người khác | 403, 6004 (Admin chỉ được ẩn) |
| R15 | User khác xóa đánh giá | 403, 6004 |
| R16 | Xóa xong, gửi lại đánh giá mới | 201 |
| R17 | Xóa/sửa đánh giá không tồn tại hoặc đã xóa | 404, 6001 |
| R18 | Admin ẩn đánh giá | Biến mất khỏi danh sách công khai; trừ khỏi trung bình; chủ vẫn thấy ở `/reviews/me` với `HIDDEN` |
| R19 | Admin ẩn đánh giá đã ẩn (lặp lại) | Không trừ hai lần |
| R20 | Admin hiện lại đánh giá | Cộng lại vào tổng hợp |
| R21 | Sửa đánh giá đang bị ẩn | Vẫn `HIDDEN`; tổng hợp không đổi |
| R22 | Danh sách: lọc `rating=4`, `sort=ratingDesc`, phân trang | Đúng lọc, đúng thứ tự, đúng tổng |
| R23 | `size=1000`, `sort=abc`, `rating=7` | cắt về 50 / 400 / 400 |
| R24 | Summary: có 12 đánh giá | Trung bình đúng, làm tròn HALF_UP 1 chữ số, phân bố cộng đủ 12 |
| R25 | SP chưa có đánh giá | `ratingAverage=0`, `ratingCount=0`, phân bố toàn 0 |
| R26a | `anonymous=true` | Tên hiển thị "Người dùng ẩn danh" |
| R26b | `anonymous=false` | Hiển thị họ tên; sau khi sửa sang `true` thì ẩn tên |
| R26 | Response công khai | Không chứa email, `userId`, `orderId` |
| R27 | Chi tiết sản phẩm sau khi có đánh giá mới | Hiển thị ngay điểm mới (cache đã bị xóa) |
| R28 | Admin liệt kê để kiểm duyệt, lọc theo `status` | Đúng; User thường nhận 403 |

## 10. QUYẾT ĐỊNH GATE 1 (đã chốt)
1. Một đánh giá cho mỗi dòng sản phẩm của mỗi đơn hàng (lý do: sản phẩm có thể được cải thiện chất lượng giữa các lần mua).
2. Người đánh giá tự chọn hiện tên hoặc ẩn danh (mặc định ẩn danh nếu không gửi trường `anonymous`).
3. Sửa đánh giá không giới hạn thời gian.
4. Admin chỉ ẩn/hiện, không xóa.
5. Ảnh trong đánh giá: không làm ở phiên bản này.

---

## 11. KẾT QUẢ TRIỂN KHAI & KIỂM THỬ (Dev/Test)

**Gate 2:** đã chạy `mvnw test` trên Postgres 16 + Redis thật (không có RabbitMQ): 132 test, 131 pass. Lỗi duy nhất là `FlashSaleEngineServiceTest.placeOrder_NotWarmedUp` (NPE trong `FlashSaleEngineService`, có từ trước, không thuộc nhánh này). Migration V4 chạy thành công trên DB đã có V1–V3.

**Test đã viết:**
- `RatingCalculatorTest`, `ReviewServiceTest` (Mockito, 29 test): R01–R05, R06 (ánh xạ lỗi UNIQUE), R08, R09, R11–R15, R17–R26.
- `ReviewIntegrationTest` (MockMvc + DB/Redis thật, 3 test): luồng đầy đủ gửi → trùng → quyền → sửa → ẩn/hiện → xóa → gửi lại; kiểm tra tổng hợp và cache chi tiết sản phẩm được cập nhật ngay; danh sách/validation (R07, R23); 4 request đồng thời cùng đơn+sản phẩm cho đúng 1 thành công (R06).

**Chưa kiểm thử:** R10 chỉ kiểm tra 401 cho thao tác gửi (chưa cho sửa/xóa qua HTTP); R22 (thứ tự sắp xếp thực tế theo `ratingDesc`/`ratingAsc` với nhiều đánh giá); R28 mới kiểm tra User bị 403 ở danh sách kiểm duyệt (chưa kiểm tra lọc `status` của Admin); chưa thử trên DB có dữ liệu cũ của môi trường dev.

**Chi tiết triển khai bổ sung so với thiết kế:**
- Cột `rating_count`/`rating_sum` trong entity `Product` được ánh xạ chỉ đọc (`insertable=false, updatable=false`) để việc sửa thông tin sản phẩm không ghi đè tổng hợp bằng giá trị cũ; chỉ cập nhật bằng câu UPDATE nguyên tử.
- Các thao tác sửa/xóa/ẩn hiện tải đánh giá bằng khóa dòng (`PESSIMISTIC_WRITE`) để tính đúng tổng hợp khi thao tác đồng thời.
- Nhận xét dài quá 1000 ký tự bị chặn hai lớp: Bean Validation ở controller và kiểm tra lại trong service.
