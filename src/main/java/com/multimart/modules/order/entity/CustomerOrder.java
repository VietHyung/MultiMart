package com.multimart.modules.order.entity;

import com.multimart.common.base.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Đơn hàng thường (không phải Flash Sale). Ánh xạ bảng {@code orders}.
 * Dùng khóa lạc quan {@code @Version} để chặn hai thao tác đồng thời (thanh toán và hủy quá hạn).
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerOrder extends BaseEntity {

    /** Mã đơn hiển thị cho khách, duy nhất. */
    @Column(name = "order_code", nullable = false, unique = true, length = 20)
    private String orderCode;

    /** Id người đặt. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Trạng thái đơn. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    /** Phương thức thanh toán. */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 10)
    private PaymentMethod paymentMethod;

    /** Tổng tiền đơn hàng. */
    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    /** Tên người nhận. */
    @Column(name = "receiver_name", nullable = false, length = 100)
    private String receiverName;

    /** Số điện thoại người nhận. */
    @Column(name = "receiver_phone", nullable = false, length = 20)
    private String receiverPhone;

    /** Địa chỉ giao hàng. */
    @Column(name = "shipping_address", nullable = false, length = 500)
    private String shippingAddress;

    /** Ghi chú của khách. */
    @Column(length = 255)
    private String note;

    /** Khóa idempotency do client gửi, duy nhất theo từng người dùng. */
    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    /** Thời điểm thanh toán. */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /** Thời điểm hủy. */
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /** Nguyên nhân hủy. */
    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason", length = 30)
    private CancelReason cancelReason;

    /** Số phiên bản phục vụ khóa lạc quan. */
    @Version
    private Long version;

    /** Cờ xóa mềm. */
    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    /** Các dòng sản phẩm của đơn. */
    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    /**
     * Thêm một dòng sản phẩm vào đơn và thiết lập quan hệ hai chiều.
     *
     * @param item dòng sản phẩm cần thêm
     */
    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }
}
