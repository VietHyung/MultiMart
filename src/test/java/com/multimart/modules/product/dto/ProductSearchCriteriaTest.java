package com.multimart.modules.product.dto;

import com.multimart.common.exception.AppException;
import com.multimart.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm thử chuẩn hóa và kiểm tra tham số tìm kiếm sản phẩm (SRS-CATALOG-001: K11-K16, K19).
 */
class ProductSearchCriteriaTest {

    private ProductSearchCriteria of(String keyword, BigDecimal min, BigDecimal max, String sort,
                                     String sortBy, String direction, int page, int size) {
        return ProductSearchCriteria.of(keyword, null, min, max, null, sort, sortBy, direction, page, size);
    }

    private void assertInvalid(Runnable action) {
        AppException ex = assertThrows(AppException.class, action::run);
        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("K11: Từ khóa được trim khoảng trắng đầu cuối")
    void keywordIsTrimmed() {
        assertEquals("ban phim", of("  ban phim  ", null, null, null, null, null, 0, 10).keyword());
    }

    @Test
    @DisplayName("K11: Từ khóa rỗng hoặc chỉ có khoảng trắng nghĩa là không lọc")
    void blankKeywordMeansNoFilter() {
        assertNull(of("   ", null, null, null, null, null, 0, 10).keyword());
        assertNull(of(null, null, null, null, null, null, 0, 10).keyword());
    }

    @Test
    @DisplayName("K12: Từ khóa 1 ký tự hoặc dài hơn 100 ký tự bị từ chối")
    void keywordLengthIsValidated() {
        assertInvalid(() -> of("a", null, null, null, null, null, 0, 10));
        assertInvalid(() -> of("x".repeat(101), null, null, null, null, null, 0, 10));
    }

    @Test
    @DisplayName("K13: minPrice lớn hơn maxPrice hoặc giá âm bị từ chối")
    void priceRangeIsValidated() {
        assertInvalid(() -> of(null, new BigDecimal("500"), new BigDecimal("100"), null, null, null, 0, 10));
        assertInvalid(() -> of(null, new BigDecimal("-1"), null, null, null, null, 0, 10));
        assertInvalid(() -> of(null, null, new BigDecimal("-5"), null, null, null, 0, 10));
    }

    @Test
    @DisplayName("K13: minPrice bằng maxPrice là hợp lệ")
    void equalPriceBoundsAreAllowed() {
        ProductSearchCriteria c = of(null, new BigDecimal("100"), new BigDecimal("100"), null, null, null, 0, 10);
        assertEquals(new BigDecimal("100"), c.minPrice());
    }

    @Test
    @DisplayName("K15: Mỗi giá trị sort hợp lệ ánh xạ đúng, giá trị lạ bị từ chối")
    void sortValuesAreMapped() {
        assertEquals(ProductSearchCriteria.SortOption.NEWEST, of(null, null, null, "newest", null, null, 0, 10).sort());
        assertEquals(ProductSearchCriteria.SortOption.PRICE_ASC, of(null, null, null, "priceAsc", null, null, 0, 10).sort());
        assertEquals(ProductSearchCriteria.SortOption.PRICE_DESC, of(null, null, null, "priceDesc", null, null, 0, 10).sort());
        assertEquals(ProductSearchCriteria.SortOption.NAME_ASC, of(null, null, null, "nameAsc", null, null, 0, 10).sort());
        assertInvalid(() -> of(null, null, null, "random", null, null, 0, 10));
    }

    @Test
    @DisplayName("K15: Mặc định sắp xếp mới nhất khi không truyền sort")
    void defaultSortIsNewest() {
        assertEquals(ProductSearchCriteria.SortOption.NEWEST, of(null, null, null, null, null, null, 0, 10).sort());
    }

    @Test
    @DisplayName("K19: Tham số cũ sortBy + direction vẫn hoạt động, trường lạ bị từ chối")
    void legacySortParametersStillWork() {
        assertEquals(ProductSearchCriteria.SortOption.NEWEST, of(null, null, null, null, "createdAt", "desc", 0, 10).sort());
        assertEquals(ProductSearchCriteria.SortOption.OLDEST, of(null, null, null, null, "createdAt", "asc", 0, 10).sort());
        assertEquals(ProductSearchCriteria.SortOption.PRICE_ASC, of(null, null, null, null, "originalPrice", "asc", 0, 10).sort());
        assertEquals(ProductSearchCriteria.SortOption.NAME_DESC, of(null, null, null, null, "name", "desc", 0, 10).sort());
        assertInvalid(() -> of(null, null, null, null, "password", "asc", 0, 10));
    }

    @Test
    @DisplayName("K16: size vượt 50 bị cắt về 50; size nhỏ hơn 1 hoặc page âm bị từ chối")
    void pagingIsBounded() {
        assertEquals(50, of(null, null, null, null, null, null, 0, 1000).size());
        assertEquals(20, of(null, null, null, null, null, null, 0, 20).size());
        assertInvalid(() -> of(null, null, null, null, null, null, 0, 0));
        assertInvalid(() -> of(null, null, null, null, null, null, -1, 10));
    }

    @Test
    @DisplayName("inStock mặc định false, true khi truyền true")
    void inStockFlag() {
        assertFalse(ProductSearchCriteria.of(null, null, null, null, null, null, null, null, 0, 10).inStock());
        assertTrue(ProductSearchCriteria.of(null, null, null, null, true, null, null, null, 0, 10).inStock());
    }

    @Test
    @DisplayName("Khóa cache giống nhau cho tiêu chí tương đương (không phân biệt hoa thường, 100 và 100.00)")
    void cacheFingerprintIsStable() {
        String a = of("Ban Phim", new BigDecimal("100"), null, null, null, null, 0, 10).cacheFingerprint();
        String b = of("ban phim", new BigDecimal("100.00"), null, null, null, null, 0, 10).cacheFingerprint();
        assertEquals(a, b);
        String c = of("ban phim", new BigDecimal("100"), null, null, null, null, 1, 10).cacheFingerprint();
        assertTrue(!a.equals(c));
    }
}
