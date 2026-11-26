package com.multimart.modules.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu sửa đánh giá. Các trường đều tùy chọn nhưng phải có ít nhất một trường.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateReviewRequest {

    /** Số sao mới từ 1 đến 5. */
    @Min(value = 1, message = "Số sao tối thiểu là 1")
    @Max(value = 5, message = "Số sao tối đa là 5")
    private Integer rating;

    /** Nhận xét mới, tối đa 1000 ký tự; chuỗi rỗng nghĩa là xóa nhận xét. */
    @Size(max = 1000, message = "Nhận xét tối đa 1000 ký tự")
    private String comment;

    /** Cài đặt ẩn danh mới. */
    private Boolean anonymous;
}
