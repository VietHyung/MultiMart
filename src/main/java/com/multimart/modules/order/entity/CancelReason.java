package com.multimart.modules.order.entity;

/**
 * Nguyên nhân hủy đơn hàng.
 */
public enum CancelReason {
    /** Khách hàng chủ động hủy. */
    USER,
    /** Admin hủy. */
    ADMIN,
    /** Hệ thống hủy do quá hạn thanh toán. */
    PAYMENT_TIMEOUT
}
