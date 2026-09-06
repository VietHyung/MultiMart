package com.multimart.modules.flashsale.mq;

import com.multimart.config.RabbitMQConfig;
import com.multimart.modules.flashsale.dto.FlashSaleOrderMessage;
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
}
