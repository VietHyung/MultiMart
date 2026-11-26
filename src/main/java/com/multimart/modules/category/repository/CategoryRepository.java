package com.multimart.modules.category.repository;

import com.multimart.modules.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository truy vấn danh mục sản phẩm.
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * Tìm danh mục chưa bị xóa theo id.
     *
     * @param id id danh mục
     * @return danh mục nếu tồn tại và chưa xóa
     */
    Optional<Category> findByIdAndDeletedFalse(Long id);

    /**
     * Lấy toàn bộ danh mục chưa xóa, sắp theo thứ tự hiển thị rồi theo tên.
     *
     * @return danh sách danh mục
     */
    List<Category> findByDeletedFalseOrderBySortOrderAscNameAsc();

    /**
     * Lấy các danh mục con chưa xóa của một danh mục cha.
     *
     * @param parentId id danh mục cha
     * @return danh sách danh mục con
     */
    List<Category> findByParentIdAndDeletedFalse(Long parentId);

    /**
     * Kiểm tra trùng tên (không phân biệt hoa thường) trong cùng một danh mục cha.
     *
     * @param parentId id danh mục cha (khác null)
     * @param name     tên cần kiểm tra
     * @return true nếu đã tồn tại
     */
    boolean existsByParentIdAndNameIgnoreCaseAndDeletedFalse(Long parentId, String name);

    /**
     * Kiểm tra trùng tên (không phân biệt hoa thường) giữa các danh mục gốc.
     *
     * @param name tên cần kiểm tra
     * @return true nếu đã tồn tại
     */
    boolean existsByParentIdIsNullAndNameIgnoreCaseAndDeletedFalse(String name);

    /**
     * Kiểm tra slug đã được dùng (kể cả danh mục đã xóa mềm vì slug là duy nhất toàn bảng).
     *
     * @param slug slug cần kiểm tra
     * @return true nếu đã tồn tại
     */
    boolean existsBySlug(String slug);
}
