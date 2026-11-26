package com.multimart.modules.order.controller;

import com.multimart.common.response.ApiResponse;
import com.multimart.common.response.PageResponse;
import com.multimart.modules.order.dto.CancelOrderRequest;
import com.multimart.modules.order.dto.CheckoutRequest;
import com.multimart.modules.order.dto.CheckoutResult;
import com.multimart.modules.order.dto.OrderResponse;
import com.multimart.modules.order.dto.OrderSummaryResponse;
import com.multimart.modules.order.entity.OrderStatus;
import com.multimart.modules.order.service.OrderService;
import com.multimart.modules.user.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API đơn hàng thường dành cho khách hàng đang đăng nhập.
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final CurrentUserService currentUserService;

    /**
     * Đặt hàng từ giỏ. Bắt buộc header Idempotency-Key.
     *
     * @param request        thông tin nhận hàng và thanh toán
     * @param idempotencyKey khóa chống đặt trùng
     * @param userDetails    người dùng đăng nhập
     * @return HTTP 201 Created với đơn mới, hoặc HTTP 200 OK với đơn cũ nếu trùng Idempotency-Key
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderResponse>> checkout(
            @Valid @RequestBody CheckoutRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserService.getUserId(userDetails);
        CheckoutResult result = orderService.checkout(userId, request, idempotencyKey);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.success("Đặt hàng thành công", result.order()));
    }

    /**
     * Thanh toán giả lập đơn ONLINE.
     *
     * @param id          id đơn hàng
     * @param userDetails người dùng đăng nhập
     * @return HTTP 200 OK cùng đơn hàng đã xác nhận
     */
    @PostMapping("/{id}/pay")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderResponse>> pay(
            @PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserService.getUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success("Thanh toán đơn hàng thành công", orderService.payOrder(userId, id)));
    }

    /**
     * Hủy đơn hàng.
     *
     * @param id          id đơn hàng
     * @param request     lý do hủy (tùy chọn)
     * @param userDetails người dùng đăng nhập
     * @return HTTP 200 OK cùng đơn hàng đã hủy
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderResponse>> cancel(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) CancelOrderRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserService.getUserId(userDetails);
        String reason = request == null ? null : request.getReason();
        return ResponseEntity.ok(ApiResponse.success("Hủy đơn hàng thành công", orderService.cancelOrder(userId, id, reason)));
    }

    /**
     * Danh sách đơn hàng của tôi, mới nhất trước.
     *
     * @param status      trạng thái cần lọc (tùy chọn)
     * @param page        trang (mặc định 0)
     * @param size        kích thước trang (mặc định 10, tối đa 50)
     * @param userDetails người dùng đăng nhập
     * @return HTTP 200 OK cùng trang đơn hàng
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<OrderSummaryResponse>>> getMyOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserService.getUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đơn hàng thành công",
                orderService.getMyOrders(userId, status, page, size)));
    }

    /**
     * Chi tiết một đơn hàng của tôi.
     *
     * @param id          id đơn hàng
     * @param userDetails người dùng đăng nhập
     * @return HTTP 200 OK cùng chi tiết đơn hàng
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderResponse>> getMyOrder(
            @PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserService.getUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết đơn hàng thành công", orderService.getMyOrder(userId, id)));
    }
}
