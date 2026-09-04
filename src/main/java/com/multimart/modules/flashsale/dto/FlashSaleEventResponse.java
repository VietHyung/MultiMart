package com.multimart.modules.flashsale.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashSaleEventResponse {
    private Long id;
    private String name;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private Long remainingSeconds; // Số giây còn lại của đợt sale (phục vụ đồng hồ đếm ngược Frontend)
    private List<FlashSaleProductResponse> products;
}
