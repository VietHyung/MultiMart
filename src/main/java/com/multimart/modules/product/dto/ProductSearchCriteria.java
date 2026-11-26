package com.multimart.modules.product.dto;

import com.multimart.common.exception.AppException;
import com.multimart.common.exception.ErrorCode;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Tiêu chí tìm kiếm sản phẩm đã được chuẩn hóa và kiểm tra hợp lệ (SRS-CATALOG-001, BR-K6..K9).
 * Dùng làm đầu vào cho truy vấn và để sinh khóa cache.
 *
 * @param keyword    từ khóa tên đã trim (null nếu không lọc)
 * @param categoryId id danh mục lọc (null nếu không lọc)
 * @param minPrice   giá tối thiểu (null nếu không lọc)
 * @param maxPrice   giá tối đa (null nếu không lọc)
 * @param inStock    true để chỉ lấy sản phẩm còn hàng
 * @param sort       kiểu sắp xếp
 * @param page       trang (từ 0)
 * @param size       kích thước trang (1..50)
 */
public record ProductSearchCriteria(
        String keyword,
        Long categoryId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        boolean inStock,
        SortOption sort,
        int page,
        int size
) {

    /** Kích thước trang tối đa. */
    public static final int MAX_SIZE = 50;

    /** Các kiểu sắp xếp được hỗ trợ. */
    public enum SortOption {
        NEWEST, OLDEST, PRICE_ASC, PRICE_DESC, NAME_ASC, NAME_DESC;

        /**
         * Chuyển kiểu sắp xếp thành {@link Sort} của Spring Data, luôn có thứ tự phụ theo id để phân trang ổn định.
         *
         * @return đối tượng Sort
         */
        public Sort toSort() {
            return switch (this) {
                case NEWEST -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
                case OLDEST -> Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));
                case PRICE_ASC -> Sort.by(Sort.Order.asc("originalPrice"), Sort.Order.asc("id"));
                case PRICE_DESC -> Sort.by(Sort.Order.desc("originalPrice"), Sort.Order.asc("id"));
                case NAME_ASC -> Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"));
                case NAME_DESC -> Sort.by(Sort.Order.desc("name"), Sort.Order.asc("id"));
            };
        }
    }

    /**
     * Chuẩn hóa và kiểm tra tham số tìm kiếm thô từ query string.
     *
     * @param keyword    từ khóa (có thể null/rỗng)
     * @param categoryId id danh mục
     * @param minPrice   giá tối thiểu
     * @param maxPrice   giá tối đa
     * @param inStock    chỉ lấy còn hàng
     * @param sort       newest | priceAsc | priceDesc | nameAsc (ưu tiên hơn sortBy)
     * @param sortBy     tham số cũ: createdAt | originalPrice | name
     * @param direction  tham số cũ: asc | desc
     * @param page       trang (từ 0)
     * @param size       kích thước trang, vượt 50 sẽ bị cắt về 50
     * @return tiêu chí đã chuẩn hóa
     * @throws AppException INVALID_REQUEST khi tham số không hợp lệ
     */
    public static ProductSearchCriteria of(String keyword, Long categoryId, BigDecimal minPrice,
                                           BigDecimal maxPrice, Boolean inStock, String sort,
                                           String sortBy, String direction, int page, int size) {
        String normalizedKeyword = null;
        if (StringUtils.hasText(keyword)) {
            normalizedKeyword = keyword.trim();
            if (normalizedKeyword.length() < 2 || normalizedKeyword.length() > 100) {
                throw new AppException(ErrorCode.INVALID_REQUEST, "Từ khóa tìm kiếm phải từ 2 đến 100 ký tự");
            }
        }
        if ((minPrice != null && minPrice.signum() < 0) || (maxPrice != null && maxPrice.signum() < 0)) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Khoảng giá không được âm");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Giá tối thiểu không được lớn hơn giá tối đa");
        }
        if (page < 0) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Số trang không được âm");
        }
        if (size < 1) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Kích thước trang phải lớn hơn 0");
        }
        return new ProductSearchCriteria(normalizedKeyword, categoryId, minPrice, maxPrice,
                Boolean.TRUE.equals(inStock), resolveSort(sort, sortBy, direction), page,
                Math.min(size, MAX_SIZE));
    }

    /** Xác định kiểu sắp xếp từ tham số mới (sort) hoặc tham số cũ (sortBy + direction). */
    private static SortOption resolveSort(String sort, String sortBy, String direction) {
        if (StringUtils.hasText(sort)) {
            return switch (sort.trim()) {
                case "newest" -> SortOption.NEWEST;
                case "priceAsc" -> SortOption.PRICE_ASC;
                case "priceDesc" -> SortOption.PRICE_DESC;
                case "nameAsc" -> SortOption.NAME_ASC;
                default -> throw new AppException(ErrorCode.INVALID_REQUEST,
                        "Kiểu sắp xếp không hợp lệ (newest, priceAsc, priceDesc, nameAsc)");
            };
        }
        if (!StringUtils.hasText(sortBy)) {
            return SortOption.NEWEST;
        }
        boolean desc = !"asc".equalsIgnoreCase(direction == null ? "desc" : direction.trim());
        return switch (sortBy.trim().toLowerCase(Locale.ROOT)) {
            case "createdat" -> desc ? SortOption.NEWEST : SortOption.OLDEST;
            case "originalprice" -> desc ? SortOption.PRICE_DESC : SortOption.PRICE_ASC;
            case "name" -> desc ? SortOption.NAME_DESC : SortOption.NAME_ASC;
            default -> throw new AppException(ErrorCode.INVALID_REQUEST,
                    "Trường sắp xếp không hợp lệ (createdAt, originalPrice, name)");
        };
    }

    /**
     * Sinh chuỗi ổn định đại diện cho bộ tiêu chí, dùng để tạo khóa cache danh sách.
     *
     * @return chuỗi định danh tiêu chí
     */
    public String cacheFingerprint() {
        return String.join("|",
                keyword == null ? "" : keyword.toLowerCase(Locale.ROOT),
                String.valueOf(categoryId),
                minPrice == null ? "" : minPrice.stripTrailingZeros().toPlainString(),
                maxPrice == null ? "" : maxPrice.stripTrailingZeros().toPlainString(),
                String.valueOf(inStock), sort.name(), String.valueOf(page), String.valueOf(size));
    }
}
