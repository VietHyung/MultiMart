package com.multimart.modules.user.service;

import com.multimart.common.exception.AppException;
import com.multimart.common.exception.ErrorCode;
import com.multimart.modules.user.dto.AuthResponse;
import com.multimart.modules.user.dto.LoginRequest;
import com.multimart.modules.user.dto.RegisterRequest;
import com.multimart.modules.user.dto.UserResponse;
import com.multimart.modules.user.entity.Role;
import com.multimart.modules.user.entity.User;
import com.multimart.modules.user.repository.RoleRepository;
import com.multimart.modules.user.repository.UserRepository;
import com.multimart.security.CustomUserDetailsService;
import com.multimart.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service xử lý toàn bộ logic nghiệp vụ về xác thực tài khoản:
 * Đăng ký tài khoản, Đăng nhập và Lấy thông tin cá nhân của người dùng hiện tại.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;

    /**
     * Xử lý đăng ký tài khoản người dùng mới:
     * - Kiểm tra trùng lặp email.
     * - Băm mật khẩu bằng BCrypt.
     * - Gán vai trò mặc định là ROLE_USER.
     * - Lưu thông tin người dùng vào cơ sở dữ liệu.
     *
     * @param request dữ liệu đăng ký gửi từ client
     * @return UserResponse chứa thông tin tài khoản an toàn (không lộ mật khẩu)
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.USER_EXISTED, "Email này đã được sử dụng: " + request.getEmail());
        }

        // Tìm hoặc tự tạo Role ROLE_USER mặc định
        Role defaultRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_USER").build()));

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .points(0)
                .roles(Set.of(defaultRole))
                .build();

        User savedUser = userRepository.save(user);
        log.info("Registered new user successfully: id={}, email={}", savedUser.getId(), savedUser.getEmail());

        return mapToUserResponse(savedUser);
    }

    /**
     * Xử lý đăng nhập tài khoản:
     * - Xác thực email và mật khẩu qua Spring Security AuthenticationManager.
     * - Sinh cặp token: Access Token (15 phút) và Refresh Token (7 ngày).
     *
     * @param request thông tin email và mật khẩu đăng nhập
     * @return AuthResponse chứa cặp token và thông tin người dùng
     */
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (BadCredentialsException ex) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS, "Tài khoản hoặc mật khẩu không chính xác");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtTokenProvider.generateAccessToken(userDetails);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails);

        return AuthResponse.builder()
                .tokenType("Bearer")
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(mapToUserResponse(user))
                .build();
    }

    /**
     * Lấy thông tin chi tiết của người dùng đang đăng nhập dựa trên email.
     *
     * @param email địa chỉ email lấy từ SecurityContextHolder
     * @return UserResponse chứa thông tin cá nhân và danh sách quyền
     */
    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return mapToUserResponse(user);
    }

    /**
     * Helper chuyển đổi đối tượng Entity User sang DTO UserResponse an toàn để trả về client.
     *
     * @param user đối tượng thực thể User
     * @return DTO UserResponse
     */
    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .points(user.getPoints())
                .roles(user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()))
                .build();
    }
}
