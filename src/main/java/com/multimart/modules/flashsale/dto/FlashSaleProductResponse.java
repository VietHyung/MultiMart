package com.multimart.modules.flashsale.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashSaleProductResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String imageUrl;
    private BigDecimal originalPrice;
    private BigDecimal flashPrice;
    private Integer discountPercentage;
    private Integer totalAllocated;
    private Integer availableStock;
    private Integer purchaseLimitPerUser;
    private boolean soldOut;
}
