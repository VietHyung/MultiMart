# SRS & DETAIL DESIGN: ĐÁNH GIÁ & XẾP HẠNG SẢN PHẨM (REVIEWS & RATINGS)
> **Vai trò:** SA + BA – tài liệu cho Dev đọc và Test đối chiếu.
> **Mã:** SRS-REVIEW-001 · **Trạng thái:** CHỜ GATE 1 (chưa viết code Java) · **Phụ thuộc:** module `order` (trạng thái `DELIVERED`), `product`, cache `RedisCacheService`.

---

## 1. LÝ DO CHỌN NGHIỆP VỤ
Sau Phase 7, hệ thống đã có danh mục, giỏ hàng và đơn hàng với vòng đời đầy đủ đến `DELIVERED`. Thiếu một bước khép vòng mua sắm: khách đã nhận hàng chưa thể phản hồi, và người mua mới không có tín hiệu tin cậy để quyết định. Đánh giá gắn với **đơn đã giao** (verified purchase) tận dụng trực tiếp dữ liệu đã có, ít rủi ro chạm vào luồng tiền/kho, và là nền cho sắp xếp theo độ phổ biến/đánh giá sau này.

## 2. MỤC TIÊU & PHẠM VI
**Trong phạm vi:** khách đã nhận hàng chấm điểm 1–5 sao + viết nhận xét cho sản phẩm; xem, sửa, xóa đánh giá của mình; mọi người xem danh sách đánh giá và thống kê sao; Admin ẩn/hiện đánh giá vi phạm; điểm trung bình hiển thị trên sản phẩm.
**Ngoài phạm vi:** ảnh/video trong đánh giá, trả lời của người bán, bình chọn "hữu ích", lọc từ ngữ cấm tự động, sắp xếp sản phẩm theo điểm (để sau).

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
| UC-R07 | Ẩn / hiện đánh giá, xóa đánh giá vi phạm | Admin |

## 4. QUY TẮC NGHIỆP VỤ
- BR-R1 **Chỉ người đã nhận hàng mới được đánh giá:** người dùng phải có ít nhất một đơn của chính mình ở trạng thái `DELIVERED` chứa sản phẩm đó. Ngược lại ⇒ `REVIEW_NOT_ELIGIBLE`.
- BR-R2 **Mỗi người một đánh giá cho mỗi sản phẩm** (kể cả khi mua nhiều lần). Gửi lần hai ⇒ `REVIEW_ALREADY_EXISTS`; muốn thay đổi thì sửa. Đảm bảo bằng ràng buộc UNIQUE ở DB, không chỉ kiểm tra trong code.
- BR-R3 `rating` là số nguyên 1–5 (bắt buộc). `comment` tùy chọn, tối đa 1000 ký tự, trim khoảng trắng; chuỗi rỗng coi như không có. Không cho phép HTML: nội dung được lưu nguyên dạng văn bản và phía hiển thị chịu trách nhiệm escape.
- BR-R4 Đánh giá lưu kèm `orderId` của một đơn `DELIVERED` hợp lệ (đơn giao gần nhất chứa sản phẩm) để truy vết.
- BR-R5 Chỉ chủ đánh giá được **sửa**; chỉ chủ hoặc Admin được **xóa**. Người khác ⇒ `REVIEW_ACCESS_DENIED`. Xóa là soft delete; sau khi xóa, người dùng được phép gửi lại đánh giá mới.
- BR-R6 Sản phẩm `INACTIVE` hoặc không tồn tại: không gửi đánh giá mới (`PRODUCT_NOT_FOUND` / `PRODUCT_NOT_AVAILABLE`), nhưng đánh giá cũ vẫn xem được nếu sản phẩm còn tồn tại.
- BR-R7 Đánh giá bị Admin **ẩn** (`HIDDEN`) không xuất hiện trong danh sách công khai và **không tính** vào điểm trung bình/phân bố; chủ đánh giá vẫn thấy trong "đánh giá của tôi" kèm trạng thái. Người dùng sửa đánh giá đang bị ẩn không làm nó hiện lại.
- BR-R8 **Điểm trung bình** = tổng sao ÷ số đánh giá `VISIBLE`, làm tròn 1 chữ số thập phân (HALF_UP); chưa có đánh giá ⇒ `ratingAverage = 0`, `ratingCount = 0`.
- BR-R9 Tên người đánh giá hiển thị công khai ở dạng **che bớt** (ví dụ `Nguyễn V***`), không lộ email hay số điện thoại.
- BR-R10 Danh sách đánh giá: mặc định mới nhất trước; `sort` ∈ {`newest`, `ratingDesc`, `ratingAsc`}; lọc `rating` 1–5 tùy chọn; `size` mặc định 10, tối đa 50; thứ tự phụ luôn `id` giảm dần.
- BR-R11 Đánh giá có cờ `edited` = true khi đã được sửa ít nhất một lần.

## 5. THIẾT KẾ KIẾN TRÚC (SA)
**ADR-R1 – Điểm trung bình lưu dạng tổng hợp trên `products`.** Thêm `rating_count` và `rating_sum`; cập nhật bằng câu UPDATE nguyên tử trong cùng giao dịch với việc ghi đánh giá (`rating_count = rating_count + :dc`, `rating_sum = rating_sum + :ds`). Tránh `AVG()` trên bảng đánh giá mỗi lần xem sản phẩm và tránh lỗi mất cập nhật khi nhiều người đánh giá cùng lúc (không đọc-sửa-ghi trong Java). Trung bình tính khi trả response.
**ADR-R2 – Chống trùng bằng DB.** UNIQUE `(user_id, product_id)` kết hợp xóa mềm: dùng **partial unique index** `WHERE is_deleted = false` để người dùng gửi lại được sau khi xóa. Bắt `DataIntegrityViolationException` ⇒ `REVIEW_ALREADY_EXISTS` (xử lý cả trường hợp hai request đồng thời).
**ADR-R3 – Kiểm tra đã nhận hàng bằng truy vấn tồn tại** (`EXISTS` trên `orders` ⨝ `order_items`, lọc `user_id`, `status='DELIVERED'`, `product_id`), có index hỗ trợ. Đọc từ DB, không cache.
**ADR-R4 – Cache:** chi tiết sản phẩm (cache 10 phút) chứa `ratingAverage/ratingCount` nên phải `evictAfterCommit` khi đánh giá thay đổi. Danh sách sản phẩm (cache 60 s) chấp nhận cũ tối đa 60 s. Danh sách đánh giá và thống kê sao **không cache** ở phiên bản này (đọc DB có index).
**ADR-R5 – Ẩn/hiện và xóa phải cập nhật tổng hợp:** đổi trạng thái hoặc xóa đánh giá đang `VISIBLE` trừ khỏi `rating_sum/rating_count`; hiện lại thì cộng lại. Mọi thay đổi trên đánh giá và tổng hợp nằm trong **một giao dịch**.

## 6. THIẾT KẾ DỮ LIỆU (Flyway `V4__reviews.sql`)
- **`product_reviews`**: `id` BIGSERIAL PK · `product_id` BIGINT NOT NULL FK products · `user_id` BIGINT NOT NULL FK users · `order_id` BIGINT NOT NULL FK orders · `rating` SMALLINT NOT NULL CHECK (1–5) · `comment` VARCHAR(1000) NULL · `status` VARCHAR(20) NOT NULL DEFAULT 'VISIBLE' (`VISIBLE`/`HIDDEN`) · `edited` BOOLEAN NOT NULL DEFAULT FALSE · `is_deleted` BOOLEAN NOT NULL DEFAULT FALSE · `created_at`, `updated_at`.
- **Index:**
  - `uq_review_user_product` – UNIQUE `(user_id, product_id)` WHERE `is_deleted = false`
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
| PUT | `/api/v1/reviews/{id}` | USER (chủ) | Sửa số sao/nhận xét |
| DELETE | `/api/v1/reviews/{id}` | USER (chủ) hoặc ADMIN | Xóa mềm |
| PUT | `/api/v1/admin/reviews/{id}/visibility` | ADMIN | Body `{ "visible": false }` |
| GET | `/api/v1/admin/reviews?status=&page=&size=` | ADMIN | Danh sách để kiểm duyệt |

**Request gửi/sửa:** `{ "rating": 5, "comment": "Giao nhanh, đóng gói kỹ" }`
**ReviewResponse:** `{ "id", "productId", "rating", "comment", "reviewerName": "Nguyễn V***", "status", "edited", "createdAt", "updatedAt" }` (không có `userId`, `orderId` ở API công khai).
**Summary:** `{ "ratingAverage": 4.3, "ratingCount": 12, "distribution": { "1": 0, "2": 1, "3": 1, "4": 3, "5": 7 } }`
**ProductResponse** bổ sung `ratingAverage`, `ratingCount`.

### Mã lỗi mới (dải 6xxx)
| Mã | Tên | HTTP | Khi nào |
|---|---|---|---|
| 6001 | REVIEW_NOT_FOUND | 404 | Không có đánh giá (hoặc đã xóa) |
| 6002 | REVIEW_ALREADY_EXISTS | 409 | Đã đánh giá sản phẩm này |
| 6003 | REVIEW_NOT_ELIGIBLE | 403 | Chưa có đơn `DELIVERED` chứa sản phẩm |
| 6004 | REVIEW_ACCESS_DENIED | 403 | Không phải chủ đánh giá |
Dùng lại: `PRODUCT_NOT_FOUND`, `PRODUCT_NOT_AVAILABLE`, `INVALID_REQUEST` (rating/comment/sort/size sai).

## 8. LUỒNG XỬ LÝ (cho Dev)
**Gửi đánh giá** (một `TransactionTemplate`/`@Transactional`): kiểm tra sản phẩm tồn tại + `ACTIVE` → kiểm tra đã nhận hàng, lấy `orderId` → chèn đánh giá (bắt vi phạm UNIQUE ⇒ 6002) → `UPDATE products SET rating_count = rating_count+1, rating_sum = rating_sum+:r` → `evictAfterCommit` cache chi tiết sản phẩm.
**Sửa:** tải đánh giá (chưa xóa) → kiểm tra chủ → đặt `edited=true` → nếu đang `VISIBLE` thì `rating_sum += (mới − cũ)` → evict.
**Xóa:** kiểm tra chủ/Admin → soft delete → nếu đang `VISIBLE` thì `count−1`, `sum−rating` → evict.
**Ẩn/hiện:** đổi trạng thái; ẩn: trừ khỏi tổng hợp, hiện: cộng lại; đổi sang đúng trạng thái hiện tại thì không làm gì (không trừ/cộng hai lần).
**Tên che bớt:** giữ họ và chữ cái đầu của tên cuối, phần còn lại thay bằng `***`; tên rỗng/null ⇒ `Người dùng ẩn danh`.

## 9. TIÊU CHÍ NGHIỆM THU & MA TRẬN KIỂM THỬ (cho Tester)
| ID | Kịch bản | Kết quả mong đợi |
|---|---|---|
| R01 | User có đơn DELIVERED chứa SP, gửi 5 sao + nhận xét | 201; `rating_count`+1, `rating_sum`+5; cache chi tiết bị xóa |
| R02 | Gửi không có nhận xét | 201, `comment` null |
| R03 | Chưa mua SP | 403, 6003 |
| R04 | Có đơn chứa SP nhưng chỉ ở trạng thái SHIPPING/CONFIRMED/CANCELLED | 403, 6003 |
| R05 | Gửi lần 2 cho cùng SP | 409, 6002 |
| R06 | Hai request gửi đồng thời cùng user+SP | Một thành công, một 409; tổng hợp chỉ +1 lần |
| R07 | `rating` = 0, 6, null, thiếu | 400 |
| R08 | `comment` dài 1001 ký tự | 400; đúng 1000 ký tự thì chấp nhận |
| R09 | SP không tồn tại / INACTIVE | 404 / lỗi PRODUCT_NOT_AVAILABLE |
| R10 | Chưa đăng nhập gửi/sửa/xóa | 401 |
| R11 | Sửa đánh giá của mình từ 5 → 3 | 200; `edited=true`; `rating_sum` giảm 2, count giữ nguyên |
| R12 | Sửa đánh giá của người khác | 403, 6004 |
| R13 | Xóa đánh giá của mình | 200; count−1, sum−rating; không còn trong danh sách |
| R14 | Admin xóa đánh giá của người khác | 200 |
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
| R26 | Response công khai | Không chứa email, `userId`, `orderId`; tên đã che |
| R27 | Chi tiết sản phẩm sau khi có đánh giá mới | Hiển thị ngay điểm mới (cache đã bị xóa) |
| R28 | Admin liệt kê để kiểm duyệt, lọc theo `status` | Đúng; User thường nhận 403 |

## 10. CÂU HỎI CẦN CHỐT Ở GATE 1 (kèm đề xuất mặc định)
1. **Một đánh giá cho mỗi sản phẩm hay mỗi đơn hàng?** Đề xuất: mỗi người một đánh giá cho mỗi sản phẩm (BR-R2), đơn giản và khó spam.
2. **Che tên người đánh giá?** Đề xuất: có (BR-R9) để bảo vệ thông tin người dùng.
3. **Cho sửa đánh giá không giới hạn thời gian?** Đề xuất: không giới hạn, có cờ `edited`. Có thể thêm hạn (ví dụ 30 ngày) nếu bạn muốn.
4. **Admin chỉ ẩn hay được xóa hẳn?** Đề xuất: có cả hai (ẩn để kiểm duyệt, xóa mềm cho vi phạm nặng).
5. **Ảnh trong đánh giá** để ngoài phạm vi phiên bản này, đồng ý không?
