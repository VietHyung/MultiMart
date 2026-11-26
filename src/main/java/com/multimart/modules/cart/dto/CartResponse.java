package com.multimart.modules.cart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Giỏ hàng của người dùng kèm tổng tiền (chỉ tính các dòng còn khả dụng).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartResponse {

    /** Các dòng trong giỏ, sắp theo id sản phẩm. */
    @Builder.Default
    private List<CartItemResponse> items = new ArrayList<>();

    /** Số dòng trong giỏ. */
    private int totalItems;

    /** Tổng tiền của các dòng khả dụng. */
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;
}
