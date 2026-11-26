package com.multimart.modules.product.repository;

import com.multimart.modules.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository truy vấn và cập nhật sản phẩm.
 * Hỗ trợ tìm kiếm động bằng Specification và các thao tác trừ/hoàn tồn kho nguyên tử.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /**
     * Tìm kiếm sản phẩm theo tên (chứa từ khóa, không phân biệt hoa thường) và trạng thái.
     *
     * @param name     từ khóa trong tên
     * @param status   trạng thái sản phẩm
     * @param pageable thông tin phân trang và sắp xếp
     * @return trang sản phẩm
     */
    Page<Product> findByNameContainingIgnoreCaseAndStatus(String name, String status, Pageable pageable);

    /**
     * Lấy danh sách sản phẩm theo trạng thái.
     *
     * @param status   trạng thái sản phẩm
     * @param pageable thông tin phân trang và sắp xếp
     * @return trang sản phẩm
     */
    Page<Product> findByStatus(String status, Pageable pageable);

    /**
     * Tìm sản phẩm theo id và trạng thái.
     *
     * @param id     id sản phẩm
     * @param status trạng thái sản phẩm
     * @return sản phẩm nếu tồn tại
     */
    Optional<Product> findByIdAndStatus(Long id, String status);

    /**
     * Đếm sản phẩm theo danh mục và trạng thái (dùng kiểm tra trước khi xóa danh mục).
     *
     * @param categoryId id danh mục
     * @param status     trạng thái sản phẩm
     * @return số sản phẩm
     */
    long countByCategoryIdAndStatus(Long categoryId, String status);

    /**
     * Đếm số sản phẩm ACTIVE theo từng danh mục.
     *
     * @return danh sách mảng [categoryId, số lượng]
     */
    @Query("select p.categoryId, count(p) from Product p "
            + "where p.status = 'ACTIVE' and p.categoryId is not null group by p.categoryId")
    List<Object[]> countActiveGroupByCategory();

    /**
     * Lấy nhiều sản phẩm theo danh sách id, sắp xếp tăng dần theo id (thứ tự khóa cố định chống deadlock).
     *
     * @param ids danh sách id
     * @return danh sách sản phẩm
     */
    List<Product> findByIdInOrderByIdAsc(java.util.Collection<Long> ids);

    /**
     * Trừ tồn kho nguyên tử cho đặt hàng thường: chỉ thành công khi sản phẩm ACTIVE và đủ hàng.
     *
     * @param id  id sản phẩm
     * @param qty số lượng cần trừ
     * @return số dòng bị ảnh hưởng (0 nghĩa là hết hàng hoặc sản phẩm không còn bán)
     */
    @Modifying
    @Query("update Product p set p.totalStock = p.totalStock - :qty "
            + "where p.id = :id and p.status = 'ACTIVE' and p.totalStock >= :qty")
    int deductStock(@Param("id") Long id, @Param("qty") int qty);

    /**
     * Trích tồn kho gốc nguyên tử để cấp cho Flash Sale (không yêu cầu sản phẩm ACTIVE).
     *
     * @param id  id sản phẩm
     * @param qty số lượng cần trích
     * @return số dòng bị ảnh hưởng (0 nghĩa là tồn kho gốc không đủ)
     */
    @Modifying
    @Query("update Product p set p.totalStock = p.totalStock - :qty "
            + "where p.id = :id and p.totalStock >= :qty")
    int reserveStock(@Param("id") Long id, @Param("qty") int qty);

    /**
     * Hoàn trả tồn kho nguyên tử (khi hủy đơn).
     *
     * @param id  id sản phẩm
     * @param qty số lượng hoàn trả
     * @return số dòng bị ảnh hưởng
     */
    @Modifying
    @Query("update Product p set p.totalStock = p.totalStock + :qty where p.id = :id")
    int restoreStock(@Param("id") Long id, @Param("qty") int qty);
}
