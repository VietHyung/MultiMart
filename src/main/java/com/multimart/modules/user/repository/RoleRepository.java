package com.multimart.modules.user.repository;

import com.multimart.modules.user.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository thao tác với bảng dữ liệu vai trò quyền hạn (roles).
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    /**
     * Tìm kiếm vai trò quyền hạn theo tên vai trò (VD: ROLE_USER, ROLE_ADMIN).
     *
     * @param name tên quyền
     * @return Optional chứa Role nếu tìm thấy
     */
    Optional<Role> findByName(String name);
}
