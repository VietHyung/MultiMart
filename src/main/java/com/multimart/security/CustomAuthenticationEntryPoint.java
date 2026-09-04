package com.multimart.security;

import com.multimart.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Xử lý các request truy cập vào API được bảo vệ nhưng chưa đăng nhập hoặc không có JWT Token hợp lệ.
 * Trả về phản hồi lỗi HTTP 401 Unauthorized theo đúng định dạng JSON chuẩn của hệ thống.
 */
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /**
     * Ghi đè phương thức commence để trả về JSON lỗi 401 Unauthorized kèm mã lỗi 2001 (UNAUTHENTICATED).
     *
     * @param request       HTTP request
     * @param response      HTTP response
     * @param authException ngoại lệ xác thực từ Spring Security
     * @throws IOException lỗi vào/ra khi ghi dữ liệu phản hồi
     */
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        String json = String.format(
                "{\"code\":%d,\"message\":\"%s\",\"timestamp\":\"%s\"}",
                ErrorCode.UNAUTHENTICATED.getCode(),
                ErrorCode.UNAUTHENTICATED.getMessage(),
                LocalDateTime.now()
        );

        response.getWriter().write(json);
    }
}
