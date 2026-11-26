package com.multimart.modules.category.controller;

import com.multimart.common.response.ApiResponse;
import com.multimart.modules.category.dto.CategoryResponse;
import com.multimart.modules.category.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API công khai xem cây danh mục sản phẩm.
 */
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * Lấy cây danh mục (gốc -> con) kèm số sản phẩm đang bán. Mở công khai.
     *
     * @return HTTP 200 OK cùng cây danh mục
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategoryTree() {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh mục thành công", categoryService.getCategoryTree()));
    }
}
