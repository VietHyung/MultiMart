package com.multimart.modules.product.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.multimart.common.cache.RedisCacheService;
import com.multimart.common.exception.AppException;
import com.multimart.common.exception.ErrorCode;
import com.multimart.common.response.PageResponse;
import com.multimart.modules.category.entity.Category;
import com.multimart.modules.category.repository.CategoryRepository;
import com.multimart.modules.category.service.CategoryService;
import com.multimart.modules.product.dto.CreateProductRequest;
import com.multimart.modules.product.dto.ProductResponse;
import com.multimart.modules.product.dto.ProductSearchCriteria;
import com.multimart.modules.product.dto.UpdateProductRequest;
import com.multimart.modules.product.entity.Product;
import com.multimart.modules.product.repository.ProductRepository;
import com.multimart.modules.product.repository.ProductSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dịch vụ nghiệp vụ sản phẩm: CRUD, tìm kiếm/lọc theo danh mục và cache hiển thị.
 * Lưu ý: cache chỉ phục vụ hiển thị; đặt hàng luôn đọc giá và tồn kho trực tiếp từ DB.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    /** Tiền tố khóa cache chi tiết sản phẩm. */
    public static final String DETAIL_CACHE_PREFIX = "cache:product:detail:";

    /** Tiền tố khóa cache danh sách sản phẩm. */
    public static final String LIST_CACHE_PREFIX = "cache:product:list:";

    private static final Duration DETAIL_TTL = Duration.ofMinutes(10);
    private static final Duration LIST_TTL = Duration.ofSeconds(60);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final RedisCacheService cacheService;

    /**
     * Tạo sản phẩm mới (Admin).
     *
     * @param request dữ liệu sản phẩm cần tạo
     * @return thông tin sản phẩm vừa tạo
     * @throws AppException CATEGORY_NOT_FOUND khi categoryId không hợp lệ
     */
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        validateCategory(request.getCategoryId());
        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .originalPrice(request.getOriginalPrice())
                .totalStock(request.getTotalStock())
                .categoryId(request.getCategoryId())
                .status("ACTIVE")
                .build();

        Product savedProduct = productRepository.save(product);
        cacheService.evictAfterCommit(CategoryService.TREE_CACHE_KEY);
        log.info("Created product successfully: id={}, name={}", savedProduct.getId(), savedProduct.getName());
        return toResponse(savedProduct, categoryNameOf(savedProduct.getCategoryId()));
    }

    /**
     * Cập nhật sản phẩm (Admin). categoryId = null nghĩa là bỏ phân loại danh mục.
     *
     * @param id      id sản phẩm
     * @param request dữ liệu cập nhật
     * @return thông tin sản phẩm sau cập nhật
     * @throws AppException PRODUCT_NOT_FOUND hoặc CATEGORY_NOT_FOUND
     */
    @Transactional
    public ProductResponse updateProduct(Long id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND, "Không tìm thấy sản phẩm với id: " + id));
        validateCategory(request.getCategoryId());

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setImageUrl(request.getImageUrl());
        product.setOriginalPrice(request.getOriginalPrice());
        product.setTotalStock(request.getTotalStock());
        product.setCategoryId(request.getCategoryId());
        if (StringUtils.hasText(request.getStatus())) {
            product.setStatus(request.getStatus());
        }

        Product updatedProduct = productRepository.save(product);
        cacheService.evictAfterCommit(DETAIL_CACHE_PREFIX + id, CategoryService.TREE_CACHE_KEY);
        log.info("Updated product successfully: id={}", updatedProduct.getId());
        return toResponse(updatedProduct, categoryNameOf(updatedProduct.getCategoryId()));
    }

    /**
     * Lấy chi tiết sản phẩm đang bán (ACTIVE), có cache 10 phút.
     *
     * @param id id sản phẩm
     * @return thông tin sản phẩm kèm danh mục
     * @throws AppException PRODUCT_NOT_FOUND khi không tồn tại hoặc không còn bán
     */
    public ProductResponse getProductById(Long id) {
        // Không đặt @Transactional ở đây để cache hit không phải giữ kết nối DB
        ProductResponse response = cacheService.getOrLoad(
                DETAIL_CACHE_PREFIX + id, DETAIL_TTL, new TypeReference<ProductResponse>() {},
                () -> productRepository.findByIdAndStatus(id, "ACTIVE")
                        .map(p -> toResponse(p, categoryNameOf(p.getCategoryId())))
                        .orElse(null));
        if (response == null) {
            throw new AppException(ErrorCode.PRODUCT_NOT_FOUND, "Không tìm thấy sản phẩm với id: " + id);
        }
        return response;
    }

    /**
     * Tìm kiếm, lọc và phân trang sản phẩm ACTIVE, có cache danh sách 60 giây.
     *
     * @param criteria tiêu chí đã chuẩn hóa
     * @return trang sản phẩm
     * @throws AppException CATEGORY_NOT_FOUND khi lọc theo danh mục không tồn tại
     */
    public PageResponse<ProductResponse> searchProducts(ProductSearchCriteria criteria) {
        String key = LIST_CACHE_PREFIX + DigestUtils.md5DigestAsHex(
                criteria.cacheFingerprint().getBytes(StandardCharsets.UTF_8));
        return cacheService.getOrLoad(key, LIST_TTL,
                new TypeReference<PageResponse<ProductResponse>>() {}, () -> loadSearchPage(criteria));
    }

    /**
     * Xóa mềm sản phẩm (chuyển sang INACTIVE).
     *
     * @param id id sản phẩm
     * @throws AppException PRODUCT_NOT_FOUND khi không tồn tại
     */
    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND, "Không tìm thấy sản phẩm với id: " + id));

        // Soft delete: chuyển trạng thái sang INACTIVE
        product.setStatus("INACTIVE");
        productRepository.save(product);
        cacheService.evictAfterCommit(DETAIL_CACHE_PREFIX + id, CategoryService.TREE_CACHE_KEY);
        log.info("Soft-deleted product: id={}", id);
    }

    /** Nạp một trang kết quả tìm kiếm từ DB (dùng khi cache miss). */
    private PageResponse<ProductResponse> loadSearchPage(ProductSearchCriteria criteria) {
        List<Long> categoryIds = null;
        if (criteria.categoryId() != null) {
            Category category = categoryRepository.findByIdAndDeletedFalse(criteria.categoryId())
                    .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
            List<Long> ids = new ArrayList<>();
            ids.add(category.getId());
            // Danh mục cha bao gồm cả sản phẩm của danh mục con (BR-K5)
            categoryRepository.findByParentIdAndDeletedFalse(category.getId())
                    .forEach(child -> ids.add(child.getId()));
            categoryIds = ids;
        }

        Pageable pageable = PageRequest.of(criteria.page(), criteria.size(), criteria.sort().toSort());
        Page<Product> page = productRepository.findAll(
                ProductSpecifications.search(criteria.keyword(), categoryIds, criteria.minPrice(),
                        criteria.maxPrice(), criteria.inStock()), pageable);

        Map<Long, String> categoryNames = loadCategoryNames(page.getContent());
        return PageResponse.from(page.map(p -> toResponse(p, categoryNames.get(p.getCategoryId()))));
    }

    /** Tải tên danh mục cho cả trang bằng một truy vấn (tránh N+1). */
    private Map<Long, String> loadCategoryNames(List<Product> products) {
        Set<Long> ids = new HashSet<>();
        products.forEach(p -> {
            if (p.getCategoryId() != null) {
                ids.add(p.getCategoryId());
            }
        });
        Map<Long, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            categoryRepository.findAllById(ids).forEach(c -> names.put(c.getId(), c.getName()));
        }
        return names;
    }

    /** Kiểm tra danh mục được gán cho sản phẩm tồn tại và đang ACTIVE (BR-K4). */
    private void validateCategory(Long categoryId) {
        if (categoryId == null) {
            return;
        }
        Category category = categoryRepository.findByIdAndDeletedFalse(categoryId)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
        if (!"ACTIVE".equals(category.getStatus())) {
            throw new AppException(ErrorCode.CATEGORY_NOT_FOUND, "Danh mục không còn hoạt động");
        }
    }

    /** Lấy tên danh mục theo id; null nếu sản phẩm chưa phân loại. */
    private String categoryNameOf(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId).map(Category::getName).orElse(null);
    }

    /** Chuyển Entity sang DTO phản hồi. */
    private ProductResponse toResponse(Product product, String categoryName) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .imageUrl(product.getImageUrl())
                .originalPrice(product.getOriginalPrice())
                .totalStock(product.getTotalStock())
                .status(product.getStatus())
                .categoryId(product.getCategoryId())
                .categoryName(categoryName)
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
