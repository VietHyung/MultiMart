package com.multimart.modules.product.entity;

import com.multimart.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "original_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal originalPrice;

    @Builder.Default
    @Column(name = "total_stock", nullable = false)
    private Integer totalStock = 0;

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String status = "ACTIVE"; // ACTIVE, INACTIVE

    /** Id danh mục chứa sản phẩm (tùy chọn, null nếu chưa phân loại). */
    @Column(name = "category_id")
    private Long categoryId;

    /**
     * Số đánh giá đang hiển thị. Chỉ đọc: được cập nhật bằng truy vấn nguyên tử để không bị ghi đè
     * bởi giá trị cũ khi sửa thông tin sản phẩm.
     */
    @Builder.Default
    @Column(name = "rating_count", nullable = false, insertable = false, updatable = false)
    private Integer ratingCount = 0;

    /** Tổng số sao của các đánh giá đang hiển thị (chỉ đọc, xem {@link #ratingCount}). */
    @Builder.Default
    @Column(name = "rating_sum", nullable = false, insertable = false, updatable = false)
    private Integer ratingSum = 0;
}
