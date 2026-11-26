package com.multimart.modules.product.repository;

import com.multimart.modules.product.entity.Product;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Bộ dựng điều kiện truy vấn động cho tìm kiếm sản phẩm (SA ADR-6).
 * Điều kiện từ khóa dùng {@code lower(name) LIKE} để tận dụng index GIN trigram {@code idx_products_name_trgm}.
 */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    /**
     * Dựng Specification tìm sản phẩm ACTIVE theo các điều kiện tùy chọn.
     *
     * @param keyword     từ khóa trong tên (null nếu không lọc)
     * @param categoryIds danh sách id danh mục được phép (null nếu không lọc danh mục)
     * @param minPrice    giá tối thiểu (null nếu không lọc)
     * @param maxPrice    giá tối đa (null nếu không lọc)
     * @param inStock     true để chỉ lấy sản phẩm còn tồn kho
     * @return Specification đã gộp các điều kiện bằng AND
     */
    public static Specification<Product> search(String keyword, Collection<Long> categoryIds,
                                                BigDecimal minPrice, BigDecimal maxPrice, boolean inStock) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), "ACTIVE"));
            if (keyword != null) {
                String pattern = "%" + escapeLike(keyword.toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), pattern, '\\'));
            }
            if (categoryIds != null) {
                predicates.add(root.get("categoryId").in(categoryIds));
            }
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("originalPrice"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("originalPrice"), maxPrice));
            }
            if (inStock) {
                predicates.add(cb.greaterThan(root.get("totalStock"), 0));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** Escape các ký tự đặc biệt của LIKE (\, %, _) để từ khóa được hiểu theo nghĩa đen. */
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
