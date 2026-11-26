package com.multimart.modules.order.mq;

import com.multimart.config.OrderPaymentRabbitConfig;
import com.multimart.modules.order.dto.OrderPaymentTimeoutMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Gửi tin nhắn hẹn giờ hủy đơn vào Delay Queue (TTL). Nếu RabbitMQ không khả dụng chỉ ghi log,
 * vì job {@code OrderExpirationScheduler} sẽ hủy đơn quá hạn thay thế.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaymentTimeoutProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Đẩy tin nhắn vào Delay Queue với thời gian sống bằng thời hạn thanh toán.
     *
     * @param message tin nhắn chứa id đơn hàng
     * @param delayMs thời gian chờ (mili-giây) trước khi tin nhắn hết hạn và chuyển sang hàng đợi hủy
     */
    public void sendTimeoutMessage(OrderPaymentTimeoutMessage message, long delayMs) {
        try {
            rabbitTemplate.convertAndSend(
                    OrderPaymentRabbitConfig.EXCHANGE,
                    OrderPaymentRabbitConfig.DELAY_ROUTING_KEY,
                    message,
                    msg -> {
                        msg.getMessageProperties().setExpiration(String.valueOf(delayMs));
                        return msg;
                    });
            log.info("Scheduled payment timeout: orderId={}, delayMs={}", message.getOrderId(), delayMs);
        } catch (Exception e) {
            log.warn("RabbitMQ unavailable, payment timeout relies on scheduler fallback: orderId={}, error={}",
                    message.getOrderId(), e.getMessage());
        }
    }
}
