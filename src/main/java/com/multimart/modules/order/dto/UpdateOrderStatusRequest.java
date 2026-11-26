package com.multimart.modules.order.dto;

import com.multimart.modules.order.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu Admin chuyển trạng thái đơn hàng.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderStatusRequest {

    /** Trạng thái đích: SHIPPING, DELIVERED hoặc CANCELLED. */
    @NotNull(message = "Trạng thái không được để trống")
    private OrderStatus status;
}
