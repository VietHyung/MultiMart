package com.multimart.modules.flashsale.entity;

import com.multimart.common.base.BaseEntity;
import com.multimart.modules.product.entity.Product;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "flash_sale_products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashSaleProduct extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private FlashSaleEvent event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "flash_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal flashPrice;

    @Column(name = "total_allocated", nullable = false)
    private Integer totalAllocated;

    @Column(name = "available_stock", nullable = false)
    private Integer availableStock;

    @Builder.Default
    @Column(name = "purchase_limit_per_user", nullable = false)
    private Integer purchaseLimitPerUser = 1;

    /**
     * Optimistic Locking: Tự động kiểm tra phiên bản bản ghi khi update dưới Database,
     * ngăn chặn 2 thread ghi đè dữ liệu lên nhau.
     */
    @Version
    private Long version;
}
