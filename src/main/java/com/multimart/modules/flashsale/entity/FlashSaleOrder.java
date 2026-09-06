package com.multimart.modules.flashsale.entity;

import com.multimart.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Thực thể lưu vết đơn hàng đặt mua trong đợt Flash Sale.
 * Được ghi nhận sau khi trừ tồn kho thành công trên Redis Cache Engine.
 */
@Entity
@Table(name = "flash_sale_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashSaleOrder extends BaseEntity {

    /**
     * Mã theo dõi đơn hàng bất đồng bộ (UUID duy nhất) đảm bảo Idempotency.
     */
    @Column(name = "order_tracking_id", length = 64, unique = true)
    private String orderTrackingId;

    /**
     * ID của người dùng đặt mua.
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * ID của sự kiện Flash Sale.
     */
    @Column(name = "flash_sale_event_id", nullable = false)
    private Long flashSaleEventId;

    /**
     * ID của bản ghi cấu hình sản phẩm trong sự kiện Flash Sale.
     */
    @Column(name = "flash_sale_product_id", nullable = false)
    private Long flashSaleProductId;

    /**
     * Số lượng sản phẩm khách hàng đặt mua.
     */
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /**
     * Đơn giá Flash Sale tại thời điểm đặt mua.
     */
    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    /**
     * Tổng số tiền thanh toán cho đơn hàng Flash Sale (quantity * unitPrice).
     */
    @Column(name = "total_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalPrice;

    /**
     * Trạng thái đơn hàng (PENDING, CONFIRMED, CANCELLED).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private FlashSaleOrderStatus status;
}
