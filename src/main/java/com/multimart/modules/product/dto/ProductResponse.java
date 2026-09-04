package com.multimart.modules.product.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {
    private Long id;
    private String name;
    private String description;
    private String imageUrl;
    private BigDecimal originalPrice;
    private Integer totalStock;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
