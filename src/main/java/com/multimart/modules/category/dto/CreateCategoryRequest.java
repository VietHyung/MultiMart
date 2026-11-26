package com.multimart.modules.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu tạo danh mục mới (Admin).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCategoryRequest {

    /** Tên danh mục. */
    @NotBlank(message = "Tên danh mục không được để trống")
    @Size(max = 100, message = "Tên danh mục tối đa 100 ký tự")
    private String name;

    /** Id danh mục cha; null nếu tạo danh mục gốc. */
    private Long parentId;

    /** Thứ tự hiển thị, mặc định 0. */
    private Integer sortOrder;
}
