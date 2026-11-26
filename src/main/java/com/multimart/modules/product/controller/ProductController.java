package com.multimart.modules.product.controller;

import com.multimart.common.response.ApiResponse;
import com.multimart.common.response.PageResponse;
import com.multimart.modules.product.dto.CreateProductRequest;
import com.multimart.modules.product.dto.ProductResponse;
import com.multimart.modules.product.dto.ProductSearchCriteria;
import com.multimart.modules.product.dto.UpdateProductRequest;
import com.multimart.modules.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller quản lý sản phẩm:
 * - Public: Cho phép khách xem danh sách và chi tiết sản phẩm.
 * - Admin: Bảo vệ quyền quản trị (@PreAuthorize("hasRole('ADMIN')")) cho các thao tác Thêm, Sửa, Xóa.
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * API Tìm kiếm, lọc và phân trang sản phẩm đang bán.
     * Mở công khai cho mọi đối tượng truy cập. Tham số cũ (search, sortBy, direction) vẫn được hỗ trợ.
     *
     * @param keyword    từ khóa trong tên sản phẩm (2-100 ký tự)
     * @param search     tham số cũ, tương đương keyword
     * @param categoryId lọc theo danh mục (gồm cả danh mục con)
     * @param minPrice   giá tối thiểu
     * @param maxPrice   giá tối đa
     * @param inStock    true để chỉ lấy sản phẩm còn hàng
     * @param sort       newest | priceAsc | priceDesc | nameAsc
     * @param page       vị trí trang (mặc định 0)
     * @param size       kích thước trang (mặc định 10, tối đa 50)
     * @param sortBy     tham số cũ: trường sắp xếp (createdAt, originalPrice, name)
     * @param direction  tham số cũ: chiều sắp xếp asc/desc
     * @return HTTP 200 OK cùng danh sách sản phẩm phân trang
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getAllProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction
    ) {
        ProductSearchCriteria criteria = ProductSearchCriteria.of(
                keyword != null ? keyword : search, categoryId, minPrice, maxPrice, inStock,
                sort, sortBy, direction, page, size);
        PageResponse<ProductResponse> response = productService.searchProducts(criteria);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * API Lấy thông tin chi tiết một sản phẩm theo ID.
     * Mở công khai cho mọi đối tượng truy cập.
     *
     * @param id mã ID sản phẩm
     * @return HTTP 200 OK cùng thông tin chi tiết sản phẩm
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable Long id) {
        ProductResponse response = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * API Tạo sản phẩm mới vào kho hàng gốc.
     * Yêu cầu quyền ROLE_ADMIN.
     *
     * @param request dữ liệu sản phẩm mới (name, originalPrice, totalStock)
     * @return HTTP 201 Created cùng thông tin sản phẩm đã tạo
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse response = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo sản phẩm thành công", response));
    }

    /**
     * API Cập nhật thông tin sản phẩm đã có.
     * Yêu cầu quyền ROLE_ADMIN.
     *
     * @param id      mã sản phẩm cần cập nhật
     * @param request dữ liệu cập nhật
     * @return HTTP 200 OK cùng thông tin sản phẩm sau khi cập nhật
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        ProductResponse response = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật sản phẩm thành công", response));
    }

    /**
     * API Xóa mềm sản phẩm (chuyển trạng thái sang INACTIVE).
     * Yêu cầu quyền ROLE_ADMIN.
     *
     * @param id mã sản phẩm cần xóa
     * @return HTTP 200 OK thông báo xóa thành công
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa sản phẩm thành công", null));
    }
}
