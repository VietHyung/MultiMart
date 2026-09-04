package com.multimart.security;

import com.multimart.common.exception.AppException;
import com.multimart.common.exception.ErrorCode;
import com.multimart.modules.user.entity.User;
import com.multimart.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/**
 * Service triển khai giao diện UserDetailsService của Spring Security
 * để nạp thông tin người dùng từ cơ sở dữ liệu dựa trên email.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Tìm kiếm người dùng theo email và chuyển đổi thành đối tượng UserDetails
     * chứa danh sách quyền (Roles: ROLE_USER, ROLE_ADMIN) để Spring Security quản lý phiên đăng nhập.
     *
     * @param email địa chỉ email của người dùng (được dùng làm username đăng nhập)
     * @return UserDetails chứa thông tin email, mật khẩu đã mã hóa BCrypt và các Authorities
     * @throws UsernameNotFoundException khi không tìm thấy người dùng
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy người dùng với email: " + email));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .authorities(user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .collect(Collectors.toSet()))
                .build();
    }
}
