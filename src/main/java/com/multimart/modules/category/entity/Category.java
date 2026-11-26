package com.multimart.modules.category.entity;

import com.multimart.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Thực thể danh mục sản phẩm, cây tối đa 2 cấp (gốc -> con).
 * Xóa mềm bằng cờ {@code deleted}.
 */
@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category extends BaseEntity {

    /** Tên hiển thị, duy nhất trong cùng danh mục cha (không phân biệt hoa thường). */
    @Column(nullable = false, length = 100)
    private String name;

    /** Đường dẫn thân thiện, duy nhất toàn hệ thống. */
    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    /** Id danh mục cha; null nếu là danh mục gốc. */
    @Column(name = "parent_id")
    private Long parentId;

    /** Thứ tự hiển thị (nhỏ hiển thị trước). */
    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** Trạng thái: ACTIVE hoặc INACTIVE. */
    @Builder.Default
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    /** Cờ xóa mềm. */
    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;
}
