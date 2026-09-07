package com.multimart.modules.flashsale.dto;

import com.multimart.modules.flashsale.entity.FlashSaleOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO phản hồi kết quả thanh toán / xác nhận đơn hàng Flash Sale.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashSaleOrderPaymentResponse {

    /**
     * ID đơn hàng trong cơ sở dữ liệu.
     */
    private Long orderId;

    /**
     * Mã theo dõi UUID của đơn hàng.
     */
    private String orderTrackingId;

    /**
     * Trạng thái đơn hàng sau khi thanh toán thành công (CONFIRMED).
     */
    private FlashSaleOrderStatus status;

    /**
     * Tổng số tiền đã thanh toán.
     */
    private BigDecimal totalPrice;

    /**
     * Thời điểm thanh toán thành công.
     */
    private LocalDateTime paidAt;

    /**
     * Thông báo cho người dùng.
     */
    private String message;
}
