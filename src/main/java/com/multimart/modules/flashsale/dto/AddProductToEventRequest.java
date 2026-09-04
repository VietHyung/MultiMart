package com.multimart.modules.flashsale.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddProductToEventRequest {

    @NotNull(message = "Product ID không được để trống")
    private Long productId;

    @NotNull(message = "Giá Flash-Sale không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá Flash-Sale phải lớn hơn 0")
    private BigDecimal flashPrice;

    @NotNull(message = "Số lượng phân bổ không được để trống")
    @Min(value = 1, message = "Số lượng phân bổ tối thiểu là 1")
    private Integer totalAllocated;

    @NotNull(message = "Giới hạn mua mỗi user không được để trống")
    @Min(value = 1, message = "Giới hạn mua tối thiểu là 1")
    private Integer purchaseLimitPerUser;
}
