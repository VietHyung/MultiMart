package com.multimart.modules.flashsale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Message DTO truyền qua hàng đợi RabbitMQ để xử lý lưu đơn hàng bất đồng bộ.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashSaleOrderMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Mã theo dõi duy nhất sinh bởi Producer (UUID) để đảm bảo tính Idempotency.
     */
    private String orderTrackingId;

    /**
     * ID của người dùng đặt mua.
     */
    private Long userId;

    /**
     * ID của sự kiện Flash Sale.
     */
    private Long eventId;

    /**
     * ID của sản phẩm gốc.
     */
    private Long productId;

    /**
     * ID của bản ghi FlashSaleProduct trong bảng cấu hình sự kiện.
     */
    private Long flashSaleProductId;

    /**
     * Số lượng đặt mua.
     */
    private Integer quantity;

    /**
     * Đơn giá Flash Sale.
     */
    private BigDecimal unitPrice;

    /**
     * Tổng số tiền cần thanh toán.
     */
    private BigDecimal totalPrice;

    /**
     * Thời điểm đẩy tin nhắn vào hàng đợi.
     */
    private LocalDateTime createdAt;
}
