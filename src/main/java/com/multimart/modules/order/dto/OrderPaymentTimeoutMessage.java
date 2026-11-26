package com.multimart.modules.order.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tin nhắn hẹn giờ hủy đơn ONLINE quá hạn thanh toán, gửi qua RabbitMQ Delay Queue.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaymentTimeoutMessage {

    /** Id đơn hàng cần kiểm tra khi hết hạn. */
    private Long orderId;

    /** Mã đơn hàng (phục vụ ghi log). */
    private String orderCode;
}
