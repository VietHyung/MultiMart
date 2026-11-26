package com.multimart.modules.product.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {
    private Long id;
    private String name;
    private String description;
    private String imageUrl;
    private BigDecimal originalPrice;
    private Integer totalStock;
    private String status;
    private Long categoryId;
    private String categoryName;
    /** Điểm đánh giá trung bình (0 nếu chưa có đánh giá). */
    private Double ratingAverage;
    /** Số đánh giá đang hiển thị. */
    private Integer ratingCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
