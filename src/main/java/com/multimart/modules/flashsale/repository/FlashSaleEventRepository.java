package com.multimart.modules.flashsale.repository;

import com.multimart.modules.flashsale.entity.FlashSaleEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository thao tác với bảng dữ liệu sự kiện săn sale (flash_sale_events).
 */
@Repository
public interface FlashSaleEventRepository extends JpaRepository<FlashSaleEvent, Long> {

    /**
     * Tìm kiếm danh sách các sự kiện Flash-Sale đang trong khung giờ diễn ra (startTime <= now <= endTime).
     * Sắp xếp ưu tiên các sự kiện sắp hết hạn lên đầu.
     *
     * @param now thời điểm hiện tại của hệ thống
     * @return danh sách các sự kiện đang hoạt động
     */
    @Query("SELECT e FROM FlashSaleEvent e WHERE e.startTime <= :now AND e.endTime >= :now ORDER BY e.endTime ASC")
    List<FlashSaleEvent> findActiveEvents(@Param("now") LocalDateTime now);

    /**
     * Tìm kiếm các sự kiện sắp diễn ra trong tương lai (startTime > now).
     * Sắp xếp theo thứ tự thời gian bắt đầu gần nhất.
     *
     * @param now thời điểm hiện tại
     * @return danh sách các sự kiện sắp mở bán
     */
    @Query("SELECT e FROM FlashSaleEvent e WHERE e.startTime > :now ORDER BY e.startTime ASC")
    List<FlashSaleEvent> findUpcomingEvents(@Param("now") LocalDateTime now);

    /**
     * Lấy danh sách sự kiện theo trạng thái (UPCOMING, ACTIVE, ENDED).
     *
     * @param status trạng thái của sự kiện
     * @return danh sách sự kiện khớp trạng thái
     */
    List<FlashSaleEvent> findByStatus(String status);
}
