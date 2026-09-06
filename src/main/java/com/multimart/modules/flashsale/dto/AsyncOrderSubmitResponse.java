package com.multimart.modules.flashsale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO phản hồi ngay lập tức cho Client sau khi trừ kho Redis thành công
 * và đẩy yêu cầu vào hàng đợi RabbitMQ để ghi Database bất đồng bộ.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AsyncOrderSubmitResponse {

    /**
     * Mã định danh theo dõi đơn hàng (UUID).
     */
    private String orderTrackingId;

    /**
     * Trạng thái tiếp nhận (PENDING_PROCESSING).
     */
    private String status;

    /**
     * ID sự kiện Flash Sale.
     */
    private Long eventId;

    /**
     * ID sản phẩm đặt mua.
     */
    private Long productId;

    /**
     * Số lượng sản phẩm đặt mua.
     */
    private Integer quantity;

    /**
     * Thông báo cho người dùng.
     */
    private String message;
}
