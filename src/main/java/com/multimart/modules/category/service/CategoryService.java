package com.multimart.modules.category.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.multimart.common.cache.RedisCacheService;
import com.multimart.common.exception.AppException;
import com.multimart.common.exception.ErrorCode;
import com.multimart.modules.category.dto.CategoryResponse;
import com.multimart.modules.category.dto.CreateCategoryRequest;
import com.multimart.modules.category.dto.UpdateCategoryRequest;
import com.multimart.modules.category.entity.Category;
import com.multimart.modules.category.repository.CategoryRepository;
import com.multimart.modules.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dịch vụ nghiệp vụ danh mục sản phẩm: cây 2 cấp, CRUD cho Admin và cây danh mục có cache 1 giờ.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    /** Khóa cache cây danh mục. */
    public static final String TREE_CACHE_KEY = "cache:category:tree";

    private static final Duration TREE_TTL = Duration.ofHours(1);

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final RedisCacheService cacheService;

    /**
     * Lấy cây danh mục (gốc -> con) kèm số sản phẩm ACTIVE, có cache 1 giờ.
     * Danh mục cha cộng dồn số sản phẩm của các danh mục con.
     *
     * @return danh sách danh mục gốc, mỗi nút chứa danh mục con
     */
    public List<CategoryResponse> getCategoryTree() {
        return cacheService.getOrLoad(TREE_CACHE_KEY, TREE_TTL,
                new TypeReference<List<CategoryResponse>>() {}, this::buildTree);
    }

    /**
     * Tạo danh mục mới (Admin).
     *
     * @param request dữ liệu danh mục
     * @return danh mục vừa tạo
     * @throws AppException CATEGORY_NOT_FOUND (cha không tồn tại), CATEGORY_DEPTH_EXCEEDED (vượt 2 cấp),
     *                      CATEGORY_NAME_DUPLICATED (trùng tên trong cùng cha)
     */
    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        String name = request.getName().trim();
        if (name.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Tên danh mục không được để trống");
        }
        Long parentId = request.getParentId();
        if (parentId != null) {
            Category parent = categoryRepository.findByIdAndDeletedFalse(parentId)
                    .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND, "Không tìm thấy danh mục cha: " + parentId));
            if (parent.getParentId() != null) {
                throw new AppException(ErrorCode.CATEGORY_DEPTH_EXCEEDED);
            }
        }
        if (isDuplicatedName(parentId, name)) {
            throw new AppException(ErrorCode.CATEGORY_NAME_DUPLICATED);
        }

        Category saved = categoryRepository.save(Category.builder()
                .name(name)
                .slug(generateUniqueSlug(name))
                .parentId(parentId)
                .sortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder())
                .build());
        cacheService.evictAfterCommit(TREE_CACHE_KEY);
        log.info("Created category: id={}, name={}, parentId={}", saved.getId(), saved.getName(), parentId);
        return toResponse(saved, 0);
    }

    /**
     * Cập nhật danh mục (Admin). Không đổi danh mục cha.
     *
     * @param id      id danh mục
     * @param request dữ liệu cập nhật
     * @return danh mục sau cập nhật
     * @throws AppException CATEGORY_NOT_FOUND hoặc CATEGORY_NAME_DUPLICATED
     */
    @Transactional
    public CategoryResponse updateCategory(Long id, UpdateCategoryRequest request) {
        Category category = categoryRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
        String name = request.getName().trim();
        if (name.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Tên danh mục không được để trống");
        }
        // Chỉ kiểm tra trùng khi tên thật sự đổi (đổi mỗi hoa/thường thì bỏ qua)
        if (!name.equalsIgnoreCase(category.getName()) && isDuplicatedName(category.getParentId(), name)) {
            throw new AppException(ErrorCode.CATEGORY_NAME_DUPLICATED);
        }
        category.setName(name);
        if (request.getSortOrder() != null) {
            category.setSortOrder(request.getSortOrder());
        }
        if (StringUtils.hasText(request.getStatus())) {
            String status = request.getStatus().trim().toUpperCase(Locale.ROOT);
            if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
                throw new AppException(ErrorCode.INVALID_REQUEST, "Trạng thái danh mục phải là ACTIVE hoặc INACTIVE");
            }
            category.setStatus(status);
        }
        Category saved = categoryRepository.save(category);
        cacheService.evictAfterCommit(TREE_CACHE_KEY);
        log.info("Updated category: id={}", id);
        return toResponse(saved, productRepository.countByCategoryIdAndStatus(id, "ACTIVE"));
    }

    /**
     * Xóa mềm danh mục (Admin). Không xóa khi còn danh mục con hoặc còn sản phẩm đang bán.
     *
     * @param id id danh mục
     * @throws AppException CATEGORY_NOT_FOUND hoặc CATEGORY_NOT_EMPTY
     */
    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
        boolean hasChildren = !categoryRepository.findByParentIdAndDeletedFalse(id).isEmpty();
        boolean hasProducts = productRepository.countByCategoryIdAndStatus(id, "ACTIVE") > 0;
        if (hasChildren || hasProducts) {
            throw new AppException(ErrorCode.CATEGORY_NOT_EMPTY);
        }
        category.setDeleted(true);
        categoryRepository.save(category);
        cacheService.evictAfterCommit(TREE_CACHE_KEY);
        log.info("Soft-deleted category: id={}", id);
    }

    /** Dựng cây danh mục từ DB bằng 2 truy vấn (danh mục + đếm sản phẩm). */
    private List<CategoryResponse> buildTree() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : productRepository.countActiveGroupByCategory()) {
            counts.put((Long) row[0], (Long) row[1]);
        }

        Map<Long, CategoryResponse> nodes = new HashMap<>();
        List<Category> all = categoryRepository.findByDeletedFalseOrderBySortOrderAscNameAsc();
        for (Category c : all) {
            nodes.put(c.getId(), toResponse(c, counts.getOrDefault(c.getId(), 0L)));
        }

        List<CategoryResponse> roots = new ArrayList<>();
        for (Category c : all) {
            CategoryResponse node = nodes.get(c.getId());
            CategoryResponse parent = c.getParentId() == null ? null : nodes.get(c.getParentId());
            if (parent != null) {
                parent.getChildren().add(node);
                // Danh mục cha cộng dồn số sản phẩm của con
                parent.setProductCount(parent.getProductCount() + node.getProductCount());
            } else if (c.getParentId() == null) {
                roots.add(node);
            }
        }
        return roots;
    }

    /** Kiểm tra trùng tên trong cùng danh mục cha (cha null = nhóm danh mục gốc). */
    private boolean isDuplicatedName(Long parentId, String name) {
        return parentId == null
                ? categoryRepository.existsByParentIdIsNullAndNameIgnoreCaseAndDeletedFalse(name)
                : categoryRepository.existsByParentIdAndNameIgnoreCaseAndDeletedFalse(parentId, name);
    }

    /** Sinh slug duy nhất: bỏ dấu tiếng Việt, chữ thường, nối bằng gạch ngang; trùng thì thêm hậu tố số. */
    private String generateUniqueSlug(String name) {
        String base = toSlug(name);
        String slug = base;
        int suffix = 2;
        while (categoryRepository.existsBySlug(slug)) {
            slug = base + "-" + suffix++;
        }
        return slug;
    }

    /**
     * Chuyển tên thành slug không dấu.
     *
     * @param name tên danh mục
     * @return slug dạng chữ thường, nối bằng gạch ngang (tối đa 110 ký tự)
     */
    static String toSlug(String name) {
        String normalized = Normalizer.normalize(name.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (normalized.isEmpty()) {
            normalized = "category";
        }
        return normalized.length() > 110 ? normalized.substring(0, 110) : normalized;
    }

    /** Chuyển Entity sang DTO. */
    private CategoryResponse toResponse(Category category, long productCount) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .parentId(category.getParentId())
                .status(category.getStatus())
                .productCount(productCount)
                .build();
    }
}
