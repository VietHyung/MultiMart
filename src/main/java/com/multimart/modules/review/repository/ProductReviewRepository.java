package com.multimart.modules.review.repository;

import com.multimart.modules.review.entity.ProductReview;
import com.multimart.modules.review.entity.ReviewStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository truy vấn đánh giá sản phẩm.
 */
@Repository
public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {

    /**
     * Tìm đánh giá chưa xóa theo id và khóa dòng để sửa (tránh tính sai tổng hợp khi thao tác đồng thời).
     *
     * @param id id đánh giá
     * @return đánh giá nếu tồn tại
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ProductReview r where r.id = :id and r.deleted = false")
    Optional<ProductReview> findByIdForUpdate(@Param("id") Long id);

    /**
     * Kiểm tra đã có đánh giá còn hiệu lực cho cặp đơn hàng và sản phẩm hay chưa.
     *
     * @param orderId   id đơn hàng
     * @param productId id sản phẩm
     * @return {@code true} nếu đã tồn tại
     */
    boolean existsByOrderIdAndProductIdAndDeletedFalse(Long orderId, Long productId);

    /**
     * Danh sách đánh giá của một sản phẩm theo trạng thái.
     *
     * @param productId id sản phẩm
     * @param status    trạng thái cần lấy
     * @param pageable  phân trang và sắp xếp
     * @return trang đánh giá
     */
    Page<ProductReview> findByProductIdAndStatusAndDeletedFalse(Long productId, ReviewStatus status, Pageable pageable);

    /**
     * Danh sách đánh giá của một sản phẩm theo trạng thái và số sao.
     *
     * @param productId id sản phẩm
     * @param status    trạng thái cần lấy
     * @param rating    số sao cần lọc
     * @param pageable  phân trang và sắp xếp
     * @return trang đánh giá
     */
    Page<ProductReview> findByProductIdAndStatusAndRatingAndDeletedFalse(
            Long productId, ReviewStatus status, Integer rating, Pageable pageable);

    /**
     * Danh sách đánh giá của một người dùng (gồm cả đánh giá bị ẩn).
     *
     * @param userId   id người dùng
     * @param pageable phân trang và sắp xếp
     * @return trang đánh giá
     */
    Page<ProductReview> findByUserIdAndDeletedFalse(Long userId, Pageable pageable);

    /**
     * Danh sách mọi đánh giá cho Admin kiểm duyệt.
     *
     * @param pageable phân trang và sắp xếp
     * @return trang đánh giá
     */
    Page<ProductReview> findByDeletedFalse(Pageable pageable);

    /**
     * Danh sách đánh giá theo trạng thái cho Admin kiểm duyệt.
     *
     * @param status   trạng thái cần lọc
     * @param pageable phân trang và sắp xếp
     * @return trang đánh giá
     */
    Page<ProductReview> findByStatusAndDeletedFalse(ReviewStatus status, Pageable pageable);

    /**
     * Đếm số đánh giá đang hiển thị của một sản phẩm theo từng mức sao.
     *
     * @param productId id sản phẩm
     * @return danh sách cặp {số sao, số lượng}
     */
    @Query("select r.rating, count(r) from ProductReview r "
            + "where r.productId = :productId and r.status = com.multimart.modules.review.entity.ReviewStatus.VISIBLE "
            + "and r.deleted = false group by r.rating")
    List<Object[]> countVisibleByRating(@Param("productId") Long productId);
}
