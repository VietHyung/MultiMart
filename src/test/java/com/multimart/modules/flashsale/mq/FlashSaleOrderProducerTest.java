package com.multimart.modules.flashsale.mq;

import com.multimart.config.RabbitMQConfig;
import com.multimart.modules.flashsale.dto.FlashSaleOrderMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Kiểm thử đơn vị (Unit Test) cho FlashSaleOrderProducer.
 */
@ExtendWith(MockitoExtension.class)
class FlashSaleOrderProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private FlashSaleOrderProducer producer;

    @Test
    @DisplayName("Gửi message tạo đơn hàng vào RabbitMQ Exchange thành công")
    void sendOrderMessage_Success() {
        FlashSaleOrderMessage message = FlashSaleOrderMessage.builder()
                .orderTrackingId(UUID.randomUUID().toString())
                .userId(1L)
                .eventId(2L)
                .productId(3L)
                .flashSaleProductId(4L)
                .quantity(1)
                .unitPrice(BigDecimal.valueOf(100000))
                .totalPrice(BigDecimal.valueOf(100000))
                .createdAt(LocalDateTime.now())
                .build();

        producer.sendOrderMessage(message);

        verify(rabbitTemplate, times(1)).convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ORDER_ROUTING_KEY,
                message
        );
    }
}
