package com.multimart.modules.order.mq;

import com.multimart.modules.order.entity.CustomerOrder;
import com.multimart.modules.order.entity.OrderStatus;
import com.multimart.modules.order.repository.OrderRepository;
import com.multimart.modules.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Job dự phòng quét đơn ONLINE quá hạn thanh toán, dùng khi RabbitMQ không khả dụng.
 * Dùng chung logic idempotent {@link OrderService#expireOrder(Long)} nên chạy song song với Delay Queue vẫn an toàn.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "order.expiration-scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class OrderExpirationScheduler {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Value("${order.payment-timeout-ms:1800000}")
    private long paymentTimeoutMs;

    /**
     * Mỗi phút hủy tối đa 100 đơn PENDING_PAYMENT đã quá hạn thanh toán. Lỗi của một đơn không chặn các đơn khác.
     */
    @Scheduled(fixedDelayString = "${order.expiration-scheduler.interval-ms:60000}",
            initialDelayString = "${order.expiration-scheduler.interval-ms:60000}")
    public void cancelOverdueOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusNanos(paymentTimeoutMs * 1_000_000L);
        List<CustomerOrder> overdue = orderRepository
                .findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(OrderStatus.PENDING_PAYMENT, deadline);
        for (CustomerOrder order : overdue) {
            try {
                orderService.expireOrder(order.getId());
            } catch (Exception e) {
                log.warn("Could not expire order {}: {}", order.getId(), e.getMessage());
            }
        }
        if (!overdue.isEmpty()) {
            log.info("Expiration scheduler processed {} overdue order(s)", overdue.size());
        }
    }
}
