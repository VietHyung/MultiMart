package com.multimart.modules.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Chi tiết đơn hàng thường trả về cho client.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderResponse {

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

    /** Hạn thanh toán, chỉ có khi đơn đang PENDING_PAYMENT. */
    private LocalDateTime paymentDeadline;

    /** Các dòng sản phẩm. */
    private List<OrderItemResponse> items;

    /** Tên người nhận. */
    private String receiverName;

    /** Số điện thoại người nhận. */
    private String receiverPhone;

    /** Địa chỉ giao hàng. */
    private String shippingAddress;

    /** Ghi chú. */
    private String note;

    /** Thời điểm thanh toán. */
    private LocalDateTime paidAt;

    /** Thời điểm hủy. */
    private LocalDateTime cancelledAt;

    /** Nguyên nhân hủy (tên enum). */
    private String cancelReason;

    /** Thời điểm tạo đơn. */
    private LocalDateTime createdAt;
}
