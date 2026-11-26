package com.multimart.modules.order.mq;

import com.multimart.config.OrderPaymentRabbitConfig;
import com.multimart.modules.order.dto.OrderPaymentTimeoutMessage;
import com.multimart.modules.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Nhận tin nhắn đã hết hạn từ Cancel Queue và hủy đơn ONLINE chưa thanh toán (idempotent).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaymentTimeoutConsumer {

    private final OrderService orderService;

    /**
     * Xử lý tin nhắn hết hạn thanh toán: hủy đơn và hoàn kho nếu đơn vẫn PENDING_PAYMENT.
     *
     * @param message tin nhắn chứa id đơn hàng
     */
    @RabbitListener(queues = OrderPaymentRabbitConfig.CANCEL_QUEUE)
    public void onPaymentTimeout(OrderPaymentTimeoutMessage message) {
        boolean cancelled = orderService.expireOrder(message.getOrderId());
        log.info("Payment timeout processed: orderId={}, cancelled={}", message.getOrderId(), cancelled);
    }
}
