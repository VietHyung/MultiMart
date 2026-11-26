package com.multimart.modules.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu gửi đánh giá cho một sản phẩm trong một đơn hàng đã giao.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateReviewRequest {

    /** Id đơn hàng đã giao chứa sản phẩm. */
    @NotNull(message = "Mã đơn hàng không được để trống")
    private Long orderId;

    /** Số sao từ 1 đến 5. */
    @NotNull(message = "Số sao không được để trống")
    @Min(value = 1, message = "Số sao tối thiểu là 1")
    @Max(value = 5, message = "Số sao tối đa là 5")
    private Integer rating;

    /** Nhận xét, tối đa 1000 ký tự (tùy chọn). */
    @Size(max = 1000, message = "Nhận xét tối đa 1000 ký tự")
    private String comment;

    /** Ẩn danh khi hiển thị công khai; bỏ trống được hiểu là {@code true}. */
    private Boolean anonymous;
}
