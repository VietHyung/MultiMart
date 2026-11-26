package com.multimart.modules.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu cập nhật danh mục (Admin). Không hỗ trợ đổi danh mục cha ở phase này.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCategoryRequest {

    /** Tên danh mục mới. */
    @NotBlank(message = "Tên danh mục không được để trống")
    @Size(max = 100, message = "Tên danh mục tối đa 100 ký tự")
    private String name;

    /** Thứ tự hiển thị mới; null giữ nguyên. */
    private Integer sortOrder;

    /** Trạng thái ACTIVE hoặc INACTIVE; null giữ nguyên. */
    private String status;
}
