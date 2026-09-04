package com.multimart.modules.product.repository;

import com.multimart.modules.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository thao tác với bảng dữ liệu sản phẩm gốc (products).
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Tìm kiếm danh sách sản phẩm theo tên (không phân biệt hoa thường) và theo trạng thái, có phân trang.
     *
     * @param name     từ khóa tìm kiếm trong tên sản phẩm
     * @param status   trạng thái sản phẩm (ACTIVE, INACTIVE)
     * @param pageable thông tin phân trang và sắp xếp
     * @return Trang danh sách sản phẩm khớp điều kiện
     */
    Page<Product> findByNameContainingIgnoreCaseAndStatus(String name, String status, Pageable pageable);

    /**
     * Lấy danh sách sản phẩm theo trạng thái, có phân trang.
     *
     * @param status   trạng thái sản phẩm (ACTIVE, INACTIVE)
     * @param pageable thông tin phân trang và sắp xếp
     * @return Trang danh sách sản phẩm
     */
    Page<Product> findByStatus(String status, Pageable pageable);

    /**
     * Tìm kiếm một sản phẩm theo ID và trạng thái cụ thể.
     *
     * @param id     mã định danh của sản phẩm
     * @param status trạng thái cần tìm (thường là ACTIVE)
     * @return Optional chứa Product nếu tìm thấy
     */
    Optional<Product> findByIdAndStatus(Long id, String status);
}
