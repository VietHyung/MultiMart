# ĐẶC TẢ KỸ THUẬT: MODULE XÁC THỰC & PHÂN QUYỀN (AUTH & USER SPEC)
> **Tài liệu lưu vết theo BMAD AI-DLC (Step 3: Operations & Memory)**

---

## 1. MỤC TIÊU NGHIỆP VỤ (BA - Mary)
- Cung cấp cơ chế xác thực người dùng không trạng thái (Stateless Authentication) dựa trên JSON Web Token (JWT).
- Quản lý thông tin tài khoản và vai trò (`ROLE_CUSTOMER`, `ROLE_VENDOR`, `ROLE_ADMIN`).
- Bảo vệ các endpoint nhạy cảm bằng RBAC (Role-Based Access Control) thông qua Spring Security 6.

---

## 2. KIẾN TRÚC & MÔ HÌNH DỮ LIỆU (Architect - Winston)

### 2.1. Cấu trúc Bảng Database (PostgreSQL)
- **`users`**:
  - `id`: `BIGSERIAL PRIMARY KEY`
  - `email`: `VARCHAR(100) NOT NULL UNIQUE`
  - `password`: `VARCHAR(255) NOT NULL` (được mã hóa BCrypt)
  - `full_name`: `VARCHAR(100) NOT NULL`
  - `phone`: `VARCHAR(20)`
  - `is_active`: `BOOLEAN DEFAULT TRUE`
  - `created_at`, `updated_at`: `TIMESTAMP` (JPA Auditing)
- **`roles`**:
  - `id`: `BIGSERIAL PRIMARY KEY`
  - `name`: `VARCHAR(50) NOT NULL UNIQUE` (ví dụ: `ROLE_CUSTOMER`, `ROLE_ADMIN`)
- **`user_roles`**:
  - `user_id`: `BIGINT REFERENCES users(id) ON DELETE CASCADE`
  - `role_id`: `BIGINT REFERENCES roles(id) ON DELETE CASCADE`
  - `PRIMARY KEY (user_id, role_id)`

### 2.2. Cơ chế JWT
- **Access Token**: Hạn 15 phút, chứa `subject` là `email`, claims gồm `userId` và danh sách `roles`.
- **Refresh Token**: Hạn 7 ngày.
- **Filter**: `JwtAuthenticationFilter` giải mã token từ header `Authorization: Bearer <token>`, trích xuất email/roles, nạp vào `SecurityContextHolder`.

---

## 3. DANH SÁCH RESTful API CONTRACT (Architect - Winston)

### 3.1. Đăng ký tài khoản (`POST /api/v1/auth/register`)
- **Phân quyền**: Public
- **Request Body**:
  ```json
  {
    "email": "user@example.com",
    "password": "Password@123",
    "fullName": "Nguyen Van A",
    "phone": "0901234567"
  }
  ```
- **Response (201 Created)**:
  ```json
  {
    "code": 1000,
    "message": "User registered successfully",
    "data": {
      "accessToken": "ey...",
      "refreshToken": "ey...",
      "tokenType": "Bearer",
      "user": {
        "id": 1,
        "email": "user@example.com",
        "fullName": "Nguyen Van A",
        "phone": "0901234567",
        "roles": ["ROLE_CUSTOMER"]
      }
    }
  }
  ```

### 3.2. Đăng nhập (`POST /api/v1/auth/login`)
- **Phân quyền**: Public
- **Request Body**:
  ```json
  {
    "email": "user@example.com",
    "password": "Password@123"
  }
  ```
- **Response (200 OK)**: Cấu trúc tương tự đăng ký.

### 3.3. Xem thông tin cá nhân (`GET /api/v1/auth/me`)
- **Phân quyền**: Yêu cầu Header `Authorization: Bearer <token>`.
- **Response (200 OK)**:
  ```json
  {
    "code": 1000,
    "message": "Get current user profile successfully",
    "data": {
      "id": 1,
      "email": "user@example.com",
      "fullName": "Nguyen Van A",
      "phone": "0901234567",
      "roles": ["ROLE_CUSTOMER"]
    }
  }
  ```

---

## 4. KẾT QUẢ THỰC THI & KIỂM THỬ (Amelia & Bob)
- Các file triển khai: `User`, `Role`, `UserRepository`, `RoleRepository`, `AuthService`, `AuthController`, `SecurityConfig`, `JwtTokenProvider`, `JwtAuthenticationFilter`.
- Đã được kiểm thử tự động tại `AuthControllerTest.java` (Pass 100%).
