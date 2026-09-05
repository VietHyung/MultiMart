package com.multimart.modules.flashsale.service;

import com.multimart.common.exception.AppException;
import com.multimart.common.exception.ErrorCode;
import com.multimart.common.response.PageResponse;
import com.multimart.modules.flashsale.dto.FlashSaleOrderRequest;
import com.multimart.modules.flashsale.dto.FlashSaleOrderResponse;
import com.multimart.modules.flashsale.dto.WarmUpEventResponse;
import com.multimart.modules.flashsale.entity.FlashSaleEvent;
import com.multimart.modules.flashsale.entity.FlashSaleOrder;
import com.multimart.modules.flashsale.entity.FlashSaleOrderStatus;
import com.multimart.modules.flashsale.entity.FlashSaleProduct;
import com.multimart.modules.flashsale.repository.FlashSaleEventRepository;
import com.multimart.modules.flashsale.repository.FlashSaleOrderRepository;
import com.multimart.modules.flashsale.repository.FlashSaleProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service xử lý động cơ Flash Sale chịu tải cao (High-Concurrency Flash Sale Engine).
 * Sử dụng Redis Cache và Lua Script để đảm bảo tính nguyên tử (Atomicity),
 * ngăn chặn hiện tượng bán âm hàng (Overselling) và kiểm soát giới hạn mua của người dùng.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlashSaleEngineService {

    private final FlashSaleEventRepository eventRepository;
    private final FlashSaleProductRepository flashSaleProductRepository;
    private final FlashSaleOrderRepository flashSaleOrderRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> flashSaleStockDeductScript;

    /**
     * Làm nóng dữ liệu (Cache Warm-up) sự kiện Flash Sale lên Redis trước giờ mở bán.
     * Nạp số lượng tồn kho khả dụng của từng sản phẩm vào Redis keys dạng string.
     *
     * @param eventId Mã định danh sự kiện Flash Sale cần warm-up
     * @return DTO thông báo kết quả warm-up
     */
    @Transactional(readOnly = true)
    public WarmUpEventResponse warmUpEvent(Long eventId) {
        FlashSaleEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new AppException(ErrorCode.EVENT_NOT_FOUND));

        if ("ENDED".equalsIgnoreCase(event.getStatus())) {
            throw new AppException(ErrorCode.EVENT_NOT_ACTIVE);
        }

        List<FlashSaleProduct> products = flashSaleProductRepository.findByEventId(eventId);
        int warmedUpCount = 0;

        for (FlashSaleProduct product : products) {
            String stockKey = "flashsale:stock:" + eventId + ":" + product.getProduct().getId();
            // Nạp số lượng tồn kho khả dụng lên Redis
            stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(product.getAvailableStock()));
            warmedUpCount++;
            log.info("Warmed up flash sale stock for key {}: {} items", stockKey, product.getAvailableStock());
        }

        return WarmUpEventResponse.builder()
                .eventId(eventId)
                .eventName(event.getName())
                .warmedUpProductsCount(warmedUpCount)
                .message("Đã nạp thành công dữ liệu " + warmedUpCount + " sản phẩm lên Redis Cache")
                .build();
    }

    /**
     * Đặt mua sản phẩm Flash Sale với khả năng chịu tải hàng trăm nghìn RPS.
     * Quy trình xử lý:
     * 1. Kiểm tra trạng thái sự kiện còn hiệu lực.
     * 2. Thực thi Redis Lua Script để kiểm tra giới hạn mua và trừ kho nguyên tử trong RAM.
     * 3. Khi Redis trừ kho thành công, đồng bộ trừ kho PostgreSQL và tạo bản ghi FlashSaleOrder.
     *
     * @param userId  Mã định danh người dùng mua hàng
     * @param request DTO chứa thông tin sự kiện, sản phẩm và số lượng mua
     * @return DTO chứa thông tin đơn hàng Flash Sale đã tạo
     */
    @Transactional
    public FlashSaleOrderResponse placeOrder(Long userId, FlashSaleOrderRequest request) {
        // 1. Kiểm tra sự kiện
        FlashSaleEvent event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new AppException(ErrorCode.EVENT_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(event.getStartTime()) || now.isAfter(event.getEndTime()) || !"ACTIVE".equalsIgnoreCase(event.getStatus())) {
            throw new AppException(ErrorCode.EVENT_NOT_ACTIVE);
        }

        // 2. Tìm thông tin sản phẩm trong đợt sale
        FlashSaleProduct flashSaleProduct = flashSaleProductRepository.findByEventIdAndProductId(
                        request.getEventId(), request.getProductId())
                .orElseThrow(() -> new AppException(ErrorCode.FLASH_SALE_PRODUCT_NOT_FOUND));

        // 3. Thực thi trừ tồn kho nguyên tử qua Redis Lua Script
        String stockKey = "flashsale:stock:" + request.getEventId() + ":" + request.getProductId();
        String buyersKey = "flashsale:buyers:" + request.getEventId() + ":" + request.getProductId();

        List<String> keys = List.of(stockKey, buyersKey);
        Long luaResult = stringRedisTemplate.execute(
                flashSaleStockDeductScript,
                keys,
                userId.toString(),
                request.getQuantity().toString(),
                flashSaleProduct.getPurchaseLimitPerUser().toString()
        );

        if (luaResult == null || luaResult == -3L) {
            log.warn("Flash sale product not warmed up on Redis: eventId={}, productId={}", request.getEventId(), request.getProductId());
            throw new AppException(ErrorCode.FLASH_SALE_NOT_WARMED_UP);
        }

        if (luaResult == -2L) {
            log.warn("User {} exceeded purchase limit for flash sale product {}", userId, request.getProductId());
            throw new AppException(ErrorCode.PURCHASE_LIMIT_EXCEEDED);
        }

        if (luaResult == -1L) {
            log.warn("Flash sale product {} is out of stock in Redis", request.getProductId());
            throw new AppException(ErrorCode.OUT_OF_STOCK);
        }

        // 4. Trừ kho PostgreSQL và tạo đơn hàng
        flashSaleProduct.setAvailableStock(flashSaleProduct.getAvailableStock() - request.getQuantity());
        flashSaleProductRepository.save(flashSaleProduct);

        BigDecimal unitPrice = flashSaleProduct.getFlashPrice();
        BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(request.getQuantity()));

        FlashSaleOrder order = FlashSaleOrder.builder()
                .userId(userId)
                .flashSaleEventId(request.getEventId())
                .flashSaleProductId(flashSaleProduct.getId())
                .quantity(request.getQuantity())
                .unitPrice(unitPrice)
                .totalPrice(totalPrice)
                .status(FlashSaleOrderStatus.PENDING)
                .build();

        FlashSaleOrder savedOrder = flashSaleOrderRepository.save(order);
        log.info("Flash sale order created successfully: orderId={}, userId={}, remainingStockInRedis={}",
                savedOrder.getId(), userId, luaResult);

        return mapToOrderResponse(savedOrder, request.getProductId());
    }

    /**
     * Lấy danh sách lịch sử các đơn hàng Flash Sale của người dùng hiện tại theo phân trang.
     *
     * @param userId Mã định danh người dùng
     * @param page   Vị trí trang
     * @param size   Số lượng bản ghi trên một trang
     * @return DTO phân trang chứa danh sách đơn hàng
     */
    @Transactional(readOnly = true)
    public PageResponse<FlashSaleOrderResponse> getMyOrders(Long userId, int page, int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                page, size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")
        );
        org.springframework.data.domain.Page<FlashSaleOrder> orderPage = flashSaleOrderRepository.findByUserId(userId, pageable);

        java.util.List<FlashSaleOrderResponse> dtoList = orderPage.getContent().stream()
                .map(order -> {
                    FlashSaleProduct fsp = flashSaleProductRepository.findById(order.getFlashSaleProductId()).orElse(null);
                    Long productId = (fsp != null && fsp.getProduct() != null) ? fsp.getProduct().getId() : null;
                    return mapToOrderResponse(order, productId);
                })
                .toList();

        return PageResponse.<FlashSaleOrderResponse>builder()
                .content(dtoList)
                .pageNumber(orderPage.getNumber())
                .pageSize(orderPage.getSize())
                .totalElements(orderPage.getTotalElements())
                .totalPages(orderPage.getTotalPages())
                .isLast(orderPage.isLast())
                .build();
    }

    /**
     * Chuyển đổi FlashSaleOrder Entity sang DTO FlashSaleOrderResponse.
     *
     * @param order     Thực thể đơn hàng Flash Sale
     * @param productId ID sản phẩm gốc
     * @return DTO FlashSaleOrderResponse
     */
    private FlashSaleOrderResponse mapToOrderResponse(FlashSaleOrder order, Long productId) {
        return FlashSaleOrderResponse.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .eventId(order.getFlashSaleEventId())
                .productId(productId)
                .quantity(order.getQuantity())
                .unitPrice(order.getUnitPrice())
                .totalPrice(order.getTotalPrice())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt() != null ? order.getCreatedAt() : LocalDateTime.now())
                .build();
    }
}
