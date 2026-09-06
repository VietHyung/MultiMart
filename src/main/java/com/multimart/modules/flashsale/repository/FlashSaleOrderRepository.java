package com.multimart.modules.flashsale.repository;

import com.multimart.modules.flashsale.entity.FlashSaleOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository thao tác dữ liệu với bảng đơn hàng Flash Sale (flash_sale_orders).
 */
@Repository
public interface FlashSaleOrderRepository extends JpaRepository<FlashSaleOrder, Long> {

    /**
     * Lấy danh sách đơn hàng Flash Sale của một người dùng theo phân trang.
     *
     * @param userId   ID của người dùng
     * @param pageable Thông tin phân trang
     * @return Danh sách đơn hàng phân trang
     */
    Page<FlashSaleOrder> findByUserId(Long userId, Pageable pageable);

    /**
     * Lấy danh sách đơn hàng thuộc một sự kiện Flash Sale cụ thể.
     *
     * @param flashSaleEventId ID sự kiện Flash Sale
     * @param pageable         Thông tin phân trang
     * @return Trang danh sách đơn hàng
     */
    Page<FlashSaleOrder> findByFlashSaleEventId(Long flashSaleEventId, Pageable pageable);

    /**
     * Tìm tất cả đơn hàng của một người dùng đối với một sản phẩm trong sự kiện.
     *
     * @param userId             ID người dùng
     * @param flashSaleEventId   ID sự kiện
     * @param flashSaleProductId ID sản phẩm Flash Sale
     * @return Danh sách các đơn hàng tương ứng
     */
    List<FlashSaleOrder> findByUserIdAndFlashSaleEventIdAndFlashSaleProductId(
            Long userId, Long flashSaleEventId, Long flashSaleProductId);

    /**
     * Tìm kiếm đơn hàng Flash Sale theo mã theo dõi duy nhất (orderTrackingId).
     *
     * @param orderTrackingId Mã theo dõi UUID
     * @return Optional chứa FlashSaleOrder nếu tìm thấy
     */
    java.util.Optional<FlashSaleOrder> findByOrderTrackingId(String orderTrackingId);

    /**
     * Kiểm tra xem đơn hàng với mã theo dõi này đã được xử lý vào DB hay chưa.
     *
     * @param orderTrackingId Mã theo dõi UUID
     * @return true nếu đã tồn tại, ngược lại false
     */
    boolean existsByOrderTrackingId(String orderTrackingId);
}
