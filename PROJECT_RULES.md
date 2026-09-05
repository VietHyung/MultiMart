# BỘ QUY TẮC PHÁT TRIỂN HỆ THỐNG MULTIMART (PROJECT_RULES.md)
> **Áp dụng:** Toàn bộ thành viên ảo (Mary, Winston, Amelia, Bob) và Nhà phát triển trong dự án MultiMart.
> **Phương pháp luận:** BMAD Method + AI-DLC (AI-Driven Development Life Cycle).

---

## 1. NGUYÊN TẮC VÀNG VỀ QUY TRÌNH (AI-DLC GOVERNANCE)

1. **Gate 1 (Pre-Code Approval):**
   - **TUYỆT ĐỐI KHÔNG** sinh mã nguồn Java (Entity, Repository, Service, Controller) trước khi người dùng (Human Tech Lead) xem và duyệt tài liệu đặc tả (PRD/User Story từ Mary, ERD/API Spec từ Winston).
2. **Gate 2 (Post-Code Verification):**
   - Sau khi Amelia viết code và Bob viết test, phải kiểm thử `mvnw test` thành công 100%.
   - Trình bày tóm tắt thay đổi và các điểm kiểm thử trước khi commit vào nhánh chính.
3. **Lưu vết đặc tả (Spec Storage):**
   - Mọi module sau khi hoàn thành hoặc bắt đầu phải được lưu trữ tài liệu đặc tả tại thư mục `docs/specs/<feature_name>_spec.md`.

---

## 2. NGUYÊN TẮC KIẾN TRÚC MÃ NGUỒN (STRICT LAYERED ARCHITECTURE)

Hệ thống tuân thủ nghiêm ngặt mô hình 5 tầng:
`Controller` -> `Service (Interface & Impl)` -> `Repository` -> `Entity` & `Database`

### 2.1. Controller Layer
- **Không bao giờ rò rỉ JPA Entity ra Controller**: Tất cả request/response đều phải sử dụng DTO (`*Request`, `*Response`).
- **Phản hồi chuẩn hóa**: Tất cả API trả về cấu trúc `ApiResponse<T>` hoặc `PageResponse<T>`.
- **Validation**: Mọi `@RequestBody` đều phải có `@Valid`. Các DTO phải khai báo tường minh annotation của Bean Validation (`@NotBlank`, `@NotNull`, `@Min`, `@Size`, `@Email`...).
- **Bảo mật & Phân quyền**: Áp dụng phân quyền phương thức `@PreAuthorize("hasRole('ADMIN')")` hoặc `@PreAuthorize("isAuthenticated()")` trên từng endpoint cần bảo vệ.

### 2.2. Service Layer
- **Tách biệt Interface & Implementation**: Các nghiệp vụ phức tạp nên có interface và implementation rõ ràng.
- **Transactional**: Khai báo `@Transactional` tường minh trên các thao tác ghi/cập nhật dữ liệu, `@Transactional(readOnly = true)` trên các truy vấn đọc để tối ưu hiệu năng connection pool.
- **Không catch Exception bừa bãi**: Ném lỗi nghiệp vụ có định danh qua `AppException(ErrorCode.XYZ)` để tầng Exception Handler toàn cục xử lý.

### 2.3. Repository Layer
- Sử dụng Spring Data JPA, viết phương thức tuân thủ chuẩn naming conventions.
- Chú ý tránh **N+1 Query**: Sử dụng `@EntityGraph` hoặc `JOIN FETCH` khi truy vấn các entity có quan hệ `@OneToMany` hoặc `@ManyToOne` (FetchType.LAZY mặc định).
- Phân trang chuẩn bằng `Pageable` và `Page<T>`.

### 2.4. Entity Layer
- Kế thừa `BaseEntity` để tự động hóa JPA Auditing (`createdAt`, `updatedAt`).
- Áp dụng Soft Delete (`isDeleted = false`) cho các thực thể dữ liệu nghiệp vụ quan trọng (như Product, Category, Store, Order).
- Khóa lạc quan (Optimistic Locking) bằng `@Version` trên các bảng có biến động số lượng tồn kho hoặc trạng thái tranh chấp cao.

### 2.5. Exception Handling & Error Codes
- Tất cả lỗi tập trung tại `GlobalExceptionHandler` (`@RestControllerAdvice`).
- Mọi mã lỗi nằm trong `ErrorCode` enum với HTTP Status Code và Message tiếng Anh/Việt chuẩn hóa.

---

## 3. CHUẨN TÀI LIỆU HÓA VÀ GHI CHÚ (JAVADOC & DOCUMENTATION)

1. **100% JavaDoc Tiếng Việt**:
   - Mọi class, method trong Controller, Service, Repository, Entity, DTO, Config phải có JavaDoc giải thích ngắn gọn, súc tích:
     - Mục đích của hàm / class.
     - `@param`: Ý nghĩa từng tham số đầu vào.
     - `@return`: Dữ liệu trả về.
     - `@throws`: Các ngoại lệ nghiệp vụ có thể xảy ra.
2. **Ngôn ngữ code**:
   - Code, tên class, biến, hàm: **100% Tiếng Anh chuẩn**.
   - Comment giải thích logic nghiệp vụ, JavaDoc: **Tiếng Việt**.

---

## 4. TIÊU CHUẨN KIỂM THỬ (TESTING & QA)

1. **Tỷ lệ bao phủ kiểm thử (Coverage)**:
   - Mỗi Service logic phải có Unit Test với Mockito bao phủ cả Happy Path và Unhappy Path (throw AppException).
   - Mỗi Controller phải có Controller Test / Integration Test với MockMvc kiểm tra Status Code, JSON response và Phân quyền (`@WithMockUser`).
2. **Tính độc lập của Test**:
   - Không phụ thuộc vào dữ liệu cố định có sẵn trong DB (sử dụng dynamic data như timestamp/nanoTime khi cần giá trị unique).
   - Đảm bảo lệnh `./mvnw test` luôn trả về `BUILD SUCCESS`.
