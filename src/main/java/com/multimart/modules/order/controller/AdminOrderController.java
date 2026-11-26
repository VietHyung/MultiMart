package com.multimart.modules.order.controller;

import com.multimart.common.response.ApiResponse;
import com.multimart.common.response.PageResponse;
import com.multimart.modules.order.dto.OrderResponse;
import com.multimart.modules.order.dto.OrderSummaryResponse;
import com.multimart.modules.order.dto.UpdateOrderStatusRequest;
import com.multimart.modules.order.entity.OrderStatus;
import com.multimart.modules.order.service.OrderService;
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
 * API quản trị đơn hàng thường (chỉ ADMIN).
 */
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;

    /**
     * Danh sách mọi đơn hàng, mới nhất trước.
     *
     * @param status trạng thái cần lọc (tùy chọn)
     * @param page   trang (mặc định 0)
     * @param size   kích thước trang (mặc định 10, tối đa 50)
     * @return HTTP 200 OK cùng trang đơn hàng
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<OrderSummaryResponse>>> getAllOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đơn hàng thành công",
                orderService.getAllOrders(status, page, size)));
    }

    /**
     * Chuyển trạng thái đơn hàng.
     *
     * @param id      id đơn hàng
     * @param request trạng thái đích
     * @return HTTP 200 OK cùng đơn hàng sau cập nhật
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
            @PathVariable Long id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái đơn hàng thành công",
                orderService.updateStatusByAdmin(id, request.getStatus())));
    }
}
