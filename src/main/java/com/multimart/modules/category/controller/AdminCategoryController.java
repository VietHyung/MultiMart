package com.multimart.modules.category.controller;

import com.multimart.common.response.ApiResponse;
import com.multimart.modules.category.dto.CategoryResponse;
import com.multimart.modules.category.dto.CreateCategoryRequest;
import com.multimart.modules.category.dto.UpdateCategoryRequest;
import com.multimart.modules.category.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API quản trị danh mục sản phẩm (chỉ ADMIN).
 */
@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    /**
     * Tạo danh mục mới.
     *
     * @param request dữ liệu danh mục
     * @return HTTP 201 Created cùng danh mục vừa tạo
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo danh mục thành công", categoryService.createCategory(request)));
    }

    /**
     * Cập nhật danh mục.
     *
     * @param id      id danh mục
     * @param request dữ liệu cập nhật
     * @return HTTP 200 OK cùng danh mục sau cập nhật
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable Long id, @Valid @RequestBody UpdateCategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật danh mục thành công",
                categoryService.updateCategory(id, request)));
    }

    /**
     * Xóa mềm danh mục.
     *
     * @param id id danh mục
     * @return HTTP 200 OK
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa danh mục thành công", null));
    }
}
