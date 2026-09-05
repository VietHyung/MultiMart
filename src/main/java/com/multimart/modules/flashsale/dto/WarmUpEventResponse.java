package com.multimart.modules.flashsale.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO phản hồi kết quả quá trình làm nóng dữ liệu Flash Sale lên Redis Cache.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarmUpEventResponse {

    /**
     * ID của sự kiện Flash Sale được làm nóng.
     */
    private Long eventId;

    /**
     * Tên của sự kiện Flash Sale.
     */
    private String eventName;

    /**
     * Số lượng sản phẩm đã được nạp tồn kho lên Redis Cache thành công.
     */
    private Integer warmedUpProductsCount;

    /**
     * Thông báo kết quả thực thi.
     */
    private String message;
}
