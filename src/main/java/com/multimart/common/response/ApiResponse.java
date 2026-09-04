package com.multimart.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Lớp vỏ bọc (envelope) chuẩn hóa cho mọi phản hồi JSON từ REST API của hệ thống.
 *
 * @param <T> Kiểu dữ liệu của payload trả về trong trường data
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    @Builder.Default
    private int code = 1000;

    @Builder.Default
    private String message = "Success";

    private T data;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Tạo phản hồi thành công mặc định với mã 1000 và thông điệp "Success".
     *
     * @param <T>  Kiểu dữ liệu của payload
     * @param data Dữ liệu kết quả nghiệp vụ trả về cho client
     * @return Đối tượng ApiResponse chứa dữ liệu
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code(1000)
                .message("Success")
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Tạo phản hồi thành công kèm theo thông điệp tùy biến và dữ liệu.
     *
     * @param <T>     Kiểu dữ liệu của payload
     * @param message Thông điệp mô tả kết quả thành công (VD: "Tạo sản phẩm thành công")
     * @param data    Dữ liệu kết quả nghiệp vụ
     * @return Đối tượng ApiResponse tùy biến
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .code(1000)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Tạo phản hồi lỗi chuẩn hóa khi xảy ra ngoại lệ hoặc từ chối yêu cầu.
     *
     * @param <T>     Kiểu dữ liệu (thường là Void)
     * @param code    Mã lỗi định danh của hệ thống (lấy từ ErrorCode)
     * @param message Thông điệp giải thích nguyên nhân lỗi
     * @return Đối tượng ApiResponse chứa mã lỗi và thông điệp
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
