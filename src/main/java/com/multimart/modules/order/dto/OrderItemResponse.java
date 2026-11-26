package com.multimart.modules.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Một dòng sản phẩm trong đơn hàng khi trả về cho client.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemResponse {

    /** Id sản phẩm. */
    private Long productId;

    /** Tên sản phẩm tại thời điểm mua. */
    private String productName;

    /** Đơn giá tại thời điểm mua. */
    private BigDecimal unitPrice;

    /** Số lượng. */
    private Integer quantity;

    /** Thành tiền. */
    private BigDecimal lineTotal;
}
