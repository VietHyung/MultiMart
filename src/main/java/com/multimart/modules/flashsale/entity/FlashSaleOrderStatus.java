package com.multimart.modules.flashsale.entity;

/**
 * Trạng thái của đơn hàng Flash Sale.
 */
public enum FlashSaleOrderStatus {
    /**
     * Đơn hàng đã giữ chỗ và trừ tồn kho thành công, đang chờ thanh toán / xác nhận.
     */
    PENDING,

    /**
     * Đơn hàng đã thanh toán và xác nhận thành công.
     */
    CONFIRMED,

    /**
     * Đơn hàng bị hủy (do quá hạn thanh toán hoặc người dùng hủy).
     */
    CANCELLED
}
