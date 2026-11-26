package com.multimart.common.exception;

import lombok.Getter;

import java.util.List;

/**
 * Ngoại lệ nghiệp vụ: một hoặc nhiều sản phẩm không đủ tồn kho khi đặt hàng.
 * Kế thừa {@link AppException} và mang kèm danh sách id sản phẩm bị thiếu.
 */
@Getter
public class InsufficientStockException extends AppException {

    /** Danh sách id sản phẩm không đủ hàng hoặc không còn được bán. */
    private final List<Long> productIds;

    /**
     * Khởi tạo ngoại lệ thiếu tồn kho.
     *
     * @param productIds danh sách id sản phẩm bị thiếu
     */
    public InsufficientStockException(List<Long> productIds) {
        super(ErrorCode.INSUFFICIENT_STOCK);
        this.productIds = List.copyOf(productIds);
    }
}
