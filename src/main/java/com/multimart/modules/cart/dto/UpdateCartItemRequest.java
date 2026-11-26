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
 * Yêu cầu đặt lại số lượng của một dòng trong giỏ hàng.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCartItemRequest {

    /** Số lượng mới (1-99). Muốn bỏ sản phẩm hãy dùng API xóa dòng. */
    @NotNull(message = "Số lượng không được để trống")
    @Min(value = 1, message = "Số lượng tối thiểu là 1")
    @Max(value = 99, message = "Số lượng tối đa là 99")
    private Integer quantity;
}
