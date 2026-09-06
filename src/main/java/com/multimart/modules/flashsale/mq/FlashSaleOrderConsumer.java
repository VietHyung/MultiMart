package com.multimart.modules.flashsale.mq;

import com.multimart.config.RabbitMQConfig;
import com.multimart.modules.flashsale.dto.FlashSaleOrderMessage;
import com.multimart.modules.flashsale.entity.FlashSaleOrder;
import com.multimart.modules.flashsale.entity.FlashSaleOrderStatus;
import com.multimart.modules.flashsale.entity.FlashSaleProduct;
import com.multimart.modules.flashsale.repository.FlashSaleOrderRepository;
import com.multimart.modules.flashsale.repository.FlashSaleProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * Consumer tiêu thụ tin nhắn từ RabbitMQ để ghi nhận đơn hàng và trừ kho Database một cách bất đồng bộ.
 * Đảm bảo tính Idempotency (chống xử lý trùng tin nhắn) dựa trên orderTrackingId.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlashSaleOrderConsumer {

    private final FlashSaleOrderRepository flashSaleOrderRepository;
    private final FlashSaleProductRepository flashSaleProductRepository;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Lắng nghe và tiêu thụ message từ hàng đợi flashsale.order.queue.
     * Thực hiện kiểm tra tính idempotent, trừ tồn kho DB và lưu bản ghi đơn hàng.
     *
     * @param message DTO chứa thông tin đơn hàng được đẩy từ Producer
     */
    @RabbitListener(queues = RabbitMQConfig.ORDER_QUEUE)
    @Transactional
    public void processOrderMessage(FlashSaleOrderMessage message) {
        log.info("Consuming flash sale order message: trackingId={}, userId={}, eventId={}",
                message.getOrderTrackingId(), message.getUserId(), message.getEventId());

        // 1. Kiểm tra tính Idempotent: Nếu orderTrackingId đã được ghi vào DB thì bỏ qua
        if (flashSaleOrderRepository.existsByOrderTrackingId(message.getOrderTrackingId())) {
            log.warn("Order message already processed, skipping duplicate: trackingId={}", message.getOrderTrackingId());
            return;
        }

        try {
            // 2. Trừ kho khả dụng trong cơ sở dữ liệu PostgreSQL
            flashSaleProductRepository.findById(message.getFlashSaleProductId()).ifPresent(fsp -> {
                int updatedStock = Math.max(0, fsp.getAvailableStock() - message.getQuantity());
                fsp.setAvailableStock(updatedStock);
                flashSaleProductRepository.save(fsp);
            });

            // 3. Tạo và lưu bản ghi FlashSaleOrder
            FlashSaleOrder order = FlashSaleOrder.builder()
                    .orderTrackingId(message.getOrderTrackingId())
                    .userId(message.getUserId())
                    .flashSaleEventId(message.getEventId())
                    .flashSaleProductId(message.getFlashSaleProductId())
                    .quantity(message.getQuantity())
                    .unitPrice(message.getUnitPrice())
                    .totalPrice(message.getTotalPrice())
                    .status(FlashSaleOrderStatus.PENDING)
                    .build();

            FlashSaleOrder savedOrder = flashSaleOrderRepository.save(order);

            // 4. Cập nhật cache trạng thái xử lý đơn hàng trên Redis để Client tra cứu
            String trackingKey = "flashsale:order:tracking:" + message.getOrderTrackingId();
            stringRedisTemplate.opsForValue().set(trackingKey, "SUCCESS:" + savedOrder.getId(), 24, TimeUnit.HOURS);

            log.info("Successfully processed and saved flash sale order: orderId={}, trackingId={}",
                    savedOrder.getId(), message.getOrderTrackingId());

        } catch (Exception e) {
            log.error("Failed to process flash sale order message: trackingId={}, error={}",
                    message.getOrderTrackingId(), e.getMessage(), e);
            // Ném lại exception để RabbitMQ kích hoạt cơ chế retry hoặc chuyển vào DLQ nếu quá số lần thử
            throw e;
        }
    }
}
