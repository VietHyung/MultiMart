package com.multimart.modules.flashsale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Message DTO truyền qua hàng đợi trễ RabbitMQ (Delay Queue) để xử lý hủy đơn hàng quá hạn thanh toán
 * và tự động hoàn trả tồn kho (Stock Rollback).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashSaleOrderTimeoutMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * ID của đơn hàng Flash Sale trong cơ sở dữ liệu PostgreSQL.
     */
    private Long orderId;

    /**
     * Mã theo dõi duy nhất sinh bởi hệ thống (UUID).
     */
    private String orderTrackingId;

    /**
     * ID của người dùng đặt mua đơn hàng.
     */
    private Long userId;

    /**
     * ID sự kiện Flash Sale.
     */
    private Long eventId;

    /**
     * ID sản phẩm gốc.
     */
    private Long productId;

    /**
     * ID bản ghi FlashSaleProduct trong cấu hình sự kiện.
     */
    private Long flashSaleProductId;

    /**
     * Số lượng sản phẩm cần hoàn trả lại kho nếu đơn hàng bị hủy.
     */
    private Integer quantity;

    /**
     * Thời điểm đẩy tin nhắn vào hàng đợi trì hoãn.
     */
    private LocalDateTime createdAt;
}
