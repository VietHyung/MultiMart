package com.multimart.modules.product.service;

import com.multimart.common.exception.AppException;
import com.multimart.common.exception.ErrorCode;
import com.multimart.common.response.PageResponse;
import com.multimart.modules.product.dto.CreateProductRequest;
import com.multimart.modules.product.dto.ProductResponse;
import com.multimart.modules.product.dto.UpdateProductRequest;
import com.multimart.modules.product.entity.Product;
import com.multimart.modules.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Service xử lý toàn bộ logic nghiệp vụ quản lý sản phẩm gốc:
 * Thêm mới, Chỉnh sửa, Lấy chi tiết, Tìm kiếm phân trang và Xóa mềm (Soft Delete).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * Tạo sản phẩm mới vào kho hàng gốc với trạng thái mặc định ACTIVE.
     *
     * @param request thông tin tạo sản phẩm (tên, giá gốc, tồn kho ban đầu)
     * @return ProductResponse chứa thông tin sản phẩm vừa tạo kèm ID và ngày tạo
     */
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .originalPrice(request.getOriginalPrice())
                .totalStock(request.getTotalStock())
                .status("ACTIVE")
                .build();

        Product savedProduct = productRepository.save(product);
        log.info("Created product successfully: id={}, name={}", savedProduct.getId(), savedProduct.getName());

        return mapToProductResponse(savedProduct);
    }

    /**
     * Cập nhật thông tin chi tiết của một sản phẩm đã có trong cơ sở dữ liệu.
     *
     * @param id      mã sản phẩm cần cập nhật
     * @param request dữ liệu mới cần cập nhật
     * @return ProductResponse chứa dữ liệu sau khi cập nhật
     */
    @Transactional
    public ProductResponse updateProduct(Long id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND, "Không tìm thấy sản phẩm với id: " + id));

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setImageUrl(request.getImageUrl());
        product.setOriginalPrice(request.getOriginalPrice());
        product.setTotalStock(request.getTotalStock());
        if (StringUtils.hasText(request.getStatus())) {
            product.setStatus(request.getStatus());
        }

        Product updatedProduct = productRepository.save(product);
        log.info("Updated product successfully: id={}", updatedProduct.getId());

        return mapToProductResponse(updatedProduct);
    }

    /**
     * Lấy thông tin chi tiết của một sản phẩm đang ở trạng thái ACTIVE.
     *
     * @param id mã sản phẩm cần tìm
     * @return ProductResponse thông tin chi tiết sản phẩm
     */
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findByIdAndStatus(id, "ACTIVE")
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND, "Không tìm thấy sản phẩm với id: " + id));

        return mapToProductResponse(product);
    }

    /**
     * Lấy danh sách sản phẩm có hỗ trợ tìm kiếm từ khóa theo tên, phân trang và sắp xếp động.
     *
     * @param search    từ khóa tìm kiếm (có thể null hoặc rỗng)
     * @param page      số thứ tự trang (0-indexed)
     * @param size      số lượng sản phẩm trên mỗi trang
     * @param sortBy    tên trường cần sắp xếp (mặc định: createdAt)
     * @param direction hướng sắp xếp (asc hoặc desc)
     * @return PageResponse bọc danh sách ProductResponse và metadata phân trang
     */
    public PageResponse<ProductResponse> getAllProducts(
            String search,
            int page,
            int size,
            String sortBy,
            String direction
    ) {
        Sort sort = direction.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Product> productPage;
        if (StringUtils.hasText(search)) {
            productPage = productRepository.findByNameContainingIgnoreCaseAndStatus(search.trim(), "ACTIVE", pageable);
        } else {
            productPage = productRepository.findByStatus("ACTIVE", pageable);
        }

        return PageResponse.from(productPage.map(this::mapToProductResponse));
    }

    /**
     * Thực hiện xóa mềm (Soft Delete) sản phẩm bằng cách chuyển trạng thái sang INACTIVE,
     * giữ nguyên tính toàn vẹn dữ liệu cho các đơn hàng lịch sử.
     *
     * @param id mã sản phẩm cần xóa
     */
    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND, "Không tìm thấy sản phẩm với id: " + id));

        // Soft delete: chuyển trạng thái sang INACTIVE
        product.setStatus("INACTIVE");
        productRepository.save(product);
        log.info("Soft-deleted product: id={}", id);
    }

    /**
     * Helper chuyển đổi thực thể Product sang DTO ProductResponse.
     *
     * @param product đối tượng thực thể Product
     * @return DTO ProductResponse
     */
    private ProductResponse mapToProductResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .imageUrl(product.getImageUrl())
                .originalPrice(product.getOriginalPrice())
                .totalStock(product.getTotalStock())
                .status(product.getStatus())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
