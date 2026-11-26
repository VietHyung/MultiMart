package com.multimart.modules.category.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Dữ liệu danh mục trả về cho client, dạng nút của cây danh mục.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponse {

    /** Id danh mục. */
    private Long id;

    /** Tên danh mục. */
    private String name;

    /** Slug danh mục. */
    private String slug;

    /** Id danh mục cha (null nếu là gốc). */
    private Long parentId;

    /** Trạng thái danh mục. */
    private String status;

    /** Số sản phẩm ACTIVE; của danh mục cha bao gồm cả danh mục con. */
    private long productCount;

    /** Danh sách danh mục con. */
    @Builder.Default
    private List<CategoryResponse> children = new ArrayList<>();
}
