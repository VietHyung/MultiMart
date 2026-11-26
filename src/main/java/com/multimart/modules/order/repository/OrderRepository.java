package com.multimart.modules.order.repository;

import com.multimart.modules.order.entity.CustomerOrder;
import com.multimart.modules.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository truy vấn đơn hàng thường. Các truy vấn chi tiết dùng EntityGraph để tải kèm dòng sản phẩm.
 */
@Repository
public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {

    /**
     * Tìm đơn chưa xóa theo id, tải kèm các dòng sản phẩm.
     *
     * @param id id đơn hàng
     * @return đơn hàng nếu tồn tại
     */
    @EntityGraph(attributePaths = "items")
    Optional<CustomerOrder> findDetailByIdAndDeletedFalse(Long id);

    /**
     * Tìm đơn theo người dùng và khóa idempotency, tải kèm các dòng sản phẩm.
     *
     * @param userId         id người dùng
     * @param idempotencyKey khóa idempotency
     * @return đơn hàng nếu đã tạo trước đó
     */
    @EntityGraph(attributePaths = "items")
    Optional<CustomerOrder> findDetailByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    /**
     * Danh sách đơn của một người dùng (không tải dòng sản phẩm để tránh N+1).
     *
     * @param userId   id người dùng
     * @param pageable phân trang và sắp xếp
     * @return trang đơn hàng
     */
    Page<CustomerOrder> findByUserIdAndDeletedFalse(Long userId, Pageable pageable);

    /**
     * Danh sách đơn của một người dùng lọc theo trạng thái.
     *
     * @param userId   id người dùng
     * @param status   trạng thái cần lọc
     * @param pageable phân trang và sắp xếp
     * @return trang đơn hàng
     */
    Page<CustomerOrder> findByUserIdAndStatusAndDeletedFalse(Long userId, OrderStatus status, Pageable pageable);

    /**
     * Danh sách mọi đơn (Admin).
     *
     * @param pageable phân trang và sắp xếp
     * @return trang đơn hàng
     */
    Page<CustomerOrder> findByDeletedFalse(Pageable pageable);

    /**
     * Danh sách mọi đơn lọc theo trạng thái (Admin).
     *
     * @param status   trạng thái cần lọc
     * @param pageable phân trang và sắp xếp
     * @return trang đơn hàng
     */
    Page<CustomerOrder> findByStatusAndDeletedFalse(OrderStatus status, Pageable pageable);

    /**
     * Đếm số dòng sản phẩm của nhiều đơn bằng một truy vấn.
     *
     * @param orderIds danh sách id đơn
     * @return danh sách mảng [orderId, số dòng]
     */
    @Query("select i.order.id, count(i) from OrderItem i where i.order.id in :orderIds group by i.order.id")
    List<Object[]> countItemsByOrderIds(@Param("orderIds") Collection<Long> orderIds);

    /**
     * Lấy tối đa 100 đơn cũ nhất theo trạng thái và tạo trước một mốc thời gian (job dự phòng hủy quá hạn).
     *
     * @param status trạng thái cần quét
     * @param before mốc thời gian
     * @return danh sách đơn
     */
    List<CustomerOrder> findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(OrderStatus status, LocalDateTime before);

    /**
     * Kiểm tra đơn hàng thuộc người dùng, đã giao ({@code DELIVERED}) và có chứa sản phẩm.
     *
     * @param orderId   id đơn hàng
     * @param userId    id người dùng sở hữu đơn
     * @param productId id sản phẩm
     * @return {@code true} nếu đủ điều kiện đánh giá
     */
    @Query("select count(o) > 0 from CustomerOrder o join o.items i "
            + "where o.id = :orderId and o.userId = :userId and o.deleted = false "
            + "and o.status = com.multimart.modules.order.entity.OrderStatus.DELIVERED and i.productId = :productId")
    boolean existsDeliveredOrderWithProduct(@Param("orderId") Long orderId, @Param("userId") Long userId,
                                            @Param("productId") Long productId);
}
