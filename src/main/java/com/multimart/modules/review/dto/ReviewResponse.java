package com.multimart.modules.review.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Thông tin một đánh giá trả về cho client. Trường {@code orderId} chỉ có ở các API dành cho chủ đánh giá và Admin.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReviewResponse {

    private Long id;
    private Long productId;
    private Long orderId;
    private Integer rating;
    private String comment;
    private String reviewerName;
    private boolean anonymous;
    private String status;
    private boolean edited;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
