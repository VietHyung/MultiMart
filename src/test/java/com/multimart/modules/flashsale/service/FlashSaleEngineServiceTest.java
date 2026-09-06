package com.multimart.modules.flashsale.service;

import com.multimart.common.exception.AppException;
import com.multimart.common.exception.ErrorCode;
import com.multimart.modules.flashsale.dto.FlashSaleOrderRequest;
import com.multimart.modules.flashsale.dto.FlashSaleOrderResponse;
import com.multimart.modules.flashsale.dto.WarmUpEventResponse;
import com.multimart.modules.flashsale.entity.FlashSaleEvent;
import com.multimart.modules.flashsale.entity.FlashSaleOrder;
import com.multimart.modules.flashsale.entity.FlashSaleOrderStatus;
import com.multimart.modules.flashsale.entity.FlashSaleProduct;
import com.multimart.modules.flashsale.repository.FlashSaleEventRepository;
import com.multimart.modules.flashsale.repository.FlashSaleOrderRepository;
import com.multimart.modules.flashsale.repository.FlashSaleProductRepository;
import com.multimart.modules.product.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bộ kiểm thử đơn vị (Unit Test) cho FlashSaleEngineService.
 * Kiểm tra các tình huống nghiệp vụ:
 * 1. Làm nóng dữ liệu (Warm-up) thành công và thất bại.
 * 2. Đặt mua thành công khi Redis còn tồn kho và user chưa chạm limit.
 * 3. Bị từ chối khi chưa warm-up (mã -3).
 * 4. Bị từ chối khi vượt quá giới hạn mua (mã -2).
 * 5. Bị từ chối khi hết hàng (mã -1).
 * 6. Bị từ chối khi sự kiện không ở trạng thái ACTIVE.
 */
@ExtendWith(MockitoExtension.class)
class FlashSaleEngineServiceTest {

    @Mock
    private FlashSaleEventRepository eventRepository;

    @Mock
    private FlashSaleProductRepository flashSaleProductRepository;

    @Mock
    private FlashSaleOrderRepository flashSaleOrderRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private DefaultRedisScript<Long> flashSaleStockDeductScript;

    @Mock
    private com.multimart.modules.flashsale.mq.FlashSaleOrderProducer flashSaleOrderProducer;

    @InjectMocks
    private FlashSaleEngineService engineService;

    private FlashSaleEvent activeEvent;
    private FlashSaleProduct flashSaleProduct;
    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        sampleProduct = Product.builder()
                .name("Tai nghe Bluetooth")
                .originalPrice(BigDecimal.valueOf(1000000))
                .totalStock(100)
                .build();
        sampleProduct.setId(10L);

        activeEvent = FlashSaleEvent.builder()
                .name("Flash Sale 12h")
                .startTime(LocalDateTime.now().minusHours(1))
                .endTime(LocalDateTime.now().plusHours(1))
                .status("ACTIVE")
                .build();
        activeEvent.setId(1L);

        flashSaleProduct = FlashSaleProduct.builder()
                .event(activeEvent)
                .product(sampleProduct)
                .flashPrice(BigDecimal.valueOf(500000))
                .totalAllocated(50)
                .availableStock(50)
                .purchaseLimitPerUser(2)
                .version(0L)
                .build();
        flashSaleProduct.setId(100L);
    }

    @Test
    @DisplayName("Nạp tồn kho Flash Sale lên Redis thành công khi sự kiện hợp lệ")
    void warmUpEvent_Success() {
        when(eventRepository.findById(1L)).thenReturn(Optional.of(activeEvent));
        when(flashSaleProductRepository.findByEventId(1L)).thenReturn(List.of(flashSaleProduct));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        WarmUpEventResponse response = engineService.warmUpEvent(1L);

        assertNotNull(response);
        assertEquals(1L, response.getEventId());
        assertEquals(1, response.getWarmedUpProductsCount());
        verify(valueOperations, times(1)).set(eq("flashsale:stock:1:10"), eq("50"));
    }

    @Test
    @DisplayName("Ném ngoại lệ EVENT_NOT_FOUND khi warm-up sự kiện không tồn tại")
    void warmUpEvent_EventNotFound() {
        when(eventRepository.findById(999L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> engineService.warmUpEvent(999L));
        assertEquals(ErrorCode.EVENT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("Đặt mua Flash Sale thành công khi Redis trừ kho nguyên tử thành công")
    void placeOrder_Success() {
        FlashSaleOrderRequest request = FlashSaleOrderRequest.builder()
                .eventId(1L)
                .productId(10L)
                .quantity(1)
                .build();

        when(eventRepository.findById(1L)).thenReturn(Optional.of(activeEvent));
        when(flashSaleProductRepository.findByEventIdAndProductId(1L, 10L)).thenReturn(Optional.of(flashSaleProduct));
        when(stringRedisTemplate.execute(
                eq(flashSaleStockDeductScript),
                anyList(),
                eq("1"),
                eq("1"),
                eq("2")
        )).thenReturn(49L); // Còn 49 sản phẩm sau khi trừ

        when(flashSaleProductRepository.save(any(FlashSaleProduct.class))).thenReturn(flashSaleProduct);

        FlashSaleOrder mockOrder = FlashSaleOrder.builder()
                .userId(1L)
                .flashSaleEventId(1L)
                .flashSaleProductId(100L)
                .quantity(1)
                .unitPrice(BigDecimal.valueOf(500000))
                .totalPrice(BigDecimal.valueOf(500000))
                .build();
        mockOrder.setId(200L);
        when(flashSaleOrderRepository.save(any(FlashSaleOrder.class))).thenReturn(mockOrder);

        FlashSaleOrderResponse response = engineService.placeOrder(1L, request);

        assertNotNull(response);
        assertEquals(200L, response.getOrderId());
        assertEquals(1L, response.getUserId());
        assertEquals(10L, response.getProductId());
        assertEquals(BigDecimal.valueOf(500000), response.getTotalPrice());

        // Kiểm tra trừ kho DB
        assertEquals(49, flashSaleProduct.getAvailableStock());
        verify(flashSaleOrderRepository, times(1)).save(any(FlashSaleOrder.class));
    }

    @Test
    @DisplayName("Ném ngoại lệ FLASH_SALE_NOT_WARMED_UP khi Redis trả về mã -3")
    void placeOrder_NotWarmedUp() {
        FlashSaleOrderRequest request = FlashSaleOrderRequest.builder()
                .eventId(1L)
                .productId(10L)
                .quantity(1)
                .build();

        when(eventRepository.findById(1L)).thenReturn(Optional.of(activeEvent));
        when(flashSaleProductRepository.findByEventIdAndProductId(1L, 10L)).thenReturn(Optional.of(flashSaleProduct));
        when(stringRedisTemplate.execute(any(), anyList(), anyString(), anyString(), anyString())).thenReturn(-3L);

        AppException ex = assertThrows(AppException.class, () -> engineService.placeOrder(1L, request));
        assertEquals(ErrorCode.FLASH_SALE_NOT_WARMED_UP, ex.getErrorCode());
        verify(flashSaleOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Ném ngoại lệ PURCHASE_LIMIT_EXCEEDED khi Redis trả về mã -2")
    void placeOrder_LimitExceeded() {
        FlashSaleOrderRequest request = FlashSaleOrderRequest.builder()
                .eventId(1L)
                .productId(10L)
                .quantity(2)
                .build();

        when(eventRepository.findById(1L)).thenReturn(Optional.of(activeEvent));
        when(flashSaleProductRepository.findByEventIdAndProductId(1L, 10L)).thenReturn(Optional.of(flashSaleProduct));
        when(stringRedisTemplate.execute(any(), anyList(), anyString(), anyString(), anyString())).thenReturn(-2L);

        AppException ex = assertThrows(AppException.class, () -> engineService.placeOrder(1L, request));
        assertEquals(ErrorCode.PURCHASE_LIMIT_EXCEEDED, ex.getErrorCode());
        verify(flashSaleOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Ném ngoại lệ OUT_OF_STOCK khi Redis trả về mã -1")
    void placeOrder_OutOfStock() {
        FlashSaleOrderRequest request = FlashSaleOrderRequest.builder()
                .eventId(1L)
                .productId(10L)
                .quantity(1)
                .build();

        when(eventRepository.findById(1L)).thenReturn(Optional.of(activeEvent));
        when(flashSaleProductRepository.findByEventIdAndProductId(1L, 10L)).thenReturn(Optional.of(flashSaleProduct));
        when(stringRedisTemplate.execute(any(), anyList(), anyString(), anyString(), anyString())).thenReturn(-1L);

        AppException ex = assertThrows(AppException.class, () -> engineService.placeOrder(1L, request));
        assertEquals(ErrorCode.OUT_OF_STOCK, ex.getErrorCode());
        verify(flashSaleOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Ném ngoại lệ EVENT_NOT_ACTIVE khi sự kiện đã kết thúc")
    void placeOrder_EventNotActive() {
        activeEvent.setStatus("ENDED");
        FlashSaleOrderRequest request = FlashSaleOrderRequest.builder()
                .eventId(1L)
                .productId(10L)
                .quantity(1)
                .build();

        when(eventRepository.findById(1L)).thenReturn(Optional.of(activeEvent));

        AppException ex = assertThrows(AppException.class, () -> engineService.placeOrder(1L, request));
        assertEquals(ErrorCode.EVENT_NOT_ACTIVE, ex.getErrorCode());
        verify(stringRedisTemplate, never()).execute(any(), anyList(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Đặt mua bất đồng bộ thành công: Trừ kho Redis, gửi message vào RabbitMQ và trả về orderTrackingId")
    void submitOrderAsync_Success() {
        FlashSaleOrderRequest request = FlashSaleOrderRequest.builder()
                .eventId(1L)
                .productId(10L)
                .quantity(1)
                .build();

        when(eventRepository.findById(1L)).thenReturn(Optional.of(activeEvent));
        when(flashSaleProductRepository.findByEventIdAndProductId(1L, 10L)).thenReturn(Optional.of(flashSaleProduct));
        when(stringRedisTemplate.execute(
                eq(flashSaleStockDeductScript),
                anyList(),
                eq("1"),
                eq("1"),
                eq("2")
        )).thenReturn(49L);

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        com.multimart.modules.flashsale.dto.AsyncOrderSubmitResponse response = engineService.submitOrderAsync(1L, request);

        assertNotNull(response);
        assertNotNull(response.getOrderTrackingId());
        assertEquals("PENDING_PROCESSING", response.getStatus());
        assertEquals(1L, response.getEventId());
        assertEquals(10L, response.getProductId());

        verify(flashSaleOrderProducer, times(1)).sendOrderMessage(any());
        verify(valueOperations, times(1)).set(
                startsWith("flashsale:order:tracking:"),
                eq("PENDING_PROCESSING"),
                eq(24L),
                eq(java.util.concurrent.TimeUnit.HOURS)
        );
    }

    @Test
    @DisplayName("Tra cứu trạng thái đơn hàng thành công từ Database")
    void getTrackingStatus_FromDb_Success() {
        String trackingId = "uuid-1234";
        FlashSaleOrder mockOrder = FlashSaleOrder.builder()
                .orderTrackingId(trackingId)
                .userId(1L)
                .flashSaleEventId(1L)
                .flashSaleProductId(100L)
                .quantity(1)
                .unitPrice(BigDecimal.valueOf(500000))
                .totalPrice(BigDecimal.valueOf(500000))
                .status(FlashSaleOrderStatus.PENDING)
                .build();
        mockOrder.setId(101L);

        when(flashSaleOrderRepository.findByOrderTrackingId(trackingId)).thenReturn(Optional.of(mockOrder));
        when(flashSaleProductRepository.findById(100L)).thenReturn(Optional.of(flashSaleProduct));

        com.multimart.modules.flashsale.dto.OrderTrackingResponse response = engineService.getTrackingStatus(trackingId);

        assertNotNull(response);
        assertEquals(trackingId, response.getOrderTrackingId());
        assertEquals("SUCCESS", response.getTrackingStatus());
        assertEquals(101L, response.getOrderId());
    }

    @Test
    @DisplayName("Tra cứu trạng thái đơn hàng đang chờ xử lý từ Redis")
    void getTrackingStatus_FromRedis_Pending() {
        String trackingId = "uuid-5678";
        when(flashSaleOrderRepository.findByOrderTrackingId(trackingId)).thenReturn(Optional.empty());
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("flashsale:order:tracking:" + trackingId)).thenReturn("PENDING_PROCESSING");

        com.multimart.modules.flashsale.dto.OrderTrackingResponse response = engineService.getTrackingStatus(trackingId);

        assertNotNull(response);
        assertEquals(trackingId, response.getOrderTrackingId());
        assertEquals("PENDING_PROCESSING", response.getTrackingStatus());
    }
}
