package com.multimart.modules.flashsale.controller;

import com.multimart.common.exception.AppException;
import com.multimart.common.exception.ErrorCode;
import com.multimart.common.response.ApiResponse;
import com.multimart.common.response.PageResponse;
import com.multimart.modules.flashsale.dto.AsyncOrderSubmitResponse;
import com.multimart.modules.flashsale.dto.FlashSaleOrderRequest;
import com.multimart.modules.flashsale.dto.FlashSaleOrderResponse;
import com.multimart.modules.flashsale.dto.OrderTrackingResponse;
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
 * Controller tiếp nhận yêu cầu đặt mua sản phẩm Flash Sale và tra cứu trạng thái đơn hàng.
 * Áp dụng kiến trúc bất đồng bộ (RabbitMQ Decoupled Processing) để đạt hiệu năng tối đa.
 */
@RestController
@RequestMapping("/api/v1/flash-sales/orders")
@RequiredArgsConstructor
public class FlashSaleOrderController {

    private final FlashSaleEngineService flashSaleEngineService;
    private final UserRepository userRepository;

    /**
     * API Đặt mua sản phẩm Flash Sale Bất đồng bộ (Async Queue Processing).
     * Trừ kho trong RAM qua Redis Lua Script (< 2ms), đẩy tin nhắn vào RabbitMQ và trả về ngay mã theo dõi.
     *
     * @param request     Dữ liệu đặt mua (eventId, productId, quantity)
     * @param userDetails Thông tin tài khoản người dùng đăng nhập
     * @return HTTP 202 Accepted cùng mã theo dõi đơn hàng orderTrackingId
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AsyncOrderSubmitResponse>> placeFlashSaleOrderAsync(
            @Valid @RequestBody FlashSaleOrderRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        AsyncOrderSubmitResponse response = flashSaleEngineService.submitOrderAsync(user.getId(), request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Yêu cầu đặt mua Flash-Sale đã được tiếp nhận và đang xử lý", response));
    }

    /**
     * API Đặt mua sản phẩm Flash Sale Đồng bộ (Sync Direct DB Write - Fallback).
     *
     * @param request     Dữ liệu đặt mua
     * @param userDetails Thông tin tài khoản người dùng đăng nhập
     * @return HTTP 201 Created cùng thông tin đơn hàng đầy đủ
     */
    @PostMapping("/sync")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<FlashSaleOrderResponse>> placeFlashSaleOrderSync(
            @Valid @RequestBody FlashSaleOrderRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        FlashSaleOrderResponse response = flashSaleEngineService.placeOrder(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đặt mua sản phẩm Flash-Sale đồng bộ thành công", response));
    }

    /**
     * API Tra cứu tiến trình xử lý đơn hàng bất đồng bộ dựa trên mã orderTrackingId.
     * Cho phép Client thực hiện Polling để hiển thị trạng thái hoàn tất cho người dùng.
     *
     * @param trackingId Mã theo dõi UUID nhận được từ API đặt mua
     * @return HTTP 200 OK cùng trạng thái chi tiết của đơn hàng
     */
    @GetMapping("/tracking/{trackingId}")
    public ResponseEntity<ApiResponse<OrderTrackingResponse>> getOrderTrackingStatus(
            @PathVariable String trackingId
    ) {
        OrderTrackingResponse response = flashSaleEngineService.getTrackingStatus(trackingId);
        return ResponseEntity.ok(ApiResponse.success("Lấy trạng thái xử lý đơn hàng thành công", response));
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

    /**
     * API Thanh toán / Xác nhận đơn hàng Flash Sale trước khi hết hạn.
     * Chuyển trạng thái đơn hàng sang CONFIRMED.
     *
     * @param id          ID đơn hàng cần thanh toán
     * @param userDetails Thông tin tài khoản người dùng đăng nhập
     * @return HTTP 200 OK cùng thông tin xác nhận thanh toán
     */
    @PostMapping("/{id}/pay")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<com.multimart.modules.flashsale.dto.FlashSaleOrderPaymentResponse>> payFlashSaleOrder(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        com.multimart.modules.flashsale.dto.FlashSaleOrderPaymentResponse response = flashSaleEngineService.confirmPayment(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Thanh toán đơn hàng Flash-Sale thành công", response));
    }
}
