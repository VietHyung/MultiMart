package com.multimart.modules.flashsale.mq;

import com.multimart.modules.flashsale.dto.FlashSaleOrderTimeoutMessage;
import com.multimart.modules.flashsale.service.FlashSaleEngineService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử đơn vị (Unit Test) cho FlashSaleOrderCancelConsumer.
 * Kiểm tra các tình huống nhận tin nhắn hủy đơn hàng hết hạn thanh toán từ Cancel Queue.
 */
@ExtendWith(MockitoExtension.class)
class FlashSaleOrderCancelConsumerTest {

    @Mock
    private FlashSaleEngineService flashSaleEngineService;

    @InjectMocks
    private FlashSaleOrderCancelConsumer cancelConsumer;

    @Test
    @DisplayName("Hủy đơn hàng và hoàn kho thành công khi đơn ở trạng thái PENDING")
    void processOrderCancelMessage_PendingOrder_Success() {
        FlashSaleOrderTimeoutMessage message = FlashSaleOrderTimeoutMessage.builder()
                .orderId(100L)
                .orderTrackingId("tracking-uuid-123")
                .userId(1L)
                .eventId(2L)
                .productId(3L)
                .flashSaleProductId(4L)
                .quantity(2)
                .createdAt(LocalDateTime.now())
                .build();

        when(flashSaleEngineService.rollbackOrderStock(message)).thenReturn(true);

        cancelConsumer.processOrderCancelMessage(message);

        verify(flashSaleEngineService, times(1)).rollbackOrderStock(message);
    }

    @Test
    @DisplayName("Bỏ qua không hủy đơn khi đơn hàng đã được thanh toán (CONFIRMED) hoặc không tồn tại")
    void processOrderCancelMessage_ConfirmedOrNotFound_Skipped() {
        FlashSaleOrderTimeoutMessage message = FlashSaleOrderTimeoutMessage.builder()
                .orderId(101L)
                .orderTrackingId("tracking-uuid-456")
                .userId(1L)
                .eventId(2L)
                .productId(3L)
                .flashSaleProductId(4L)
                .quantity(1)
                .createdAt(LocalDateTime.now())
                .build();

        when(flashSaleEngineService.rollbackOrderStock(message)).thenReturn(false);

        cancelConsumer.processOrderCancelMessage(message);

        verify(flashSaleEngineService, times(1)).rollbackOrderStock(message);
    }

    @Test
    @DisplayName("Ném lại ngoại lệ khi xảy ra lỗi bất thường trong Service để RabbitMQ kích hoạt Retry")
    void processOrderCancelMessage_ServiceException_Rethrown() {
        FlashSaleOrderTimeoutMessage message = FlashSaleOrderTimeoutMessage.builder()
                .orderId(102L)
                .orderTrackingId("tracking-uuid-789")
                .userId(1L)
                .eventId(2L)
                .productId(3L)
                .flashSaleProductId(4L)
                .quantity(1)
                .createdAt(LocalDateTime.now())
                .build();

        when(flashSaleEngineService.rollbackOrderStock(message)).thenThrow(new RuntimeException("Database connection error"));

        assertThrows(RuntimeException.class, () -> cancelConsumer.processOrderCancelMessage(message));
        verify(flashSaleEngineService, times(1)).rollbackOrderStock(message);
    }
}