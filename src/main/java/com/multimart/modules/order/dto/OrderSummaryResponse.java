package com.multimart.modules.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Thông tin tóm tắt một đơn hàng dùng trong danh sách.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderSummaryResponse {

    /** Id đơn hàng. */
    private Long orderId;

    /** Mã đơn hiển thị. */
    private String orderCode;

    /** Trạng thái đơn (tên enum). */
    private String status;

    /** Phương thức thanh toán (tên enum). */
    private String paymentMethod;

    /** Tổng tiền. */
    private BigDecimal totalAmount;

    /** Số dòng sản phẩm. */
    private long itemCount;

    /** Thời điểm tạo đơn. */
    private LocalDateTime createdAt;
}
