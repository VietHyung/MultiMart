package com.multimart.modules.cart.service;

import com.multimart.common.exception.AppException;
import com.multimart.common.exception.ErrorCode;
import com.multimart.modules.cart.dto.AddCartItemRequest;
import com.multimart.modules.cart.dto.CartResponse;
import com.multimart.modules.cart.dto.UpdateCartItemRequest;
import com.multimart.modules.product.entity.Product;
import com.multimart.modules.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm thử giỏ hàng Redis (SRS-CART-001: T01-T07, T27).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CartServiceTest {

    private static final String CART_KEY = "cart:1";

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private HashOperations<String, String, String> hashOps;
    @Mock
    private ProductRepository productRepository;
    @InjectMocks
    private CartService cartService;

    @BeforeEach
    void setUp() {
        when(redis.<String, String>opsForHash()).thenReturn(hashOps);
    }

    private Product product(Long id, String price, int stock, String status) {
        Product p = Product.builder().name("SP " + id).originalPrice(new BigDecimal(price))
                .totalStock(stock).status(status).build();
        p.setId(id);
        return p;
    }

    private AddCartItemRequest add(Long productId, int quantity) {
        return AddCartItemRequest.builder().productId(productId).quantity(quantity).build();
    }

    @Test
    @DisplayName("T01: Thêm sản phẩm còn hàng vào giỏ -> ghi Redis và gia hạn TTL 7 ngày")
    void addItem_NewProduct_WritesToRedis() {
        Product p = product(5L, "100000", 10, "ACTIVE");
        when(productRepository.findById(5L)).thenReturn(Optional.of(p));
        when(hashOps.get(CART_KEY, "5")).thenReturn(null);
        when(hashOps.size(CART_KEY)).thenReturn(0L);
        when(hashOps.entries(CART_KEY)).thenReturn(Map.of("5", "2"));
        when(productRepository.findByIdInOrderByIdAsc(anyCollection())).thenReturn(List.of(p));

        CartResponse response = cartService.addItem(1L, add(5L, 2));

        verify(hashOps).put(CART_KEY, "5", "2");
        verify(redis).expire(CART_KEY, Duration.ofDays(7));
        assertEquals(1, response.getTotalItems());
        assertEquals(0, new BigDecimal("200000").compareTo(response.getTotalAmount()));
    }

    @Test
    @DisplayName("T02: Thêm cùng sản phẩm lần 2 -> cộng dồn số lượng")
    void addItem_ExistingProduct_Accumulates() {
        Product p = product(5L, "100000", 10, "ACTIVE");
        when(productRepository.findById(5L)).thenReturn(Optional.of(p));
        when(hashOps.get(CART_KEY, "5")).thenReturn("2");
        when(hashOps.entries(CART_KEY)).thenReturn(Map.of("5", "5"));
        when(productRepository.findByIdInOrderByIdAsc(anyCollection())).thenReturn(List.of(p));

        cartService.addItem(1L, add(5L, 3));

        verify(hashOps).put(CART_KEY, "5", "5");
    }

    @Test
    @DisplayName("T03: Thêm vượt tồn kho -> INSUFFICIENT_STOCK và không ghi Redis")
    void addItem_ExceedsStock_Throws() {
        when(productRepository.findById(5L)).thenReturn(Optional.of(product(5L, "100000", 3, "ACTIVE")));
        when(hashOps.get(CART_KEY, "5")).thenReturn("2");

        AppException ex = assertThrows(AppException.class, () -> cartService.addItem(1L, add(5L, 2)));

        assertEquals(ErrorCode.INSUFFICIENT_STOCK, ex.getErrorCode());
        verify(hashOps, never()).put(any(), any(), any());
    }

    @Test
    @DisplayName("T03: Cộng dồn vượt 99 -> INVALID_REQUEST")
    void addItem_ExceedsMaxQuantity_Throws() {
        when(productRepository.findById(5L)).thenReturn(Optional.of(product(5L, "100000", 500, "ACTIVE")));
        when(hashOps.get(CART_KEY, "5")).thenReturn("98");

        AppException ex = assertThrows(AppException.class, () -> cartService.addItem(1L, add(5L, 2)));

        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("T04: Sản phẩm INACTIVE -> PRODUCT_NOT_AVAILABLE; không tồn tại -> PRODUCT_NOT_FOUND")
    void addItem_InvalidProduct_Throws() {
        when(productRepository.findById(5L)).thenReturn(Optional.of(product(5L, "100000", 10, "INACTIVE")));
        when(productRepository.findById(404L)).thenReturn(Optional.empty());

        assertEquals(ErrorCode.PRODUCT_NOT_AVAILABLE,
                assertThrows(AppException.class, () -> cartService.addItem(1L, add(5L, 1))).getErrorCode());
        assertEquals(ErrorCode.PRODUCT_NOT_FOUND,
                assertThrows(AppException.class, () -> cartService.addItem(1L, add(404L, 1))).getErrorCode());
    }

    @Test
    @DisplayName("T05: Dòng thứ 51 -> CART_ITEM_LIMIT_EXCEEDED; dòng đã có thì vẫn cộng dồn được")
    void addItem_CartFull_Throws() {
        when(productRepository.findById(5L)).thenReturn(Optional.of(product(5L, "100000", 10, "ACTIVE")));
        when(hashOps.get(CART_KEY, "5")).thenReturn(null);
        when(hashOps.size(CART_KEY)).thenReturn(50L);

        AppException ex = assertThrows(AppException.class, () -> cartService.addItem(1L, add(5L, 1)));

        assertEquals(ErrorCode.CART_ITEM_LIMIT_EXCEEDED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Cập nhật dòng không có trong giỏ -> RESOURCE_NOT_FOUND")
    void updateItem_NotInCart_Throws() {
        when(productRepository.findById(5L)).thenReturn(Optional.of(product(5L, "100000", 10, "ACTIVE")));
        when(hashOps.hasKey(CART_KEY, "5")).thenReturn(false);

        AppException ex = assertThrows(AppException.class,
                () -> cartService.updateItem(1L, 5L, UpdateCartItemRequest.builder().quantity(2).build()));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("Cập nhật số lượng hợp lệ -> đặt lại giá trị trong Redis")
    void updateItem_Success() {
        Product p = product(5L, "100000", 10, "ACTIVE");
        when(productRepository.findById(5L)).thenReturn(Optional.of(p));
        when(hashOps.hasKey(CART_KEY, "5")).thenReturn(true);
        when(hashOps.entries(CART_KEY)).thenReturn(Map.of("5", "4"));
        when(productRepository.findByIdInOrderByIdAsc(anyCollection())).thenReturn(List.of(p));

        cartService.updateItem(1L, 5L, UpdateCartItemRequest.builder().quantity(4).build());

        verify(hashOps).put(CART_KEY, "5", "4");
    }

    @Test
    @DisplayName("T06/T07: Xem giỏ lấy giá hiện tại; dòng hết hàng available=false và không tính vào tổng")
    void getCart_MarksUnavailableAndSkipsInTotal() {
        Product ok = product(5L, "150000", 10, "ACTIVE");
        Product soldOut = product(7L, "90000", 1, "ACTIVE");
        when(hashOps.entries(CART_KEY)).thenReturn(Map.of("5", "2", "7", "3"));
        when(productRepository.findByIdInOrderByIdAsc(anyCollection())).thenReturn(List.of(ok, soldOut));

        CartResponse response = cartService.getCart(1L);

        assertEquals(2, response.getTotalItems());
        assertTrue(response.getItems().get(0).isAvailable());
        assertFalse(response.getItems().get(1).isAvailable());
        assertEquals(0, new BigDecimal("300000").compareTo(response.getTotalAmount()));
    }

    @Test
    @DisplayName("Giỏ trống trả về danh sách rỗng, tổng 0, không gọi DB")
    void getCart_Empty() {
        when(hashOps.entries(CART_KEY)).thenReturn(Map.of());

        CartResponse response = cartService.getCart(1L);

        assertEquals(0, response.getTotalItems());
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getTotalAmount()));
        verify(productRepository, never()).findByIdInOrderByIdAsc(anyCollection());
    }

    @Test
    @DisplayName("T27: Redis sập -> CART_UNAVAILABLE (503)")
    void redisDown_ThrowsCartUnavailable() {
        when(hashOps.entries(CART_KEY)).thenThrow(new RedisConnectionFailureException("down"));

        AppException ex = assertThrows(AppException.class, () -> cartService.getCart(1L));

        assertEquals(ErrorCode.CART_UNAVAILABLE, ex.getErrorCode());
        assertEquals(503, ex.getErrorCode().getHttpStatus().value());
    }

    @Test
    @DisplayName("Xóa dòng đã đặt khỏi giỏ: lỗi Redis không được ném ra (đơn đã tạo)")
    void removeItems_RedisError_IsSwallowed() {
        when(hashOps.delete(any(), any(Object[].class))).thenThrow(new RedisConnectionFailureException("down"));

        cartService.removeItems(1L, List.of(5L));
    }
}
