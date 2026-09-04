package com.multimart.modules.user.controller;

import com.multimart.common.response.ApiResponse;
import com.multimart.modules.user.dto.AuthResponse;
import com.multimart.modules.user.dto.LoginRequest;
import com.multimart.modules.user.dto.RegisterRequest;
import com.multimart.modules.user.dto.UserResponse;
import com.multimart.modules.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Controller tiếp nhận các yêu cầu liên quan đến xác thực người dùng:
 * Đăng ký tài khoản, Đăng nhập và Xem thông tin cá nhân.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * API Đăng ký tài khoản người dùng mới.
     * Cho phép truy cập công khai không cần đăng nhập.
     *
     * @param request dữ liệu đăng ký hợp lệ (email, password tối thiểu 6 ký tự, fullName)
     * @return HTTP 201 Created cùng thông tin tài khoản vừa tạo
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đăng ký tài khoản thành công", response));
    }

    /**
     * API Đăng nhập tài khoản.
     * Cho phép truy cập công khai không cần đăng nhập.
     *
     * @param request thông tin email và mật khẩu
     * @return HTTP 200 OK cùng cặp Access Token và Refresh Token
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công", response));
    }

    /**
     * API Lấy thông tin chi tiết của người dùng đang đăng nhập.
     * Bắt buộc phải có Bearer Token hợp lệ trong header Authorization.
     *
     * @param userDetails thông tin người dùng được trích xuất tự động từ SecurityContext
     * @return HTTP 200 OK cùng thông tin cá nhân của người dùng
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        UserResponse response = authService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
