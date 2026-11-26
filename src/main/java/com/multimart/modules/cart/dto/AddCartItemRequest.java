package com.multimart.modules.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu thêm sản phẩm vào giỏ hàng.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddCartItemRequest {

    /** Id sản phẩm cần thêm. */
    @NotNull(message = "Mã sản phẩm không được để trống")
    private Long productId;

    /** Số lượng thêm (1-99), được cộng dồn nếu sản phẩm đã có trong giỏ. */
    @NotNull(message = "Số lượng không được để trống")
    @Min(value = 1, message = "Số lượng tối thiểu là 1")
    @Max(value = 99, message = "Số lượng tối đa là 99")
    private Integer quantity;
}
