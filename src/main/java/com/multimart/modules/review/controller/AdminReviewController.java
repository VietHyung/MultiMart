package com.multimart.modules.review.controller;

import com.multimart.common.response.ApiResponse;
import com.multimart.common.response.PageResponse;
import com.multimart.modules.review.dto.ReviewResponse;
import com.multimart.modules.review.dto.UpdateReviewVisibilityRequest;
import com.multimart.modules.review.entity.ReviewStatus;
import com.multimart.modules.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API kiểm duyệt đánh giá (chỉ ADMIN): xem danh sách, ẩn hoặc hiện đánh giá.
 */
@RestController
@RequestMapping("/api/v1/admin/reviews")
@RequiredArgsConstructor
public class AdminReviewController {

    private final ReviewService reviewService;

    /**
     * Danh sách đánh giá để kiểm duyệt.
     *
     * @param status lọc theo trạng thái (VISIBLE hoặc HIDDEN, tùy chọn)
     * @param page   số trang (từ 0)
     * @param size   kích thước trang (mặc định 10, tối đa 50)
     * @return HTTP 200 OK cùng trang đánh giá
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> getAllReviews(
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đánh giá thành công",
                reviewService.getAllReviews(status, page, size)));
    }

    /**
     * Ẩn hoặc hiện một đánh giá.
     *
     * @param id      id đánh giá
     * @param request trạng thái hiển thị mong muốn
     * @return HTTP 200 OK cùng đánh giá sau khi đổi trạng thái
     */
    @PutMapping("/{id}/visibility")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ReviewResponse>> updateVisibility(
            @PathVariable Long id, @Valid @RequestBody UpdateReviewVisibilityRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật hiển thị đánh giá thành công",
                reviewService.setVisibility(id, request.getVisible())));
    }
}
