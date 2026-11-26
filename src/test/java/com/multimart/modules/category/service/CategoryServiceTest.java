package com.multimart.modules.category.service;

import com.multimart.common.cache.RedisCacheService;
import com.multimart.common.exception.AppException;
import com.multimart.common.exception.ErrorCode;
import com.multimart.modules.category.dto.CategoryResponse;
import com.multimart.modules.category.dto.CreateCategoryRequest;
import com.multimart.modules.category.entity.Category;
import com.multimart.modules.category.repository.CategoryRepository;
import com.multimart.modules.product.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm thử nghiệp vụ danh mục (SRS-CATALOG-001: K01-K07).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private RedisCacheService cacheService;
    @InjectMocks
    private CategoryService categoryService;

    private Category category(Long id, String name, Long parentId) {
        Category c = Category.builder().name(name).slug(name.toLowerCase()).parentId(parentId).build();
        c.setId(id);
        return c;
    }

    private void stubSaveAssignsId(long id) {
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(id);
            }
            return c;
        });
    }

    @Test
    @DisplayName("K01: Tạo danh mục gốc thành công, sinh slug không dấu và xóa cache cây")
    void createRootCategory_Success() {
        stubSaveAssignsId(1L);

        CategoryResponse response = categoryService.createCategory(
                CreateCategoryRequest.builder().name("  Điện Thoại & Máy Tính  ").build());

        assertEquals("Điện Thoại & Máy Tính", response.getName());
        assertEquals("dien-thoai-may-tinh", response.getSlug());
        assertNull(response.getParentId());
        verify(cacheService).evictAfterCommit(CategoryService.TREE_CACHE_KEY);
    }

    @Test
    @DisplayName("K02: Tạo danh mục con dưới danh mục gốc thành công")
    void createChildCategory_Success() {
        when(categoryRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(category(1L, "Điện tử", null)));
        stubSaveAssignsId(2L);

        CategoryResponse response = categoryService.createCategory(
                CreateCategoryRequest.builder().name("Bàn phím").parentId(1L).build());

        assertEquals(1L, response.getParentId());
        assertEquals("ban-phim", response.getSlug());
    }

    @Test
    @DisplayName("K03: Tạo danh mục cấp 3 bị từ chối CATEGORY_DEPTH_EXCEEDED")
    void createThirdLevel_ThrowsDepthExceeded() {
        when(categoryRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(category(2L, "Bàn phím", 1L)));

        AppException ex = assertThrows(AppException.class, () -> categoryService.createCategory(
                CreateCategoryRequest.builder().name("Bàn phím cơ").parentId(2L).build()));

        assertEquals(ErrorCode.CATEGORY_DEPTH_EXCEEDED, ex.getErrorCode());
        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Danh mục cha không tồn tại -> CATEGORY_NOT_FOUND")
    void createWithMissingParent_ThrowsNotFound() {
        when(categoryRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> categoryService.createCategory(
                CreateCategoryRequest.builder().name("Bàn phím").parentId(99L).build()));

        assertEquals(ErrorCode.CATEGORY_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("K04: Trùng tên trong cùng cha (kể cả khác hoa thường) -> CATEGORY_NAME_DUPLICATED")
    void createDuplicateName_ThrowsDuplicated() {
        when(categoryRepository.existsByParentIdIsNullAndNameIgnoreCaseAndDeletedFalse("điện tử")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> categoryService.createCategory(
                CreateCategoryRequest.builder().name("điện tử").build()));

        assertEquals(ErrorCode.CATEGORY_NAME_DUPLICATED, ex.getErrorCode());
    }

    @Test
    @DisplayName("K05: Cùng tên nhưng khác cha vẫn được phép")
    void sameNameUnderDifferentParent_Allowed() {
        when(categoryRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(category(5L, "Thời trang", null)));
        when(categoryRepository.existsByParentIdAndNameIgnoreCaseAndDeletedFalse(5L, "Phụ kiện")).thenReturn(false);
        stubSaveAssignsId(7L);

        CategoryResponse response = categoryService.createCategory(
                CreateCategoryRequest.builder().name("Phụ kiện").parentId(5L).build());

        assertEquals(5L, response.getParentId());
    }

    @Test
    @DisplayName("Slug trùng thì thêm hậu tố số tăng dần")
    void slugCollision_AppendsSuffix() {
        when(categoryRepository.existsBySlug("dien-tu")).thenReturn(true);
        when(categoryRepository.existsBySlug("dien-tu-2")).thenReturn(false);
        stubSaveAssignsId(3L);

        CategoryResponse response = categoryService.createCategory(
                CreateCategoryRequest.builder().name("Điện tử").build());

        assertEquals("dien-tu-2", response.getSlug());
    }

    @Test
    @DisplayName("K06: Xóa danh mục còn danh mục con -> CATEGORY_NOT_EMPTY")
    void deleteWithChildren_ThrowsNotEmpty() {
        when(categoryRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(category(1L, "Điện tử", null)));
        when(categoryRepository.findByParentIdAndDeletedFalse(1L)).thenReturn(List.of(category(2L, "Bàn phím", 1L)));

        AppException ex = assertThrows(AppException.class, () -> categoryService.deleteCategory(1L));

        assertEquals(ErrorCode.CATEGORY_NOT_EMPTY, ex.getErrorCode());
        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("K06: Xóa danh mục còn sản phẩm đang bán -> CATEGORY_NOT_EMPTY")
    void deleteWithProducts_ThrowsNotEmpty() {
        when(categoryRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(category(2L, "Bàn phím", 1L)));
        when(categoryRepository.findByParentIdAndDeletedFalse(2L)).thenReturn(List.of());
        when(productRepository.countByCategoryIdAndStatus(2L, "ACTIVE")).thenReturn(3L);

        AppException ex = assertThrows(AppException.class, () -> categoryService.deleteCategory(2L));

        assertEquals(ErrorCode.CATEGORY_NOT_EMPTY, ex.getErrorCode());
    }

    @Test
    @DisplayName("K07: Xóa danh mục rỗng thành công (xóa mềm) và xóa cache cây")
    void deleteEmptyCategory_SoftDeletes() {
        Category c = category(2L, "Bàn phím", 1L);
        when(categoryRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(c));
        when(categoryRepository.findByParentIdAndDeletedFalse(2L)).thenReturn(List.of());
        when(productRepository.countByCategoryIdAndStatus(2L, "ACTIVE")).thenReturn(0L);

        categoryService.deleteCategory(2L);

        assertTrue(c.isDeleted());
        verify(categoryRepository).save(c);
        verify(cacheService).evictAfterCommit(CategoryService.TREE_CACHE_KEY);
    }

    @Test
    @DisplayName("Xóa danh mục không tồn tại -> CATEGORY_NOT_FOUND")
    void deleteMissingCategory_ThrowsNotFound() {
        when(categoryRepository.findByIdAndDeletedFalse(404L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> categoryService.deleteCategory(404L));

        assertEquals(ErrorCode.CATEGORY_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("toSlug: bỏ dấu tiếng Việt, chuyển đ -> d, ký tự lạ thành gạch ngang")
    void toSlug_HandlesVietnamese() {
        assertEquals("dien-thoai-may-tinh", CategoryService.toSlug("Điện Thoại & Máy Tính"));
        assertEquals("do-choi-tre-em", CategoryService.toSlug("  Đồ chơi   trẻ em!! "));
        assertEquals("category", CategoryService.toSlug("###"));
    }
}
