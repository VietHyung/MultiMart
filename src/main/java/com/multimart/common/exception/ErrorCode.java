package com.multimart.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    // 1xxx: Lỗi chung hệ thống
    UNCATEGORIZED_EXCEPTION(9999, "Lỗi không xác định từ hệ thống", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_REQUEST(1001, "Dữ liệu yêu cầu không hợp lệ", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(1002, "Không tìm thấy tài nguyên yêu cầu", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(1003, "Phương thức HTTP không được hỗ trợ", HttpStatus.METHOD_NOT_ALLOWED),

    // 2xxx: Xác thực & Phân quyền (Auth & Security)
    UNAUTHENTICATED(2001, "Người dùng chưa đăng nhập", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(2002, "Bạn không có quyền truy cập chức năng này", HttpStatus.FORBIDDEN),
    INVALID_CREDENTIALS(2003, "Email hoặc mật khẩu không chính xác", HttpStatus.UNAUTHORIZED),
    USER_EXISTED(2004, "Email người dùng đã tồn tại trong hệ thống", HttpStatus.CONFLICT),
    USER_NOT_FOUND(2005, "Không tìm thấy người dùng", HttpStatus.NOT_FOUND),

    // 3xxx: Nghiệp vụ Sản phẩm & Flash-Sale
    PRODUCT_NOT_FOUND(3001, "Sản phẩm không tồn tại", HttpStatus.NOT_FOUND),
    EVENT_NOT_FOUND(3002, "Sự kiện Flash-Sale không tồn tại", HttpStatus.NOT_FOUND),
    EVENT_NOT_ACTIVE(3003, "Sự kiện Flash-Sale chưa bắt đầu hoặc đã kết thúc", HttpStatus.BAD_REQUEST),
    OUT_OF_STOCK(3004, "Sản phẩm trong đợt Flash-Sale đã hết hàng", HttpStatus.BAD_REQUEST),
    PURCHASE_LIMIT_EXCEEDED(3005, "Bạn đã vượt quá giới hạn số lượng được mua trong đợt sale này", HttpStatus.BAD_REQUEST),
    ORDER_NOT_FOUND(3006, "Đơn hàng không tồn tại", HttpStatus.NOT_FOUND),
    ORDER_EXPIRED(3007, "Đơn hàng đã hết hạn thanh toán", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
