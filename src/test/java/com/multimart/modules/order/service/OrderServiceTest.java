package com.multimart.modules.order.service;

import com.multimart.common.exception.AppException;
import com.multimart.common.exception.ErrorCode;
import com.multimart.common.exception.InsufficientStockException;
import com.multimart.modules.cart.service.CartService;
import com.multimart.modules.order.dto.CheckoutRequest;
import com.multimart.modules.order.dto.CheckoutResult;
import com.multimart.modules.order.dto.OrderPaymentTimeoutMessage;
import com.multimart.modules.order.dto.OrderResponse;
import com.multimart.modules.order.entity.CancelReason;
import com.multimart.modules.order.entity.CustomerOrder;
import com.multimart.modules.order.entity.OrderItem;
import com.multimart.modules.order.entity.OrderStatus;
import com.multimart.modules.order.entity.PaymentMethod;
import com.multimart.modules.order.mq.OrderPaymentTimeoutProducer;
import com.multimart.modules.order.repository.OrderRepository;
import com.multimart.modules.product.entity.Product;
import com.multimart.modules.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm thử nghiệp vụ đơn hàng thường bằng Mockito (SRS-CART-001: T09-T13, T16-T23).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private CartService cartService;
    @Mock
    private OrderPaymentTimeoutProducer timeoutProducer;
    @Mock
    private PlatformTransactionManager transactionManager;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, productRepository, cartService,
                new TransactionTemplate(transactionManager), timeoutProducer);
        ReflectionTestUtils.setField(orderService, "paymentTimeoutMs", 1_800_000L);

        when(orderRepository.saveAndFlush(any(CustomerOrder.class))).thenAnswer(inv -> {
            CustomerOrder o = inv.getArgument(0);
            o.setId(31L);
            return o;
        });
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.findDetailByUserIdAndIdempotencyKey(anyLong(), any())).thenReturn(Optional.empty());
    }

    // ----------------------------- helpers -----------------------------

    private Product product(Long id, String price, int stock) {
        Product p = Product.builder().name("SP " + id).originalPrice(new BigDecimal(price))
                .totalStock(stock).status("ACTIVE").build();
        p.setId(id);
        return p;
    }

    private CheckoutRequest request(PaymentMethod method) {
        return CheckoutRequest.builder().paymentMethod(method).receiverName("Nguyen Van A")
                .receiverPhone("0901234567").shippingAddress("12 Le Loi, Q1").build();
    }

    private void cartHas(Object... idQuantityPairs) {
        TreeMap<Long, Integer> cart = new TreeMap<>();
        for (int i = 0; i < idQuantityPairs.length; i += 2) {
            cart.put((Long) idQuantityPairs[i], (Integer) idQuantityPairs[i + 1]);
        }
        when(cartService.getCartQuantities(USER_ID)).thenReturn(cart);
    }

    private CustomerOrder order(OrderStatus status, PaymentMethod method) {
        CustomerOrder o = CustomerOrder.builder().orderCode("MM20261125ABC123").userId(USER_ID)
                .status(status).paymentMethod(method).totalAmount(new BigDecimal("300000"))
                .receiverName("A").receiverPhone("0901234567").shippingAddress("addr")
                .idempotencyKey("k").build();
        o.setId(10L);
        o.addItem(OrderItem.builder().productId(7L).productName("SP 7").unitPrice(new BigDecimal("100000"))
                .quantity(2).lineTotal(new BigDecimal("200000")).build());
        o.addItem(OrderItem.builder().productId(5L).productName("SP 5").unitPrice(new BigDecimal("100000"))
                .quantity(1).lineTotal(new BigDecimal("100000")).build());
        return o;
    }

    private void orderExists(CustomerOrder o) {
        when(orderRepository.findDetailByIdAndDeletedFalse(o.getId())).thenReturn(Optional.of(o));
    }

    private ErrorCode errorOf(Runnable action) {
        return assertThrows(AppException.class, action::run).getErrorCode();
    }

    // ----------------------------- checkout -----------------------------

    @Test
    @DisplayName("T09: Checkout COD -> CONFIRMED, trừ kho, giá chốt từ DB, dọn giỏ, không hẹn giờ hủy")
    void checkoutCod_Success() {
        cartHas(5L, 2, 7L, 1);
        when(productRepository.findByIdInOrderByIdAsc(anyCollection()))
                .thenReturn(List.of(product(5L, "100000", 10), product(7L, "50000", 10)));
        when(productRepository.deductStock(anyLong(), anyInt())).thenReturn(1);

        CheckoutResult result = orderService.checkout(USER_ID, request(PaymentMethod.COD), "key-1");

        assertTrue(result.created());
        OrderResponse order = result.order();
        assertEquals("CONFIRMED", order.getStatus());
        assertEquals(0, new BigDecimal("250000").compareTo(order.getTotalAmount()));
        assertEquals(2, order.getItems().size());
        assertTrue(order.getOrderCode().startsWith("MM"));
        assertEquals(16, order.getOrderCode().length());
        assertNull(order.getPaymentDeadline());
        verify(productRepository).deductStock(5L, 2);
        verify(productRepository).deductStock(7L, 1);
        verify(cartService).removeItems(eq(USER_ID), anyCollection());
        verify(timeoutProducer, never()).sendTimeoutMessage(any(), anyLong());
    }

    @Test
    @DisplayName("T10: Checkout ONLINE -> PENDING_PAYMENT và hẹn giờ hủy theo thời hạn thanh toán")
    void checkoutOnline_SchedulesTimeout() {
        cartHas(5L, 1);
        when(productRepository.findByIdInOrderByIdAsc(anyCollection())).thenReturn(List.of(product(5L, "100000", 10)));
        when(productRepository.deductStock(5L, 1)).thenReturn(1);

        CheckoutResult result = orderService.checkout(USER_ID, request(PaymentMethod.ONLINE), "key-2");

        assertEquals("PENDING_PAYMENT", result.order().getStatus());
        verify(timeoutProducer).sendTimeoutMessage(any(OrderPaymentTimeoutMessage.class), eq(1_800_000L));
    }

    @Test
    @DisplayName("T11: Giỏ rỗng hoặc không có sản phẩm được chọn -> CART_EMPTY")
    void checkout_EmptyCart_Throws() {
        cartHas();
        assertEquals(ErrorCode.CART_EMPTY,
                errorOf(() -> orderService.checkout(USER_ID, request(PaymentMethod.COD), "k")));

        cartHas(5L, 1);
        CheckoutRequest subset = request(PaymentMethod.COD);
        subset.setProductIds(List.of(99L));
        assertEquals(ErrorCode.CART_EMPTY, errorOf(() -> orderService.checkout(USER_ID, subset, "k")));
    }

    @Test
    @DisplayName("T09: Chỉ đặt tập sản phẩm được chọn trong giỏ")
    void checkout_OnlySelectedItems() {
        cartHas(5L, 1, 7L, 1);
        CheckoutRequest subset = request(PaymentMethod.COD);
        subset.setProductIds(List.of(7L));
        when(productRepository.findByIdInOrderByIdAsc(anyCollection())).thenReturn(List.of(product(7L, "50000", 10)));
        when(productRepository.deductStock(7L, 1)).thenReturn(1);

        CheckoutResult result = orderService.checkout(USER_ID, subset, "k");

        assertEquals(1, result.order().getItems().size());
        verify(productRepository, never()).deductStock(eq(5L), anyInt());
    }

    @Test
    @DisplayName("T12: Một sản phẩm thiếu hàng -> INSUFFICIENT_STOCK kèm id, không dọn giỏ, không hẹn giờ")
    void checkout_InsufficientStock_ThrowsWithProductIds() {
        cartHas(5L, 1, 7L, 5);
        when(productRepository.findByIdInOrderByIdAsc(anyCollection()))
                .thenReturn(List.of(product(5L, "100000", 10), product(7L, "50000", 2)));
        when(productRepository.deductStock(5L, 1)).thenReturn(1);
        when(productRepository.deductStock(7L, 5)).thenReturn(0);

        InsufficientStockException ex = assertThrows(InsufficientStockException.class,
                () -> orderService.checkout(USER_ID, request(PaymentMethod.ONLINE), "k"));

        assertEquals(ErrorCode.INSUFFICIENT_STOCK, ex.getErrorCode());
        assertEquals(List.of(7L), ex.getProductIds());
        verify(orderRepository, never()).saveAndFlush(any());
        verify(cartService, never()).removeItems(any(), anyCollection());
        verify(timeoutProducer, never()).sendTimeoutMessage(any(), anyLong());
        // Giao dịch phải được rollback để sản phẩm 5 không bị trừ kho
        verify(transactionManager).rollback(any());
    }

    @Test
    @DisplayName("Sản phẩm không còn ACTIVE hoặc không tồn tại bị tính là thiếu hàng")
    void checkout_InactiveProduct_CountsAsFailed() {
        cartHas(5L, 1, 8L, 1);
        Product inactive = product(5L, "100000", 10);
        inactive.setStatus("INACTIVE");
        when(productRepository.findByIdInOrderByIdAsc(anyCollection())).thenReturn(List.of(inactive));

        InsufficientStockException ex = assertThrows(InsufficientStockException.class,
                () -> orderService.checkout(USER_ID, request(PaymentMethod.COD), "k"));

        assertEquals(List.of(5L, 8L), ex.getProductIds());
        verify(productRepository, never()).deductStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("T13: Thiếu hoặc rỗng Idempotency-Key -> IDEMPOTENCY_KEY_REQUIRED")
    void checkout_MissingKey_Throws() {
        assertEquals(ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                errorOf(() -> orderService.checkout(USER_ID, request(PaymentMethod.COD), null)));
        assertEquals(ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                errorOf(() -> orderService.checkout(USER_ID, request(PaymentMethod.COD), "  ")));
        verify(cartService, never()).getCartQuantities(any());
    }

    @Test
    @DisplayName("T14: Trùng Idempotency-Key -> trả đơn cũ (created=false), không đụng giỏ và kho")
    void checkout_DuplicateKey_ReturnsExistingOrder() {
        CustomerOrder existing = order(OrderStatus.CONFIRMED, PaymentMethod.COD);
        when(orderRepository.findDetailByUserIdAndIdempotencyKey(USER_ID, "same-key")).thenReturn(Optional.of(existing));

        CheckoutResult result = orderService.checkout(USER_ID, request(PaymentMethod.COD), "same-key");

        assertFalse(result.created());
        assertEquals("MM20261125ABC123", result.order().getOrderCode());
        verify(cartService, never()).getCartQuantities(any());
        verify(productRepository, never()).deductStock(anyLong(), anyInt());
    }

    // ----------------------------- pay -----------------------------

    @Test
    @DisplayName("T16: Thanh toán đơn ONLINE đang chờ -> CONFIRMED và ghi paidAt")
    void pay_Success() {
        CustomerOrder o = order(OrderStatus.PENDING_PAYMENT, PaymentMethod.ONLINE);
        orderExists(o);

        OrderResponse response = orderService.payOrder(USER_ID, 10L);

        assertEquals("CONFIRMED", response.getStatus());
        assertNotNull(response.getPaidAt());
    }

    @Test
    @DisplayName("T17: Thanh toán lần 2 -> ORDER_ALREADY_PAID")
    void pay_Twice_Throws() {
        orderExists(order(OrderStatus.CONFIRMED, PaymentMethod.ONLINE));
        assertEquals(ErrorCode.ORDER_ALREADY_PAID, errorOf(() -> orderService.payOrder(USER_ID, 10L)));
    }

    @Test
    @DisplayName("T18: Thanh toán đơn đã hủy -> ORDER_ALREADY_CANCELLED")
    void pay_CancelledOrder_Throws() {
        orderExists(order(OrderStatus.CANCELLED, PaymentMethod.ONLINE));
        assertEquals(ErrorCode.ORDER_ALREADY_CANCELLED, errorOf(() -> orderService.payOrder(USER_ID, 10L)));
    }

    @Test
    @DisplayName("Đơn COD không có luồng thanh toán trực tuyến -> INVALID_ORDER_STATUS_TRANSITION")
    void pay_CodOrder_Throws() {
        orderExists(order(OrderStatus.CONFIRMED, PaymentMethod.COD));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, errorOf(() -> orderService.payOrder(USER_ID, 10L)));
    }

    @Test
    @DisplayName("T22: Người khác thao tác đơn không phải của mình -> ORDER_ACCESS_DENIED_STD; không tồn tại -> 4005")
    void access_ControlledByOwner() {
        orderExists(order(OrderStatus.PENDING_PAYMENT, PaymentMethod.ONLINE));
        when(orderRepository.findDetailByIdAndDeletedFalse(404L)).thenReturn(Optional.empty());

        assertEquals(ErrorCode.ORDER_ACCESS_DENIED_STD, errorOf(() -> orderService.payOrder(2L, 10L)));
        assertEquals(ErrorCode.ORDER_ACCESS_DENIED_STD, errorOf(() -> orderService.cancelOrder(2L, 10L, null)));
        assertEquals(ErrorCode.ORDER_ACCESS_DENIED_STD, errorOf(() -> orderService.getMyOrder(2L, 10L)));
        assertEquals(ErrorCode.STANDARD_ORDER_NOT_FOUND, errorOf(() -> orderService.getMyOrder(USER_ID, 404L)));
    }

    // ----------------------------- cancel / expire -----------------------------

    @Test
    @DisplayName("T19: Khách hủy đơn CONFIRMED -> CANCELLED, hoàn kho đúng từng dòng theo thứ tự id tăng dần")
    void cancel_RestoresStockInIdOrder() {
        CustomerOrder o = order(OrderStatus.CONFIRMED, PaymentMethod.COD);
        orderExists(o);

        OrderResponse response = orderService.cancelOrder(USER_ID, 10L, "đổi ý");

        assertEquals("CANCELLED", response.getStatus());
        assertEquals("USER", response.getCancelReason());
        assertNotNull(response.getCancelledAt());
        var inOrder = org.mockito.Mockito.inOrder(productRepository);
        inOrder.verify(productRepository).restoreStock(5L, 1);
        inOrder.verify(productRepository).restoreStock(7L, 2);
    }

    @Test
    @DisplayName("T19: Hủy lần 2 -> ORDER_ALREADY_CANCELLED và không hoàn kho thêm")
    void cancel_Twice_DoesNotRestoreAgain() {
        orderExists(order(OrderStatus.CANCELLED, PaymentMethod.COD));

        assertEquals(ErrorCode.ORDER_ALREADY_CANCELLED, errorOf(() -> orderService.cancelOrder(USER_ID, 10L, null)));
        verify(productRepository, never()).restoreStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("T20: Hủy đơn đang giao hoặc đã giao -> ORDER_CANNOT_CANCEL")
    void cancel_ShippedOrder_Throws() {
        orderExists(order(OrderStatus.SHIPPING, PaymentMethod.COD));
        assertEquals(ErrorCode.ORDER_CANNOT_CANCEL, errorOf(() -> orderService.cancelOrder(USER_ID, 10L, null)));

        CustomerOrder delivered = order(OrderStatus.DELIVERED, PaymentMethod.COD);
        delivered.setId(11L);
        when(orderRepository.findDetailByIdAndDeletedFalse(11L)).thenReturn(Optional.of(delivered));
        assertEquals(ErrorCode.ORDER_CANNOT_CANCEL, errorOf(() -> orderService.cancelOrder(USER_ID, 11L, null)));
        verify(productRepository, never()).restoreStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("T21: Hết hạn thanh toán -> hủy đơn PENDING_PAYMENT với lý do PAYMENT_TIMEOUT và hoàn kho")
    void expire_PendingOrder_IsCancelled() {
        CustomerOrder o = order(OrderStatus.PENDING_PAYMENT, PaymentMethod.ONLINE);
        orderExists(o);

        assertTrue(orderService.expireOrder(10L));

        assertEquals(OrderStatus.CANCELLED, o.getStatus());
        assertEquals(CancelReason.PAYMENT_TIMEOUT, o.getCancelReason());
        verify(productRepository, times(2)).restoreStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("T21: Đơn đã thanh toán hoặc đã hủy hoặc không tồn tại -> bỏ qua, không hoàn kho")
    void expire_NonPendingOrder_IsSkipped() {
        CustomerOrder paid = order(OrderStatus.CONFIRMED, PaymentMethod.ONLINE);
        orderExists(paid);
        when(orderRepository.findDetailByIdAndDeletedFalse(404L)).thenReturn(Optional.empty());

        assertFalse(orderService.expireOrder(10L));
        assertFalse(orderService.expireOrder(404L));

        assertEquals(OrderStatus.CONFIRMED, paid.getStatus());
        verify(productRepository, never()).restoreStock(anyLong(), anyInt());
    }

    // ----------------------------- admin -----------------------------

    @Test
    @DisplayName("T23: Admin chuyển CONFIRMED -> SHIPPING -> DELIVERED")
    void admin_ValidTransitions() {
        CustomerOrder o = order(OrderStatus.CONFIRMED, PaymentMethod.COD);
        orderExists(o);

        assertEquals("SHIPPING", orderService.updateStatusByAdmin(10L, OrderStatus.SHIPPING).getStatus());
        assertEquals("DELIVERED", orderService.updateStatusByAdmin(10L, OrderStatus.DELIVERED).getStatus());
    }

    @Test
    @DisplayName("T23: Nhảy cóc trạng thái hoặc chuyển về trạng thái không cho phép -> INVALID_ORDER_STATUS_TRANSITION")
    void admin_InvalidTransitions() {
        orderExists(order(OrderStatus.CONFIRMED, PaymentMethod.COD));

        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION,
                errorOf(() -> orderService.updateStatusByAdmin(10L, OrderStatus.DELIVERED)));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION,
                errorOf(() -> orderService.updateStatusByAdmin(10L, OrderStatus.PENDING_PAYMENT)));
        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION,
                errorOf(() -> orderService.updateStatusByAdmin(10L, OrderStatus.CONFIRMED)));
    }

    @Test
    @DisplayName("Admin hủy đơn CONFIRMED -> CANCELLED lý do ADMIN và hoàn kho; hủy đơn SHIPPING bị chặn")
    void admin_Cancel() {
        CustomerOrder o = order(OrderStatus.CONFIRMED, PaymentMethod.COD);
        orderExists(o);

        OrderResponse response = orderService.updateStatusByAdmin(10L, OrderStatus.CANCELLED);

        assertEquals("ADMIN", response.getCancelReason());
        verify(productRepository, times(2)).restoreStock(anyLong(), anyInt());

        CustomerOrder shipping = order(OrderStatus.SHIPPING, PaymentMethod.COD);
        shipping.setId(12L);
        when(orderRepository.findDetailByIdAndDeletedFalse(12L)).thenReturn(Optional.of(shipping));
        assertEquals(ErrorCode.ORDER_CANNOT_CANCEL,
                errorOf(() -> orderService.updateStatusByAdmin(12L, OrderStatus.CANCELLED)));
    }

    @Test
    @DisplayName("Tham số phân trang không hợp lệ -> INVALID_REQUEST")
    void listOrders_InvalidPaging_Throws() {
        assertEquals(ErrorCode.INVALID_REQUEST, errorOf(() -> orderService.getMyOrders(USER_ID, null, -1, 10)));
        assertEquals(ErrorCode.INVALID_REQUEST, errorOf(() -> orderService.getAllOrders(null, 0, 0)));
    }
}
