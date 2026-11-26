package com.multimart.modules.order.dto;

import com.multimart.modules.order.entity.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Yêu cầu đặt hàng từ giỏ hàng.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutRequest {

    /** Phương thức thanh toán: COD hoặc ONLINE. */
    @NotNull(message = "Phương thức thanh toán không được để trống")
    private PaymentMethod paymentMethod;

    /** Các sản phẩm trong giỏ muốn đặt; null hoặc rỗng nghĩa là đặt toàn bộ giỏ. */
    private List<Long> productIds;

    /** Tên người nhận. */
    @NotBlank(message = "Tên người nhận không được để trống")
    @Size(max = 100, message = "Tên người nhận tối đa 100 ký tự")
    private String receiverName;

    /** Số điện thoại người nhận (10 số, bắt đầu bằng 0). */
    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0")
    private String receiverPhone;

    /** Địa chỉ giao hàng. */
    @NotBlank(message = "Địa chỉ giao hàng không được để trống")
    @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
    private String shippingAddress;

    /** Ghi chú (tùy chọn). */
    @Size(max = 255, message = "Ghi chú tối đa 255 ký tự")
    private String note;
}
