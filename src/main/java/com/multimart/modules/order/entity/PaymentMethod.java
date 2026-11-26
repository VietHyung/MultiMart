package com.multimart.modules.order.entity;

/**
 * Phương thức thanh toán của đơn hàng thường.
 */
public enum PaymentMethod {
    /** Thanh toán khi nhận hàng. */
    COD,
    /** Thanh toán trực tuyến (giả lập ở phase này). */
    ONLINE
}
