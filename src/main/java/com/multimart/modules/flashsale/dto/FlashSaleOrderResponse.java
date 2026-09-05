package com.multimart.modules.flashsale.dto;

import com.multimart.modules.flashsale.entity.FlashSaleOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO phản hồi thông tin đơn hàng Flash Sale đã tạo thành công.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashSaleOrderResponse {

    /**
     * Mã định danh đơn hàng Flash Sale.
     */
    private Long orderId;

    /**
     * ID người dùng đặt hàng.
     */
    private Long userId;

    /**
     * ID sự kiện Flash Sale.
     */
    private Long eventId;

    /**
     * ID sản phẩm đặt mua.
     */
    private Long productId;

    /**
     * Số lượng đã đặt mua thành công.
     */
    private Integer quantity;

    /**
     * Đơn giá Flash Sale.
     */
    private BigDecimal unitPrice;

    /**
     * Tổng số tiền thanh toán của đơn hàng.
     */
    private BigDecimal totalPrice;

    /**
     * Trạng thái đơn hàng (PENDING, CONFIRMED, CANCELLED).
     */
    private FlashSaleOrderStatus status;

    /**
     * Thời điểm tạo đơn hàng.
     */
    private LocalDateTime createdAt;
}
