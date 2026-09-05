package com.multimart.modules.flashsale.controller;

import com.multimart.common.exception.AppException;
import com.multimart.common.exception.ErrorCode;
import com.multimart.common.response.ApiResponse;
import com.multimart.common.response.PageResponse;
import com.multimart.modules.flashsale.dto.FlashSaleOrderRequest;
import com.multimart.modules.flashsale.dto.FlashSaleOrderResponse;
import com.multimart.modules.flashsale.service.FlashSaleEngineService;
import com.multimart.modules.user.entity.User;
import com.multimart.modules.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Controller tiếp nhận yêu cầu đặt mua sản phẩm trong các đợt Flash Sale tốc độ cao.
 * Yêu cầu người dùng phải đăng nhập để xác định danh tính và giới hạn số lượt mua.
 */
@RestController
@RequestMapping("/api/v1/flash-sales/orders")
@RequiredArgsConstructor
public class FlashSaleOrderController {

    private final FlashSaleEngineService flashSaleEngineService;
    private final UserRepository userRepository;

    /**
     * API Đặt mua sản phẩm Flash Sale với khả năng chịu tải hàng trăm nghìn RPS.
     * Sử dụng Redis Lua Script trừ kho nguyên tử trong RAM trước khi lưu đơn hàng.
     *
     * @param request     Dữ liệu đặt mua (eventId, productId, quantity)
     * @param userDetails Thông tin tài khoản người dùng đăng nhập
     * @return HTTP 201 Created cùng thông tin đơn hàng Flash Sale đã tạo
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<FlashSaleOrderResponse>> placeFlashSaleOrder(
            @Valid @RequestBody FlashSaleOrderRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        FlashSaleOrderResponse response = flashSaleEngineService.placeOrder(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đặt mua sản phẩm Flash-Sale thành công", response));
    }

    /**
     * API Lấy danh sách lịch sử đơn hàng Flash Sale của người dùng đang đăng nhập.
     *
     * @param page        Số trang (mặc định 0)
     * @param size        Kích thước trang (mặc định 10)
     * @param userDetails Thông tin tài khoản đăng nhập
     * @return HTTP 200 OK cùng danh sách đơn hàng phân trang
     */
    @GetMapping("/my-orders")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<FlashSaleOrderResponse>>> getMyOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        PageResponse<FlashSaleOrderResponse> response = flashSaleEngineService.getMyOrders(user.getId(), page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử đơn hàng Flash-Sale thành công", response));
    }
}
