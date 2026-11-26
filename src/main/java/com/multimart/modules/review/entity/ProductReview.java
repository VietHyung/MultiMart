package com.multimart.modules.review.entity;

import com.multimart.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Đánh giá của người dùng cho một sản phẩm trong một đơn hàng đã giao.
 * Mỗi cặp (đơn hàng, sản phẩm) có tối đa một đánh giá còn hiệu lực. Xóa mềm bằng cờ {@code deleted}.
 */
@Entity
@Table(name = "product_reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductReview extends BaseEntity {

    /** Id sản phẩm được đánh giá. */
    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** Id người dùng viết đánh giá. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Id đơn hàng đã giao làm căn cứ đánh giá. */
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** Số sao từ 1 đến 5. */
    @Column(nullable = false)
    private Integer rating;

    /** Nhận xét (tùy chọn, tối đa 1000 ký tự). */
    @Column(length = 1000)
    private String comment;

    /** {@code true} nếu người viết chọn ẩn danh khi hiển thị công khai. */
    @Builder.Default
    @Column(nullable = false)
    private boolean anonymous = true;

    /** Trạng thái hiển thị. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewStatus status = ReviewStatus.VISIBLE;

    /** {@code true} nếu đánh giá đã được sửa ít nhất một lần. */
    @Builder.Default
    @Column(nullable = false)
    private boolean edited = false;

    /** Cờ xóa mềm. */
    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;
}
