package com.multimart.modules.review.controller;

import com.multimart.common.response.ApiResponse;
import com.multimart.common.response.PageResponse;
import com.multimart.modules.review.dto.CreateReviewRequest;
import com.multimart.modules.review.dto.ReviewResponse;
import com.multimart.modules.review.dto.ReviewSummaryResponse;
import com.multimart.modules.review.dto.UpdateReviewRequest;
import com.multimart.modules.review.service.ReviewService;
import com.multimart.modules.user.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API đánh giá sản phẩm dành cho khách và người dùng đã đăng nhập.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final CurrentUserService currentUserService;

    /**
     * Gửi đánh giá cho sản phẩm thuộc đơn hàng đã giao.
     *
     * @param productId   id sản phẩm
     * @param request     dữ liệu đánh giá
     * @param userDetails người dùng đăng nhập
     * @return HTTP 201 Created cùng đánh giá vừa tạo
     */
    @PostMapping("/products/{productId}/reviews")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @PathVariable Long productId, @Valid @RequestBody CreateReviewRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserService.getUserId(userDetails);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Gửi đánh giá thành công",
                        reviewService.createReview(userId, productId, request)));
    }

    /**
     * Danh sách đánh giá đang hiển thị của một sản phẩm (công khai).
     *
     * @param productId id sản phẩm
     * @param rating    lọc theo số sao (tùy chọn)
     * @param sort      newest, ratingDesc hoặc ratingAsc
     * @param page      số trang (từ 0)
     * @param size      kích thước trang (mặc định 10, tối đa 50)
     * @return HTTP 200 OK cùng trang đánh giá
     */
    @GetMapping("/products/{productId}/reviews")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> getProductReviews(
            @PathVariable Long productId,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đánh giá thành công",
                reviewService.getProductReviews(productId, rating, sort, page, size)));
    }

    /**
     * Thống kê đánh giá của một sản phẩm (công khai).
     *
     * @param productId id sản phẩm
     * @return HTTP 200 OK cùng thống kê
     */
    @GetMapping("/products/{productId}/reviews/summary")
    public ResponseEntity<ApiResponse<ReviewSummaryResponse>> getSummary(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success("Lấy thống kê đánh giá thành công",
                reviewService.getSummary(productId)));
    }

    /**
     * Danh sách đánh giá của tôi.
     *
     * @param page        số trang (từ 0)
     * @param size        kích thước trang (mặc định 10, tối đa 50)
     * @param userDetails người dùng đăng nhập
     * @return HTTP 200 OK cùng trang đánh giá
     */
    @GetMapping("/reviews/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> getMyReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserService.getUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success("Lấy đánh giá của tôi thành công",
                reviewService.getMyReviews(userId, page, size)));
    }

    /**
     * Sửa đánh giá của chính mình.
     *
     * @param id          id đánh giá
     * @param request     dữ liệu cần sửa
     * @param userDetails người dùng đăng nhập
     * @return HTTP 200 OK cùng đánh giá sau khi sửa
     */
    @PutMapping("/reviews/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ReviewResponse>> updateReview(
            @PathVariable Long id, @Valid @RequestBody UpdateReviewRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserService.getUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật đánh giá thành công",
                reviewService.updateReview(userId, id, request)));
    }

    /**
     * Xóa đánh giá của chính mình.
     *
     * @param id          id đánh giá
     * @param userDetails người dùng đăng nhập
     * @return HTTP 200 OK
     */
    @DeleteMapping("/reviews/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteReview(
            @PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserService.getUserId(userDetails);
        reviewService.deleteReview(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Xóa đánh giá thành công", null));
    }
}
