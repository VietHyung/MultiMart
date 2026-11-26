package com.multimart.modules.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * Thống kê đánh giá của một sản phẩm.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewSummaryResponse {

    /** Điểm trung bình làm tròn 1 chữ số thập phân (0 nếu chưa có đánh giá). */
    private Double ratingAverage;

    /** Tổng số đánh giá đang hiển thị. */
    private Integer ratingCount;

    /** Số đánh giá theo từng mức sao (khóa 1 đến 5). */
    private Map<Integer, Long> distribution;
}
