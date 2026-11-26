package com.multimart.modules.review.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu Admin ẩn hoặc hiện một đánh giá.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateReviewVisibilityRequest {

    /** {@code true} để hiện đánh giá, {@code false} để ẩn. */
    @NotNull(message = "Trạng thái hiển thị không được để trống")
    private Boolean visible;
}
