package com.multimart.modules.flashsale.mq;

import com.multimart.config.RabbitMQConfig;
import com.multimart.modules.flashsale.dto.FlashSaleOrderMessage;
import com.multimart.modules.flashsale.dto.FlashSaleOrderTimeoutMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Producer chịu trách nhiệm phát sinh sự kiện đặt đơn hàng Flash Sale vào hàng đợi RabbitMQ.
 * Tách rời luồng ghi đĩa Database khỏi luồng phản hồi cho người dùng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlashSaleOrderProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Gửi message yêu cầu tạo đơn hàng vào Exchange của RabbitMQ.
     *
     * @param message DTO chứa toàn bộ thông tin đơn hàng Flash Sale cần tạo
     */
    public void sendOrderMessage(FlashSaleOrderMessage message) {
        log.info("Sending flash sale order message to RabbitMQ: trackingId={}, userId={}, eventId={}, productId={}",
                message.getOrderTrackingId(), message.getUserId(), message.getEventId(), message.getProductId());

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ORDER_ROUTING_KEY,
                message
        );
    }

    /**
     * Gửi message hẹn giờ hủy đơn hàng vào Delay Queue với thời gian sống (TTL).
     * Khi hết hạn delayMs, tin nhắn tự động được Dead Letter sang Cancel Exchange.
     *
     * @param message DTO chứa thông tin đơn hàng cần kiểm tra quá hạn
     * @param delayMs Thời gian trì hoãn tính bằng mili-giây (TTL)
     */
    public void sendOrderTimeoutMessage(FlashSaleOrderTimeoutMessage message, long delayMs) {
        log.info("Sending order timeout message to delay queue: orderId={}, trackingId={}, delayMs={}",
                message.getOrderId(), message.getOrderTrackingId(), delayMs);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.DELAY_ROUTING_KEY,
                message,
                msg -> {
                    msg.getMessageProperties().setExpiration(String.valueOf(delayMs));
                    return msg;
                }
        );
    }
}
