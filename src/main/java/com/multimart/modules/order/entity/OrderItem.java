package com.multimart.modules.order.entity;

import com.multimart.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Một dòng sản phẩm của đơn hàng, lưu snapshot tên và giá tại thời điểm mua.
 */
@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem extends BaseEntity {

    /** Đơn hàng chứa dòng này. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    /** Id sản phẩm. */
    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** Tên sản phẩm tại thời điểm mua. */
    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    /** Đơn giá tại thời điểm mua. */
    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    /** Số lượng mua. */
    @Column(nullable = false)
    private Integer quantity;

    /** Thành tiền = đơn giá x số lượng. */
    @Column(name = "line_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal lineTotal;
}
