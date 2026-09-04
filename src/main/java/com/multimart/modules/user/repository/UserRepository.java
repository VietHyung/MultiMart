package com.multimart.modules.user.repository;

import com.multimart.modules.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository thao tác với bảng dữ liệu người dùng (users).
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Tìm kiếm người dùng theo địa chỉ email.
     *
     * @param email địa chỉ email cần tìm
     * @return Optional chứa đối tượng User nếu tìm thấy, ngược lại rỗng
     */
    Optional<User> findByEmail(String email);

    /**
     * Kiểm tra xem địa chỉ email đã tồn tại trong cơ sở dữ liệu hay chưa.
     *
     * @param email địa chỉ email cần kiểm tra
     * @return true nếu đã có người dùng đăng ký email này, ngược lại false
     */
    boolean existsByEmail(String email);
}
