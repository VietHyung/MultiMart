package com.multimart.modules.cart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Một dòng trong giỏ hàng khi hiển thị (giá và tồn kho lấy tại thời điểm xem).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemResponse {

    /** Id sản phẩm. */
    private Long productId;

    /** Tên sản phẩm hiện tại. */
    private String productName;

    /** Ảnh sản phẩm. */
    private String imageUrl;

    /** Giá hiện tại của sản phẩm. */
    private BigDecimal unitPrice;

    /** Số lượng trong giỏ. */
    private Integer quantity;

    /** Thành tiền = đơn giá x số lượng. */
    private BigDecimal lineTotal;

    /** Tồn kho hiện tại. */
    private Integer availableStock;

    /** true nếu sản phẩm còn bán và đủ hàng cho số lượng trong giỏ. */
    private boolean available;
}
