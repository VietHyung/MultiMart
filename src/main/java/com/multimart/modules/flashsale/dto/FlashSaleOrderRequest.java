package com.multimart.modules.flashsale.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO yêu cầu đặt mua sản phẩm trong sự kiện Flash Sale.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashSaleOrderRequest {

    /**
     * ID của sự kiện Flash Sale.
     */
    @NotNull(message = "ID sự kiện Flash Sale không được để trống")
    private Long eventId;

    /**
     * ID của sản phẩm gốc cần đặt mua.
     */
    @NotNull(message = "ID sản phẩm không được để trống")
    private Long productId;

    /**
     * Số lượng sản phẩm muốn mua.
     */
    @NotNull(message = "Số lượng mua không được để trống")
    @Min(value = 1, message = "Số lượng mua tối thiểu phải từ 1 trở lên")
    private Integer quantity;
}
