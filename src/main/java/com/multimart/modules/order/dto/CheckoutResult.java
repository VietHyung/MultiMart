package com.multimart.modules.order.dto;

/**
 * Kết quả đặt hàng nội bộ: đơn hàng và cờ cho biết đơn mới tạo hay là đơn cũ trả lại do trùng Idempotency-Key.
 *
 * @param order   đơn hàng
 * @param created true nếu đơn vừa được tạo mới (HTTP 201), false nếu là đơn cũ (HTTP 200)
 */
public record CheckoutResult(OrderResponse order, boolean created) {
}
