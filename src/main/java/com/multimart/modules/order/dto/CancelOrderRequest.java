package com.multimart.modules.order.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu hủy đơn (nội dung tùy chọn).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CancelOrderRequest {

    /** Lý do khách nhập (chỉ để ghi log). */
    @Size(max = 255, message = "Lý do tối đa 255 ký tự")
    private String reason;
}
