package com.multimart.modules.flashsale.controller;

import com.multimart.common.response.ApiResponse;
import com.multimart.modules.flashsale.dto.FlashSaleEventResponse;
import com.multimart.modules.flashsale.service.FlashSaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller công khai dành cho khách hàng xem các sự kiện Flash-Sale.
 * Cho phép truy cập không cần đăng nhập.
 */
@RestController
@RequestMapping("/api/v1/flash-sales")
@RequiredArgsConstructor
public class PublicFlashSaleController {

    private final FlashSaleService flashSaleService;

    /**
     * API Lấy danh sách các sự kiện Flash-Sale đang trong khung giờ mở bán (ACTIVE).
     * Đi kèm số giây đếm ngược còn lại (remainingSeconds) để hiển thị đồng hồ đếm ngược trên giao diện.
     *
     * @return HTTP 200 OK cùng danh sách sự kiện đang hoạt động
     */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<FlashSaleEventResponse>>> getActiveEvents() {
        List<FlashSaleEventResponse> response = flashSaleService.getActiveEvents();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * API Xem thông tin chi tiết của một sự kiện Flash-Sale và toàn bộ danh sách sản phẩm giảm giá bên trong.
     *
     * @param id mã định danh sự kiện Flash-Sale
     * @return HTTP 200 OK cùng thông tin sự kiện và danh sách sản phẩm sale
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FlashSaleEventResponse>> getEventDetails(@PathVariable Long id) {
        FlashSaleEventResponse response = flashSaleService.getEventDetails(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
