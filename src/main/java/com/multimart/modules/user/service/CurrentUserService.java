package com.multimart.modules.user.service;

import com.multimart.common.exception.AppException;
import com.multimart.common.exception.ErrorCode;
import com.multimart.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

/**
 * Tiện ích xác định id người dùng đang đăng nhập từ {@link UserDetails} của Spring Security.
 */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    /**
     * Lấy id người dùng theo email (username) trong token đăng nhập.
     *
     * @param userDetails thông tin người dùng đã xác thực
     * @return id người dùng
     * @throws AppException USER_NOT_FOUND khi tài khoản không còn tồn tại
     */
    public Long getUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND))
                .getId();
    }
}
