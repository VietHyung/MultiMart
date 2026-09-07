# TÀI LIỆU ĐẶC TẢ GIAO DIỆN NGƯỜI DÙNG & TÍCH HỢP HỆ THỐNG MULTIMART (FRONTEND INTEGRATION SPEC)

> **Mã đặc tả:** FE-SPEC-001  
> **Phiên bản:** 1.0.0  
> **Thuộc hệ thống:** MultiMart High-Concurrency E-Commerce Platform  
> **Đội ngũ ảo (BMAD AI-DLC):** Mary (BA), Winston (Architect), Amelia (Senior Dev), Bob (QA)  
> **Trạng thái:** Gate 2 Ready

---

## 1. MỤC TIÊU & TỔNG QUAN HỆ THỐNG
Tài liệu này đặc tả kiến trúc, luồng người dùng và cách thức tích hợp giữa giao diện Frontend (Single-Port Embedded SPA) với hệ thống dịch vụ Backend Spring Boot 3, Redis và RabbitMQ.

Giao diện phục vụ mục đích:
1. Trực quan hóa toàn diện hoạt động của hệ thống thương mại điện tử MultiMart.
2. Kiểm chứng trực tiếp tính năng **Đặt mua Flash Sale chịu tải cao**, cơ chế **Trừ kho nguyên tử trên RAM (Redis Lua Script)**, và **Hàng đợi phi tập trung (RabbitMQ)**.
3. Kiểm chứng cơ chế **Đếm ngược thời gian thực 15 phút (Order Timeout)** và **Tự động hoàn tồn kho (Double Rollback)**.

---

## 2. KIẾN TRÚC KỸ THUẬT (WINSTON - ARCHITECT)

### 2.1. Cấu Trúc Single-Port Embedded SPA
- **Lý do lựa chọn:** Máy phát triển của người dùng chưa cài đặt Node.js/npm. Kiến trúc Embedded SPA tận dụng tầng tĩnh `src/main/resources/static/` của Spring Boot để phục vụ toàn bộ giao diện từ cổng `8080`.
- **Thư viện chính:**
  - **Vue 3 (CDN Reactive)**: Quản lý trạng thái giao diện phản ứng (Reactive Data Binding, Lifecycle hooks, Polling intervals).
  - **Tailwind CSS (CDN)**: Thiết kế giao diện hiện đại, responsive, bảng màu e-commerce Shopee/Lazada (Red/Orange/Dark).
  - **Axios (CDN)**: Xử lý giao tiếp HTTP REST API với tự động đính kèm `Authorization: Bearer <JWT>`.
  - **Lucide Icons**: Bộ icon vector phong phú, sắc nét.

### 2.2. Bản Đồ Tệp (File Structure)
```
MultiMart/
├── src/main/java/com/multimart/
│   ├── config/
│   │   └── DataInitializer.java        # Seeder tài khoản mẫu, sản phẩm mẫu và Warm-up Redis
│   └── security/
│       └── SecurityConfig.java         # Mở quyền truy cập tài nguyên tĩnh và cấu hình CORS
└── src/main/resources/
    └── static/
        ├── index.html                  # Giao diện chính (Header, Banner, Grid, Modals, Toasts)
        ├── css/
        │   └── custom.css              # Animation Pulse, Fire Flicker, Progress Stripes
        └── js/
            ├── api.js                  # Axios client, JWT Interceptor, chuẩn hóa ApiResponse
            └── app.js                  # Vue 3 App: Auth, Countdown, RabbitMQ Polling, Orders & Pay
```

---

## 3. LUỒNG NGHIỆP VỤ & TƯƠNG TÁC (MARY - BA)

### 3.1. Luồng Xác Thực (Authentication)
1. Khách hàng bấm **"Đăng nhập"** hoặc **"Đăng ký"**.
2. Hỗ trợ nút **"Điền nhanh tài khoản kiểm thử"**:
   - `Buyer`: `buyer@multimart.com` / `123456`.
   - `Admin`: `admin@multimart.com` / `Admin@123456`.
3. Nhận về JWT Token, lưu vào `localStorage('multimart_token')` và hiển thị thông tin người dùng trên Header.

### 3.2. Luồng Săn Deal Flash Sale Chớp Nhoáng (High Concurrency Flow)
1. Hệ thống hiển thị Banner sự kiện đang diễn ra với **Đồng hồ đếm ngược từng giây (Live Countdown Timer)**.
2. Thẻ sản phẩm hiển thị % giảm giá, giá gốc (gạch ngang), giá Flash Sale (đỏ đậm), và thanh tiến độ tồn kho (`Còn lại X / Y suất`).
3. Khi bấm **"SĂN DEAL NGAY"**:
   - Gửi yêu cầu `POST /api/v1/flash-sales/orders` (Async Queue Processing).
   - Modal hiển thị **Mô hình Pipeline 3 Bước**:
     - *Bước 1:* Trừ kho RAM bằng Redis Lua Script (< 2ms) -> **DONE**.
     - *Bước 2:* Đẩy vào hàng đợi RabbitMQ -> **DONE**.
     - *Bước 3:* Consumer lưu DB PostgreSQL & Lên lịch Delay Queue 15 phút -> **WAITING/DONE**.
   - Client thực hiện Polling `GET /api/v1/flash-sales/orders/tracking/{trackingId}` mỗi 600ms.
   - Khi nhận `SUCCESS:{orderId}`: Chuyển sang màn hình chúc mừng, hiển thị ID đơn hàng và thời hạn giữ chỗ 15 phút.

### 3.3. Luồng Quản Lý Đơn Hàng & Đếm Ngược 15 Phút (Order Timeout & Pay)
1. Người dùng mở tab **"Đơn hàng của tôi"**.
2. Các đơn hàng ở trạng thái `PENDING`:
   - Hiển thị nhãn cảnh báo **"Chờ thanh toán (15 phút)"**.
   - Nút **"💳 Thanh Toán Ngay"** (`POST /api/v1/flash-sales/orders/{id}/pay`).
   - Sau khi thanh toán thành công: Trạng thái chuyển sang `CONFIRMED`.
3. Nếu đơn hàng không được thanh toán trong 15 phút:
   - RabbitMQ Delay Queue hết hạn TTL, đẩy message sang Cancel Exchange.
   - Cancel Consumer cập nhật đơn thành `CANCELLED` và chạy Lua Script hoàn lại kho trên Redis RAM và DB.
   - Trên UI hiển thị trạng thái `ĐÃ HỦY (QUÁ HẠN)` kèm chú thích kho đã hoàn trả tự động.

### 3.4. Bảng Tiện Ích Quản Trị (Admin Quick Tools)
- Hỗ trợ đăng nhập nhanh quyền Admin.
- Cho phép kích hoạt nút **"🔥 WARM-UP LÊN REDIS"** (`POST /api/v1/admin/flash-sales/{id}/warm-up`) cho bất kỳ sự kiện nào trực tiếp từ UI.

---

## 4. KẾT QUẢ KIỂM THỬ (BOB - QA)
- **Kiểm thử tự động Maven:** 43/43 tests PASS (`BUILD SUCCESS`).
- **Tài nguyên tĩnh:** Được cấu hình mở quyền trong `SecurityConfig.java` tại `/`, `/index.html`, `/css/**`, `/js/**`.
- **Tương thích trình duyệt:** Chrome, Edge, Firefox, Safari (HTML5 + ES6 + Tailwind).
