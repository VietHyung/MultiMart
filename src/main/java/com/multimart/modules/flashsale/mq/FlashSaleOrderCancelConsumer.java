package com.multimart.modules.flashsale.mq;

import com.multimart.config.RabbitMQConfig;
import com.multimart.modules.flashsale.dto.FlashSaleOrderTimeoutMessage;
import com.multimart.modules.flashsale.service.FlashSaleEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumer chịu trách nhiệm lắng nghe các tin nhắn đơn hàng quá hạn từ Cancel Queue (flashsale.order.cancel.queue).
 * Kích hoạt luồng hủy đơn hàng và hoàn trả tồn kho tự động (Stock Rollback).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlashSaleOrderCancelConsumer {

    private final FlashSaleEngineService flashSaleEngineService;

    /**
     * Tiêu thụ message hết hạn từ Dead Letter Exchange để xử lý hủy đơn hàng quá hạn thanh toán.
     *
     * @param message DTO chứa thông tin đơn hàng bị quá hạn cần hủy và hoàn kho
     */
    @RabbitListener(queues = RabbitMQConfig.CANCEL_QUEUE)
    public void processOrderCancelMessage(FlashSaleOrderTimeoutMessage message) {
        log.info("Received order timeout message from cancel queue: orderId={}, trackingId={}, userId={}",
                message.getOrderId(), message.getOrderTrackingId(), message.getUserId());

        try {
            boolean result = flashSaleEngineService.rollbackOrderStock(message);
            if (result) {
                log.info("Order timeout processed successfully, stock rolled back: orderId={}", message.getOrderId());
            } else {
                log.info("Order timeout skipped (order already processed/paid or not found): orderId={}", message.getOrderId());
            }
        } catch (Exception e) {
            log.error("Failed to process order timeout message: orderId={}, error={}",
                    message.getOrderId(), e.getMessage(), e);
            throw e;
        }
    }
}
