package com.multimart.modules.order.service;

import com.multimart.common.exception.AppException;
import com.multimart.common.exception.ErrorCode;
import com.multimart.common.exception.InsufficientStockException;
import com.multimart.common.response.PageResponse;
import com.multimart.modules.cart.service.CartService;
import com.multimart.modules.order.dto.CheckoutRequest;
import com.multimart.modules.order.dto.CheckoutResult;
import com.multimart.modules.order.dto.OrderItemResponse;
import com.multimart.modules.order.dto.OrderPaymentTimeoutMessage;
import com.multimart.modules.order.dto.OrderResponse;
import com.multimart.modules.order.dto.OrderSummaryResponse;
import com.multimart.modules.order.entity.CancelReason;
import com.multimart.modules.order.entity.CustomerOrder;
import com.multimart.modules.order.entity.OrderItem;
import com.multimart.modules.order.entity.OrderStatus;
import com.multimart.modules.order.entity.PaymentMethod;
import com.multimart.modules.order.mq.OrderPaymentTimeoutProducer;
import com.multimart.modules.order.repository.OrderRepository;
import com.multimart.modules.product.entity.Product;
import com.multimart.modules.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dịch vụ nghiệp vụ đơn hàng thường: đặt hàng từ giỏ, thanh toán giả lập, hủy, hủy quá hạn và quản trị trạng thái.
 * <p>
 * Nguyên tắc: giá và tồn kho luôn đọc/ghi trực tiếp DB; trừ kho bằng UPDATE có điều kiện nguyên tử,
 * duyệt sản phẩm theo id tăng dần để tránh deadlock (SA ADR-2, ADR-3).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CartService cartService;
    private final TransactionTemplate transactionTemplate;
    private final OrderPaymentTimeoutProducer timeoutProducer;

    @Value("${order.payment-timeout-ms:1800000}")
    private long paymentTimeoutMs;

    // =====================================================================
    // KHÁCH HÀNG
    // =====================================================================

    /**
     * Đặt hàng từ giỏ: trừ kho nguyên tử, tạo đơn với giá chốt, xóa các dòng đã đặt khỏi giỏ,
     * hẹn giờ hủy nếu thanh toán ONLINE. Cùng Idempotency-Key của cùng người dùng chỉ tạo một đơn.
     *
     * @param userId         id người đặt
     * @param request        thông tin nhận hàng và phương thức thanh toán
     * @param idempotencyKey khóa chống đặt trùng (header Idempotency-Key)
     * @return đơn hàng và cờ cho biết đơn mới hay đơn cũ trả lại
     * @throws AppException IDEMPOTENCY_KEY_REQUIRED, CART_EMPTY, CART_UNAVAILABLE, INSUFFICIENT_STOCK
     */
    public CheckoutResult checkout(Long userId, CheckoutRequest request, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey) || idempotencyKey.length() > 64) {
            throw new AppException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        String key = idempotencyKey.trim();

        var existing = orderRepository.findDetailByUserIdAndIdempotencyKey(userId, key);
        if (existing.isPresent()) {
            return new CheckoutResult(toResponse(existing.get()), false);
        }

        Map<Long, Integer> selected = selectCartItems(userId, request.getProductIds());

        CustomerOrder created;
        try {
            created = transactionTemplate.execute(status -> createOrder(userId, request, key, selected));
        } catch (DataIntegrityViolationException e) {
            // Hai request cùng Idempotency-Key chạy song song: ràng buộc UNIQUE giữ lại một đơn, trả đơn đó
            return orderRepository.findDetailByUserIdAndIdempotencyKey(userId, key)
                    .map(order -> new CheckoutResult(toResponse(order), false))
                    .orElseThrow(() -> e);
        }

        // Sau khi commit: dọn giỏ và hẹn giờ hủy (lỗi hạ tầng không làm hỏng đơn đã tạo)
        cartService.removeItems(userId, selected.keySet());
        if (created.getPaymentMethod() == PaymentMethod.ONLINE) {
            timeoutProducer.sendTimeoutMessage(
                    new OrderPaymentTimeoutMessage(created.getId(), created.getOrderCode()), paymentTimeoutMs);
        }
        log.info("Order created: code={}, userId={}, total={}, method={}",
                created.getOrderCode(), userId, created.getTotalAmount(), created.getPaymentMethod());
        return new CheckoutResult(toResponse(created), true);
    }

    /**
     * Thanh toán giả lập đơn ONLINE đang chờ thanh toán.
     *
     * @param userId  id người dùng
     * @param orderId id đơn hàng
     * @return đơn hàng sau khi thanh toán (CONFIRMED)
     * @throws AppException STANDARD_ORDER_NOT_FOUND, ORDER_ACCESS_DENIED_STD, ORDER_ALREADY_PAID,
     *                      ORDER_ALREADY_CANCELLED, INVALID_ORDER_STATUS_TRANSITION (đơn COD)
     */
    @Transactional
    public OrderResponse payOrder(Long userId, Long orderId) {
        CustomerOrder order = loadOwnedOrder(userId, orderId);
        if (order.getPaymentMethod() != PaymentMethod.ONLINE) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, "Đơn COD không cần thanh toán trực tuyến");
        }
        switch (order.getStatus()) {
            case PENDING_PAYMENT -> {
                order.setStatus(OrderStatus.CONFIRMED);
                order.setPaidAt(LocalDateTime.now());
            }
            case CANCELLED -> throw new AppException(ErrorCode.ORDER_ALREADY_CANCELLED);
            default -> throw new AppException(ErrorCode.ORDER_ALREADY_PAID);
        }
        log.info("Order paid: code={}", order.getOrderCode());
        return toResponse(orderRepository.save(order));
    }

    /**
     * Khách hủy đơn khi còn PENDING_PAYMENT hoặc CONFIRMED; hoàn kho đúng một lần.
     *
     * @param userId  id người dùng
     * @param orderId id đơn hàng
     * @param reason  lý do khách nhập (chỉ ghi log, có thể null)
     * @return đơn hàng sau khi hủy
     * @throws AppException STANDARD_ORDER_NOT_FOUND, ORDER_ACCESS_DENIED_STD, ORDER_ALREADY_CANCELLED, ORDER_CANNOT_CANCEL
     */
    @Transactional
    public OrderResponse cancelOrder(Long userId, Long orderId, String reason) {
        CustomerOrder order = loadOwnedOrder(userId, orderId);
        cancelInternal(order, CancelReason.USER);
        log.info("Order cancelled by user: code={}, reason={}", order.getOrderCode(), reason);
        return toResponse(orderRepository.save(order));
    }

    /**
     * Danh sách đơn của người dùng, mới nhất trước.
     *
     * @param userId id người dùng
     * @param status trạng thái cần lọc (null = tất cả)
     * @param page   trang (từ 0)
     * @param size   kích thước trang (tối đa 50)
     * @return trang tóm tắt đơn hàng
     */
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> getMyOrders(Long userId, OrderStatus status, int page, int size) {
        Pageable pageable = buildPageable(page, size);
        Page<CustomerOrder> result = status == null
                ? orderRepository.findByUserIdAndDeletedFalse(userId, pageable)
                : orderRepository.findByUserIdAndStatusAndDeletedFalse(userId, status, pageable);
        return toSummaryPage(result);
    }

    /**
     * Chi tiết một đơn của người dùng.
     *
     * @param userId  id người dùng
     * @param orderId id đơn hàng
     * @return chi tiết đơn hàng
     * @throws AppException STANDARD_ORDER_NOT_FOUND, ORDER_ACCESS_DENIED_STD
     */
    @Transactional(readOnly = true)
    public OrderResponse getMyOrder(Long userId, Long orderId) {
        return toResponse(loadOwnedOrder(userId, orderId));
    }

    // =====================================================================
    // ADMIN & HỆ THỐNG
    // =====================================================================

    /**
     * Admin xem danh sách mọi đơn, mới nhất trước.
     *
     * @param status trạng thái cần lọc (null = tất cả)
     * @param page   trang (từ 0)
     * @param size   kích thước trang (tối đa 50)
     * @return trang tóm tắt đơn hàng
     */
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> getAllOrders(OrderStatus status, int page, int size) {
        Pageable pageable = buildPageable(page, size);
        Page<CustomerOrder> result = status == null
                ? orderRepository.findByDeletedFalse(pageable)
                : orderRepository.findByStatusAndDeletedFalse(status, pageable);
        return toSummaryPage(result);
    }

    /**
     * Admin chuyển trạng thái đơn: CONFIRMED->SHIPPING, SHIPPING->DELIVERED hoặc hủy (kèm hoàn kho).
     *
     * @param orderId id đơn hàng
     * @param target  trạng thái đích
     * @return đơn hàng sau khi cập nhật
     * @throws AppException STANDARD_ORDER_NOT_FOUND, INVALID_ORDER_STATUS_TRANSITION, ORDER_CANNOT_CANCEL
     */
    @Transactional
    public OrderResponse updateStatusByAdmin(Long orderId, OrderStatus target) {
        CustomerOrder order = orderRepository.findDetailByIdAndDeletedFalse(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.STANDARD_ORDER_NOT_FOUND));
        switch (target) {
            case SHIPPING -> {
                requireStatus(order, OrderStatus.CONFIRMED);
                order.setStatus(OrderStatus.SHIPPING);
            }
            case DELIVERED -> {
                requireStatus(order, OrderStatus.SHIPPING);
                order.setStatus(OrderStatus.DELIVERED);
            }
            case CANCELLED -> cancelInternal(order, CancelReason.ADMIN);
            default -> throw new AppException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        }
        log.info("Order status updated by admin: code={}, status={}", order.getOrderCode(), order.getStatus());
        return toResponse(orderRepository.save(order));
    }

    /**
     * Hủy đơn ONLINE quá hạn thanh toán và hoàn kho. Idempotent: đơn không còn PENDING_PAYMENT thì bỏ qua.
     *
     * @param orderId id đơn hàng
     * @return true nếu đơn bị hủy trong lần gọi này, false nếu bỏ qua
     */
    @Transactional
    public boolean expireOrder(Long orderId) {
        CustomerOrder order = orderRepository.findDetailByIdAndDeletedFalse(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return false;
        }
        cancelInternal(order, CancelReason.PAYMENT_TIMEOUT);
        orderRepository.save(order);
        log.info("Order expired and stock restored: code={}", order.getOrderCode());
        return true;
    }

    // =====================================================================
    // HÀM NỘI BỘ
    // =====================================================================

    /** Lọc các dòng giỏ được chọn để đặt; ném CART_EMPTY nếu không còn dòng nào. */
    private Map<Long, Integer> selectCartItems(Long userId, List<Long> productIds) {
        Map<Long, Integer> cart = cartService.getCartQuantities(userId);
        Map<Long, Integer> selected = new LinkedHashMap<>();
        cart.forEach((id, quantity) -> {
            if (productIds == null || productIds.isEmpty() || productIds.contains(id)) {
                selected.put(id, quantity);
            }
        });
        if (selected.isEmpty()) {
            throw new AppException(ErrorCode.CART_EMPTY);
        }
        return selected;
    }

    /**
     * Tạo đơn trong một giao dịch: trừ kho theo thứ tự id tăng dần, gom mọi sản phẩm thiếu hàng
     * rồi ném lỗi để rollback toàn bộ (hoặc tất cả, hoặc không gì cả).
     */
    private CustomerOrder createOrder(Long userId, CheckoutRequest request, String key, Map<Long, Integer> selected) {
        Map<Long, Product> products = new HashMap<>();
        productRepository.findByIdInOrderByIdAsc(selected.keySet()).forEach(p -> products.put(p.getId(), p));

        CustomerOrder order = CustomerOrder.builder()
                .orderCode(generateOrderCode())
                .userId(userId)
                .paymentMethod(request.getPaymentMethod())
                .status(request.getPaymentMethod() == PaymentMethod.COD
                        ? OrderStatus.CONFIRMED : OrderStatus.PENDING_PAYMENT)
                .receiverName(request.getReceiverName().trim())
                .receiverPhone(request.getReceiverPhone().trim())
                .shippingAddress(request.getShippingAddress().trim())
                .note(StringUtils.hasText(request.getNote()) ? request.getNote().trim() : null)
                .idempotencyKey(key)
                .totalAmount(BigDecimal.ZERO)
                .build();

        List<Long> failed = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        // selected đã sắp theo id tăng dần (TreeMap ở CartService) -> thứ tự khóa dòng cố định, tránh deadlock
        for (Map.Entry<Long, Integer> entry : selected.entrySet()) {
            Long productId = entry.getKey();
            int quantity = entry.getValue();
            Product product = products.get(productId);
            if (product == null || !"ACTIVE".equals(product.getStatus())
                    || productRepository.deductStock(productId, quantity) == 0) {
                failed.add(productId);
                continue;
            }
            BigDecimal lineTotal = product.getOriginalPrice().multiply(BigDecimal.valueOf(quantity));
            total = total.add(lineTotal);
            order.addItem(OrderItem.builder()
                    .productId(productId)
                    .productName(product.getName())
                    .unitPrice(product.getOriginalPrice())
                    .quantity(quantity)
                    .lineTotal(lineTotal)
                    .build());
        }
        if (!failed.isEmpty()) {
            throw new InsufficientStockException(failed);
        }
        order.setTotalAmount(total);
        return orderRepository.saveAndFlush(order);
    }

    /** Hủy đơn: kiểm tra trạng thái, đổi sang CANCELLED và hoàn tồn kho từng dòng (thứ tự id tăng dần). */
    private void cancelInternal(CustomerOrder order, CancelReason reason) {
        switch (order.getStatus()) {
            case CANCELLED -> throw new AppException(ErrorCode.ORDER_ALREADY_CANCELLED);
            case SHIPPING, DELIVERED -> throw new AppException(ErrorCode.ORDER_CANNOT_CANCEL);
            default -> {
                // PENDING_PAYMENT hoặc CONFIRMED: được phép hủy
            }
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        order.setCancelReason(reason);
        order.getItems().stream()
                .sorted((a, b) -> a.getProductId().compareTo(b.getProductId()))
                .forEach(item -> productRepository.restoreStock(item.getProductId(), item.getQuantity()));
    }

    /** Yêu cầu đơn đang ở đúng trạng thái nguồn, ngược lại báo chuyển trạng thái không hợp lệ. */
    private void requireStatus(CustomerOrder order, OrderStatus expected) {
        if (order.getStatus() != expected) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS_TRANSITION,
                    "Đơn hàng đang ở trạng thái " + order.getStatus() + ", cần ở trạng thái " + expected);
        }
    }

    /** Tải đơn (kèm dòng sản phẩm) và bảo đảm thuộc về người dùng. */
    private CustomerOrder loadOwnedOrder(Long userId, Long orderId) {
        CustomerOrder order = orderRepository.findDetailByIdAndDeletedFalse(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.STANDARD_ORDER_NOT_FOUND));
        if (!order.getUserId().equals(userId)) {
            throw new AppException(ErrorCode.ORDER_ACCESS_DENIED_STD);
        }
        return order;
    }

    /** Dựng Pageable mới nhất trước, kiểm tra tham số và cắt size về tối đa 50. */
    private Pageable buildPageable(int page, int size) {
        if (page < 0 || size < 1) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Tham số phân trang không hợp lệ");
        }
        return PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    /** Chuyển trang đơn sang trang tóm tắt, đếm số dòng của cả trang bằng một truy vấn. */
    private PageResponse<OrderSummaryResponse> toSummaryPage(Page<CustomerOrder> page) {
        Map<Long, Long> itemCounts = new HashMap<>();
        if (!page.isEmpty()) {
            List<Long> ids = page.getContent().stream().map(CustomerOrder::getId).toList();
            for (Object[] row : orderRepository.countItemsByOrderIds(ids)) {
                itemCounts.put((Long) row[0], (Long) row[1]);
            }
        }
        return PageResponse.from(page.map(o -> OrderSummaryResponse.builder()
                .orderId(o.getId())
                .orderCode(o.getOrderCode())
                .status(o.getStatus().name())
                .paymentMethod(o.getPaymentMethod().name())
                .totalAmount(o.getTotalAmount())
                .itemCount(itemCounts.getOrDefault(o.getId(), 0L))
                .createdAt(o.getCreatedAt())
                .build()));
    }

    /** Chuyển Entity sang DTO chi tiết; hạn thanh toán chỉ có với đơn PENDING_PAYMENT. */
    private OrderResponse toResponse(CustomerOrder order) {
        LocalDateTime deadline = (order.getStatus() == OrderStatus.PENDING_PAYMENT && order.getCreatedAt() != null)
                ? order.getCreatedAt().plusNanos(paymentTimeoutMs * 1_000_000L) : null;
        return OrderResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .status(order.getStatus().name())
                .paymentMethod(order.getPaymentMethod().name())
                .totalAmount(order.getTotalAmount())
                .paymentDeadline(deadline)
                .items(order.getItems().stream().map(i -> OrderItemResponse.builder()
                        .productId(i.getProductId())
                        .productName(i.getProductName())
                        .unitPrice(i.getUnitPrice())
                        .quantity(i.getQuantity())
                        .lineTotal(i.getLineTotal())
                        .build()).toList())
                .receiverName(order.getReceiverName())
                .receiverPhone(order.getReceiverPhone())
                .shippingAddress(order.getShippingAddress())
                .note(order.getNote())
                .paidAt(order.getPaidAt())
                .cancelledAt(order.getCancelledAt())
                .cancelReason(order.getCancelReason() == null ? null : order.getCancelReason().name())
                .createdAt(order.getCreatedAt())
                .build();
    }

    /** Sinh mã đơn dạng MM + yyyyMMdd + 6 ký tự chữ/số ngẫu nhiên in hoa. */
    private String generateOrderCode() {
        StringBuilder sb = new StringBuilder("MM").append(LocalDate.now().format(CODE_DATE));
        for (int i = 0; i < 6; i++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
