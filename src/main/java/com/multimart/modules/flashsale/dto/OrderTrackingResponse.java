package com.multimart.modules.flashsale.dto;

import com.multimart.modules.flashsale.entity.FlashSaleOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO phản hồi kết quả tra cứu trạng thái xử lý đơn hàng bất đồng bộ.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderTrackingResponse {

    /**
     * Mã theo dõi UUID.
     */
    private String orderTrackingId;

    /**
     * Trạng thái xử lý (PENDING_PROCESSING, SUCCESS, FAILED).
     */
    private String trackingStatus;

    public String getStatus() {
        return trackingStatus;
    }

    /**
     * ID đơn hàng trong cơ sở dữ liệu (khi trackingStatus = SUCCESS).
     */
    private Long orderId;

    /**
     * ID người dùng sở hữu đơn hàng.
     */
    private Long userId;

    /**
     * ID sự kiện Flash Sale.
     */
    private Long eventId;

    /**
     * ID sản phẩm Flash Sale.
     */
    private Long productId;

    /**
     * Số lượng đã đặt mua.
     */
    private Integer quantity;

    /**
     * Đơn giá.
     */
    private BigDecimal unitPrice;

    /**
     * Tổng số tiền thanh toán.
     */
    private BigDecimal totalPrice;

    /**
     * Trạng thái đơn hàng FlashSaleOrderStatus (PENDING, CONFIRMED, CANCELLED).
     */
    private FlashSaleOrderStatus orderStatus;

    /**
     * Thời điểm tạo đơn hàng.
     */
    private LocalDateTime createdAt;
}
