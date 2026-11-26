package com.multimart.modules.cart.service;

import com.multimart.common.exception.AppException;
import com.multimart.common.exception.ErrorCode;
import com.multimart.modules.cart.dto.AddCartItemRequest;
import com.multimart.modules.cart.dto.CartItemResponse;
import com.multimart.modules.cart.dto.CartResponse;
import com.multimart.modules.cart.dto.UpdateCartItemRequest;
import com.multimart.modules.product.entity.Product;
import com.multimart.modules.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Dịch vụ giỏ hàng lưu trên Redis Hash {@code cart:{userId}} (field = productId, value = số lượng).
 * Giỏ KHÔNG lưu giá: giá, tên và tồn kho luôn lấy từ DB tại thời điểm xem hoặc đặt hàng.
 * Khi Redis không khả dụng, mọi thao tác trả lỗi 503 {@code CART_UNAVAILABLE}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    /** Số dòng khác nhau tối đa trong một giỏ. */
    public static final int MAX_DISTINCT_ITEMS = 50;

    /** Số lượng tối đa của một dòng. */
    public static final int MAX_QUANTITY = 99;

    private static final Duration CART_TTL = Duration.ofDays(7);

    private final StringRedisTemplate stringRedisTemplate;
    private final ProductRepository productRepository;

    /**
     * Xem giỏ hàng với giá và tồn kho hiện tại.
     *
     * @param userId id người dùng
     * @return giỏ hàng kèm tổng tiền
     * @throws AppException CART_UNAVAILABLE khi Redis không khả dụng
     */
    public CartResponse getCart(Long userId) {
        return buildCartResponse(getCartQuantities(userId));
    }

    /**
     * Thêm sản phẩm vào giỏ, cộng dồn nếu đã có.
     *
     * @param userId  id người dùng
     * @param request sản phẩm và số lượng cần thêm
     * @return giỏ hàng sau khi thêm
     * @throws AppException PRODUCT_NOT_FOUND, PRODUCT_NOT_AVAILABLE, INSUFFICIENT_STOCK,
     *                      CART_ITEM_LIMIT_EXCEEDED, INVALID_REQUEST (vượt 99), CART_UNAVAILABLE
     */
    public CartResponse addItem(Long userId, AddCartItemRequest request) {
        Product product = loadSellableProduct(request.getProductId());
        String key = cartKey(userId);
        String field = String.valueOf(request.getProductId());

        redisCall(() -> {
            HashOperations<String, String, String> ops = stringRedisTemplate.opsForHash();
            String current = ops.get(key, field);
            if (current == null) {
                Long size = ops.size(key);
                if (size != null && size >= MAX_DISTINCT_ITEMS) {
                    throw new AppException(ErrorCode.CART_ITEM_LIMIT_EXCEEDED);
                }
            }
            int newQuantity = (current == null ? 0 : Integer.parseInt(current)) + request.getQuantity();
            validateQuantity(newQuantity, product);
            ops.put(key, field, String.valueOf(newQuantity));
            stringRedisTemplate.expire(key, CART_TTL);
            return null;
        });
        return getCart(userId);
    }

    /**
     * Đặt lại số lượng của một dòng trong giỏ.
     *
     * @param userId    id người dùng
     * @param productId id sản phẩm
     * @param request   số lượng mới
     * @return giỏ hàng sau khi cập nhật
     * @throws AppException RESOURCE_NOT_FOUND (dòng không có trong giỏ), INSUFFICIENT_STOCK, CART_UNAVAILABLE
     */
    public CartResponse updateItem(Long userId, Long productId, UpdateCartItemRequest request) {
        Product product = loadSellableProduct(productId);
        String key = cartKey(userId);
        String field = String.valueOf(productId);

        redisCall(() -> {
            HashOperations<String, String, String> ops = stringRedisTemplate.opsForHash();
            if (!ops.hasKey(key, field)) {
                throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Sản phẩm không có trong giỏ hàng");
            }
            validateQuantity(request.getQuantity(), product);
            ops.put(key, field, String.valueOf(request.getQuantity()));
            stringRedisTemplate.expire(key, CART_TTL);
            return null;
        });
        return getCart(userId);
    }

    /**
     * Xóa một dòng khỏi giỏ.
     *
     * @param userId    id người dùng
     * @param productId id sản phẩm cần xóa
     * @return giỏ hàng sau khi xóa
     * @throws AppException CART_UNAVAILABLE khi Redis không khả dụng
     */
    public CartResponse removeItem(Long userId, Long productId) {
        redisCall(() -> stringRedisTemplate.opsForHash().delete(cartKey(userId), String.valueOf(productId)));
        return getCart(userId);
    }

    /**
     * Xóa toàn bộ giỏ hàng.
     *
     * @param userId id người dùng
     * @throws AppException CART_UNAVAILABLE khi Redis không khả dụng
     */
    public void clearCart(Long userId) {
        redisCall(() -> stringRedisTemplate.delete(cartKey(userId)));
    }

    /**
     * Đọc thô nội dung giỏ: id sản phẩm -> số lượng, sắp theo id (dùng cho đặt hàng).
     *
     * @param userId id người dùng
     * @return map đã sắp xếp theo id sản phẩm
     * @throws AppException CART_UNAVAILABLE khi Redis không khả dụng
     */
    public Map<Long, Integer> getCartQuantities(Long userId) {
        Map<String, String> raw = redisCall(() -> {
            HashOperations<String, String, String> ops = stringRedisTemplate.opsForHash();
            return ops.entries(cartKey(userId));
        });
        Map<Long, Integer> result = new TreeMap<>();
        if (raw != null) {
            raw.forEach((field, value) -> result.put(Long.parseLong(field), Integer.parseInt(value)));
        }
        return result;
    }

    /**
     * Xóa các dòng đã đặt khỏi giỏ sau khi đặt hàng thành công. Lỗi Redis chỉ ghi log vì đơn đã được tạo.
     *
     * @param userId     id người dùng
     * @param productIds các sản phẩm đã đặt
     */
    public void removeItems(Long userId, Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return;
        }
        try {
            Object[] fields = productIds.stream().map(String::valueOf).toArray();
            stringRedisTemplate.opsForHash().delete(cartKey(userId), fields);
        } catch (RuntimeException e) {
            log.warn("Could not remove ordered items from cart of user {}: {}", userId, e.getMessage());
        }
    }

    /** Dựng phản hồi giỏ hàng từ nội dung thô, đọc giá và tồn kho từ DB. */
    private CartResponse buildCartResponse(Map<Long, Integer> quantities) {
        if (quantities.isEmpty()) {
            return CartResponse.builder().items(new ArrayList<>()).totalItems(0).totalAmount(BigDecimal.ZERO).build();
        }
        Map<Long, Product> products = new HashMap<>();
        productRepository.findByIdInOrderByIdAsc(quantities.keySet()).forEach(p -> products.put(p.getId(), p));

        List<CartItemResponse> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Product p = products.get(entry.getKey());
            if (p == null) {
                continue;
            }
            int quantity = entry.getValue();
            BigDecimal lineTotal = p.getOriginalPrice().multiply(BigDecimal.valueOf(quantity));
            boolean available = "ACTIVE".equals(p.getStatus()) && p.getTotalStock() >= quantity;
            if (available) {
                total = total.add(lineTotal);
            }
            items.add(CartItemResponse.builder()
                    .productId(p.getId())
                    .productName(p.getName())
                    .imageUrl(p.getImageUrl())
                    .unitPrice(p.getOriginalPrice())
                    .quantity(quantity)
                    .lineTotal(lineTotal)
                    .availableStock(p.getTotalStock())
                    .available(available)
                    .build());
        }
        return CartResponse.builder().items(items).totalItems(items.size()).totalAmount(total).build();
    }

    /** Tải sản phẩm và kiểm tra còn được bán. */
    private Product loadSellableProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND, "Không tìm thấy sản phẩm với id: " + productId));
        if (!"ACTIVE".equals(product.getStatus())) {
            throw new AppException(ErrorCode.PRODUCT_NOT_AVAILABLE);
        }
        return product;
    }

    /** Kiểm tra số lượng không vượt 99 và không vượt tồn kho hiện tại (kiểm tra mềm, checkout kiểm tra cứng). */
    private void validateQuantity(int quantity, Product product) {
        if (quantity > MAX_QUANTITY) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Số lượng mỗi sản phẩm trong giỏ tối đa là " + MAX_QUANTITY);
        }
        if (quantity > product.getTotalStock()) {
            throw new AppException(ErrorCode.INSUFFICIENT_STOCK,
                    "Chỉ còn " + product.getTotalStock() + " sản phẩm trong kho");
        }
    }

    /** Chạy thao tác Redis; lỗi hạ tầng được đổi thành 503 CART_UNAVAILABLE. */
    private <T> T redisCall(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataAccessException e) {
            log.error("Redis unavailable for cart operation: {}", e.getMessage());
            throw new AppException(ErrorCode.CART_UNAVAILABLE);
        }
    }

    private String cartKey(Long userId) {
        return "cart:" + userId;
    }
}
