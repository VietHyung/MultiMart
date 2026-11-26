package com.multimart.modules.order.entity;

/**
 * Trạng thái đơn hàng thường.
 * Luồng: PENDING_PAYMENT -> CONFIRMED -> SHIPPING -> DELIVERED; có thể CANCELLED trước khi giao.
 */
public enum OrderStatus {
    /** Đơn ONLINE đang chờ thanh toán (có hạn). */
    PENDING_PAYMENT,
    /** Đã xác nhận (COD khi tạo, ONLINE sau khi thanh toán). */
    CONFIRMED,
    /** Đang giao hàng. */
    SHIPPING,
    /** Đã giao thành công. */
    DELIVERED,
    /** Đã hủy (người dùng, Admin hoặc quá hạn thanh toán). */
    CANCELLED
}
