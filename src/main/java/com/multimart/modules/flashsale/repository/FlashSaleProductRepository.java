package com.multimart.modules.flashsale.repository;

import com.multimart.modules.flashsale.entity.FlashSaleProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository thao tác với bảng dữ liệu sản phẩm trong đợt săn sale (flash_sale_products).
 */
@Repository
public interface FlashSaleProductRepository extends JpaRepository<FlashSaleProduct, Long> {

    /**
     * Lấy toàn bộ danh sách sản phẩm giảm giá thuộc về một sự kiện Flash-Sale cụ thể.
     *
     * @param eventId mã định danh sự kiện Flash-Sale
     * @return danh sách các FlashSaleProduct
     */
    List<FlashSaleProduct> findByEventId(Long eventId);

    /**
     * Tìm kiếm bản ghi sản phẩm sale theo mã sự kiện và mã sản phẩm gốc.
     *
     * @param eventId   mã sự kiện Flash-Sale
     * @param productId mã sản phẩm gốc
     * @return Optional chứa FlashSaleProduct nếu tìm thấy
     */
    Optional<FlashSaleProduct> findByEventIdAndProductId(Long eventId, Long productId);

    /**
     * Kiểm tra xem một sản phẩm gốc đã từng được đưa vào sự kiện Flash-Sale này hay chưa.
     *
     * @param eventId   mã sự kiện
     * @param productId mã sản phẩm
     * @return true nếu đã có trong sự kiện, ngược lại false
     */
    boolean existsByEventIdAndProductId(Long eventId, Long productId);
}
