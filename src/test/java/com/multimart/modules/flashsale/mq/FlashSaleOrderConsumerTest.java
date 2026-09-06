package com.multimart.modules.flashsale.mq;

import com.multimart.modules.flashsale.dto.FlashSaleOrderMessage;
import com.multimart.modules.flashsale.entity.FlashSaleOrder;
import com.multimart.modules.flashsale.entity.FlashSaleProduct;
import com.multimart.modules.flashsale.repository.FlashSaleOrderRepository;
import com.multimart.modules.flashsale.repository.FlashSaleProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử đơn vị (Unit Test) cho FlashSaleOrderConsumer.
 * Kiểm tra tính Idempotent và việc trừ tồn kho DB + lưu đơn hàng.
 */
@ExtendWith(MockitoExtension.class)
class FlashSaleOrderConsumerTest {

    @Mock
    private FlashSaleOrderRepository flashSaleOrderRepository;

    @Mock
    private FlashSaleProductRepository flashSaleProductRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private FlashSaleOrderConsumer consumer;

    @Test
    @DisplayName("Bỏ qua xử lý tin nhắn trùng lặp khi orderTrackingId đã tồn tại trong DB (Idempotency)")
    void processOrderMessage_DuplicateMessage_Skipped() {
        String trackingId = UUID.randomUUID().toString();
        FlashSaleOrderMessage message = FlashSaleOrderMessage.builder()
                .orderTrackingId(trackingId)
                .userId(1L)
                .eventId(2L)
                .productId(3L)
                .flashSaleProductId(4L)
                .quantity(1)
                .unitPrice(BigDecimal.valueOf(100000))
                .totalPrice(BigDecimal.valueOf(100000))
                .createdAt(LocalDateTime.now())
                .build();

        when(flashSaleOrderRepository.existsByOrderTrackingId(trackingId)).thenReturn(true);

        consumer.processOrderMessage(message);

        verify(flashSaleOrderRepository, times(1)).existsByOrderTrackingId(trackingId);
        verify(flashSaleProductRepository, never()).findById(any());
        verify(flashSaleOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Xử lý đơn hàng thành công: Trừ kho DB, lưu FlashSaleOrder và cập nhật tracking cache")
    void processOrderMessage_Success() {
        String trackingId = UUID.randomUUID().toString();
        FlashSaleOrderMessage message = FlashSaleOrderMessage.builder()
                .orderTrackingId(trackingId)
                .userId(1L)
                .eventId(2L)
                .productId(3L)
                .flashSaleProductId(4L)
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(100000))
                .totalPrice(BigDecimal.valueOf(200000))
                .createdAt(LocalDateTime.now())
                .build();

        FlashSaleProduct mockProduct = FlashSaleProduct.builder()
                .availableStock(10)
                .totalAllocated(20)
                .flashPrice(BigDecimal.valueOf(100000))
                .build();
        mockProduct.setId(4L);

        FlashSaleOrder mockSavedOrder = FlashSaleOrder.builder()
                .orderTrackingId(trackingId)
                .userId(1L)
                .flashSaleEventId(2L)
                .flashSaleProductId(4L)
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(100000))
                .totalPrice(BigDecimal.valueOf(200000))
                .build();
        mockSavedOrder.setId(99L);

        when(flashSaleOrderRepository.existsByOrderTrackingId(trackingId)).thenReturn(false);
        when(flashSaleProductRepository.findById(4L)).thenReturn(Optional.of(mockProduct));
        when(flashSaleOrderRepository.save(any(FlashSaleOrder.class))).thenReturn(mockSavedOrder);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        consumer.processOrderMessage(message);

        // Kiểm tra trừ kho DB: 10 - 2 = 8
        assertEquals(8, mockProduct.getAvailableStock());
        verify(flashSaleProductRepository, times(1)).save(mockProduct);
        verify(flashSaleOrderRepository, times(1)).save(any(FlashSaleOrder.class));
        verify(valueOperations, times(1)).set(
                eq("flashsale:order:tracking:" + trackingId),
                eq("SUCCESS:99"),
                eq(24L),
                eq(TimeUnit.HOURS)
        );
    }
}
