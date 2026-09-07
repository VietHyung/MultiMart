package com.multimart.modules.flashsale.service;

import com.multimart.common.exception.AppException;
import com.multimart.common.exception.ErrorCode;
import com.multimart.common.response.PageResponse;
import com.multimart.modules.flashsale.dto.*;
import com.multimart.modules.flashsale.dto.FlashSaleOrderTimeoutMessage;
import com.multimart.modules.flashsale.entity.FlashSaleEvent;
import com.multimart.modules.flashsale.entity.FlashSaleOrder;
import com.multimart.modules.flashsale.entity.FlashSaleOrderStatus;
import com.multimart.modules.flashsale.entity.FlashSaleProduct;
import com.multimart.modules.flashsale.mq.FlashSaleOrderProducer;
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
    private final DefaultRedisScript<Long> flashSaleStockRollbackScript;
    private final FlashSaleOrderProducer flashSaleOrderProducer;

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
     * Tiếp nhận yêu cầu đặt mua Flash Sale theo mô hình xử lý bất đồng bộ (Asynchronous Order Processing).
     * 1. Trừ kho nguyên tử trên RAM với Redis Lua Script (< 2ms).
     * 2. Sinh mã orderTrackingId (UUID).
     * 3. Gửi message FlashSaleOrderMessage vào RabbitMQ Exchange để ghi đĩa ngầm.
     * 4. Trả về ngay lập tức phản hồi cho Client (< 10ms) giải phóng kết nối.
     *
     * @param userId  ID người dùng đặt mua
     * @param request DTO yêu cầu đặt mua
     * @return DTO AsyncOrderSubmitResponse kèm mã theo dõi
     */
    public AsyncOrderSubmitResponse submitOrderAsync(Long userId, FlashSaleOrderRequest request) {
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

        // 4. Sinh mã tracking và gửi tin nhắn vào RabbitMQ
        String orderTrackingId = java.util.UUID.randomUUID().toString();
        BigDecimal unitPrice = flashSaleProduct.getFlashPrice();
        BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(request.getQuantity()));

        FlashSaleOrderMessage orderMessage = FlashSaleOrderMessage.builder()
                .orderTrackingId(orderTrackingId)
                .userId(userId)
                .eventId(request.getEventId())
                .productId(request.getProductId())
                .flashSaleProductId(flashSaleProduct.getId())
                .quantity(request.getQuantity())
                .unitPrice(unitPrice)
                .totalPrice(totalPrice)
                .createdAt(LocalDateTime.now())
                .build();

        // Ghi nhận trạng thái PENDING_PROCESSING trên Redis
        String trackingKey = "flashsale:order:tracking:" + orderTrackingId;
        stringRedisTemplate.opsForValue().set(trackingKey, "PENDING_PROCESSING", 24, java.util.concurrent.TimeUnit.HOURS);

        // Đẩy vào hàng đợi RabbitMQ
        flashSaleOrderProducer.sendOrderMessage(orderMessage);

        log.info("Flash sale order request accepted asynchronously: trackingId={}, userId={}, remainingStockInRedis={}",
                orderTrackingId, userId, luaResult);

        return AsyncOrderSubmitResponse.builder()
                .orderTrackingId(orderTrackingId)
                .status("PENDING_PROCESSING")
                .eventId(request.getEventId())
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .message("Yêu cầu đặt mua Flash Sale đã được tiếp nhận và đang xử lý")
                .build();
    }

    /**
     * Tra cứu trạng thái tiến trình xử lý đơn hàng bất đồng bộ dựa trên mã orderTrackingId.
     *
     * @param orderTrackingId Mã theo dõi UUID
     * @return DTO OrderTrackingResponse chứa trạng thái hiện tại
     */
    @Transactional(readOnly = true)
    public OrderTrackingResponse getTrackingStatus(String orderTrackingId) {
        // Kiểm tra trong DB trước
        java.util.Optional<FlashSaleOrder> orderOpt = flashSaleOrderRepository.findByOrderTrackingId(orderTrackingId);
        if (orderOpt.isPresent()) {
            FlashSaleOrder order = orderOpt.get();
            FlashSaleProduct fsp = flashSaleProductRepository.findById(order.getFlashSaleProductId()).orElse(null);
            Long productId = (fsp != null && fsp.getProduct() != null) ? fsp.getProduct().getId() : null;

            return OrderTrackingResponse.builder()
                    .orderTrackingId(orderTrackingId)
                    .trackingStatus("SUCCESS")
                    .orderId(order.getId())
                    .userId(order.getUserId())
                    .eventId(order.getFlashSaleEventId())
                    .productId(productId)
                    .quantity(order.getQuantity())
                    .unitPrice(order.getUnitPrice())
                    .totalPrice(order.getTotalPrice())
                    .orderStatus(order.getStatus())
                    .createdAt(order.getCreatedAt())
                    .build();
        }

        // Nếu chưa có trong DB, kiểm tra cache Redis
        String trackingKey = "flashsale:order:tracking:" + orderTrackingId;
        String redisStatus = stringRedisTemplate.opsForValue().get(trackingKey);
        if (redisStatus != null && redisStatus.startsWith("PENDING_PROCESSING")) {
            return OrderTrackingResponse.builder()
                    .orderTrackingId(orderTrackingId)
                    .trackingStatus("PENDING_PROCESSING")
                    .build();
        }

        throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin đơn hàng với mã theo dõi: " + orderTrackingId);
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

    /**
     * Xác nhận thanh toán đơn hàng Flash Sale trước khi hết hạn.
     * Chuyển trạng thái đơn hàng từ PENDING sang CONFIRMED.
     *
     * @param userId  ID người dùng thực hiện thanh toán
     * @param orderId ID đơn hàng cần thanh toán
     * @return DTO FlashSaleOrderPaymentResponse xác nhận thanh toán thành công
     */
    @Transactional
    public FlashSaleOrderPaymentResponse confirmPayment(Long userId, Long orderId) {
        FlashSaleOrder order = flashSaleOrderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        // Kiểm tra quyền sở hữu đơn hàng
        if (!order.getUserId().equals(userId)) {
            throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        // Kiểm tra trạng thái đơn hàng
        if (order.getStatus() == FlashSaleOrderStatus.CANCELLED) {
            throw new AppException(ErrorCode.ORDER_ALREADY_CANCELLED);
        }

        if (order.getStatus() == FlashSaleOrderStatus.CONFIRMED) {
            throw new AppException(ErrorCode.ORDER_ALREADY_PAID);
        }

        // Cập nhật trạng thái sang CONFIRMED
        order.setStatus(FlashSaleOrderStatus.CONFIRMED);
        FlashSaleOrder updatedOrder = flashSaleOrderRepository.save(order);

        // Cập nhật cache trạng thái trên Redis
        String trackingKey = "flashsale:order:tracking:" + order.getOrderTrackingId();
        stringRedisTemplate.opsForValue().set(trackingKey, "CONFIRMED:" + order.getId(), 24, java.util.concurrent.TimeUnit.HOURS);

        log.info("Flash sale order payment confirmed successfully: orderId={}, userId={}", orderId, userId);

        return FlashSaleOrderPaymentResponse.builder()
                .orderId(updatedOrder.getId())
                .orderTrackingId(updatedOrder.getOrderTrackingId())
                .status(updatedOrder.getStatus())
                .totalPrice(updatedOrder.getTotalPrice())
                .paidAt(LocalDateTime.now())
                .message("Thanh toán đơn hàng Flash Sale thành công")
                .build();
    }

    /**
     * Thực hiện hủy đơn hàng quá hạn và hoàn trả tồn kho trên cả Database lẫn Redis.
     * Được gọi bởi Consumer lắng nghe Dead Letter Queue (flashsale.order.cancel.queue).
     *
     * @param message DTO chứa thông tin đơn hàng cần kiểm tra và hủy
     * @return true nếu đơn hàng được hủy và hoàn kho thành công, false nếu đơn đã thanh toán hoặc không tồn tại
     */
    @Transactional
    public boolean rollbackOrderStock(FlashSaleOrderTimeoutMessage message) {
        FlashSaleOrder order = flashSaleOrderRepository.findById(message.getOrderId()).orElse(null);
        if (order == null) {
            log.warn("Order not found for timeout cancellation: orderId={}", message.getOrderId());
            return false;
        }

        // Chỉ hủy khi đơn hàng vẫn ở trạng thái PENDING
        if (order.getStatus() != FlashSaleOrderStatus.PENDING) {
            log.info("Order {} is not PENDING (current status: {}), skipping cancellation and rollback",
                    order.getId(), order.getStatus());
            return false;
        }

        // 1. Chuyển trạng thái đơn hàng thành CANCELLED
        order.setStatus(FlashSaleOrderStatus.CANCELLED);
        flashSaleOrderRepository.save(order);

        // 2. Hoàn trả tồn kho DB
        flashSaleProductRepository.findById(message.getFlashSaleProductId()).ifPresent(fsp -> {
            fsp.setAvailableStock(fsp.getAvailableStock() + message.getQuantity());
            flashSaleProductRepository.save(fsp);
            log.info("Restored DB available stock for flashSaleProductId {}: +{}", fsp.getId(), message.getQuantity());
        });

        // 3. Hoàn trả tồn kho và hạn mức mua trên Redis qua Lua script
        String stockKey = "flashsale:stock:" + message.getEventId() + ":" + message.getProductId();
        String buyersKey = "flashsale:buyers:" + message.getEventId() + ":" + message.getProductId();
        List<String> keys = List.of(stockKey, buyersKey);

        Long updatedStock = stringRedisTemplate.execute(
                flashSaleStockRollbackScript,
                keys,
                message.getUserId().toString(),
                message.getQuantity().toString()
        );

        // 4. Cập nhật cache trạng thái đơn hàng trên Redis
        String trackingKey = "flashsale:order:tracking:" + message.getOrderTrackingId();
        stringRedisTemplate.opsForValue().set(trackingKey, "CANCELLED_TIMEOUT", 24, java.util.concurrent.TimeUnit.HOURS);

        log.info("Successfully cancelled expired flash sale order: orderId={}, trackingId={}, remainingStockInRedis={}",
                order.getId(), message.getOrderTrackingId(), updatedStock);

        return true;
    }
}
