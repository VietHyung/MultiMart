package com.multimart.modules.flashsale.controller;

import com.multimart.common.response.ApiResponse;
import com.multimart.modules.flashsale.dto.AddProductToEventRequest;
import com.multimart.modules.flashsale.dto.CreateEventRequest;
import com.multimart.modules.flashsale.dto.FlashSaleEventResponse;
import com.multimart.modules.flashsale.dto.FlashSaleProductResponse;
import com.multimart.modules.flashsale.dto.WarmUpEventResponse;
import com.multimart.modules.flashsale.service.FlashSaleEngineService;
import com.multimart.modules.flashsale.service.FlashSaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller dành riêng cho Quản trị viên (Admin) quản lý các sự kiện Flash-Sale.
 * Tất cả các endpoint đều được bảo vệ nghiêm ngặt bằng @PreAuthorize("hasRole('ADMIN')").
 */
@RestController
@RequestMapping("/api/v1/admin/flash-sales")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminFlashSaleController {

    private final FlashSaleService flashSaleService;
    private final FlashSaleEngineService flashSaleEngineService;

    /**
     * API Tạo đợt sự kiện Flash-Sale mới.
     * Yêu cầu quyền ROLE_ADMIN.
     *
     * @param request thông tin sự kiện (tên sự kiện, thời gian bắt đầu, thời gian kết thúc ở tương lai)
     * @return HTTP 201 Created cùng thông tin sự kiện vừa tạo
     */
    @PostMapping
    public ResponseEntity<ApiResponse<FlashSaleEventResponse>> createEvent(
            @Valid @RequestBody CreateEventRequest request
    ) {
        FlashSaleEventResponse response = flashSaleService.createEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo sự kiện Flash-Sale thành công", response));
    }

    /**
     * API Đưa một sản phẩm gốc vào đợt Flash-Sale và thực hiện giam kho tồn gốc.
     * Yêu cầu quyền ROLE_ADMIN.
     *
     * @param id      mã sự kiện Flash-Sale
     * @param request thông tin sản phẩm đưa vào sale (productId, flashPrice, totalAllocated, purchaseLimitPerUser)
     * @return HTTP 201 Created cùng thông tin FlashSaleProduct đã tạo
     */
    @PostMapping("/{id}/products")
    public ResponseEntity<ApiResponse<FlashSaleProductResponse>> addProductToEvent(
            @PathVariable Long id,
            @Valid @RequestBody AddProductToEventRequest request
    ) {
        FlashSaleProductResponse response = flashSaleService.addProductToEvent(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Thêm sản phẩm vào Flash-Sale thành công", response));
    }

    /**
     * API Nạp trước tồn kho sự kiện Flash Sale lên Redis Cache (Cache Warm-up).
     * Yêu cầu quyền ROLE_ADMIN.
     *
     * @param id mã sự kiện Flash Sale
     * @return HTTP 200 OK cùng thông tin kết quả warm-up
     */
    @PostMapping("/{id}/warm-up")
    public ResponseEntity<ApiResponse<WarmUpEventResponse>> warmUpEvent(
            @PathVariable Long id
    ) {
        WarmUpEventResponse response = flashSaleEngineService.warmUpEvent(id);
        return ResponseEntity.ok(ApiResponse.success("Làm nóng dữ liệu Flash Sale thành công", response));
    }
}
