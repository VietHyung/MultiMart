package com.multimart.modules.cart.controller;

import com.multimart.common.response.ApiResponse;
import com.multimart.modules.cart.dto.AddCartItemRequest;
import com.multimart.modules.cart.dto.CartResponse;
import com.multimart.modules.cart.dto.UpdateCartItemRequest;
import com.multimart.modules.cart.service.CartService;
import com.multimart.modules.user.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API giỏ hàng của người dùng đang đăng nhập.
 */
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final CurrentUserService currentUserService;

    /**
     * Xem giỏ hàng.
     *
     * @param userDetails người dùng đăng nhập
     * @return HTTP 200 OK cùng giỏ hàng
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CartResponse>> getCart(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserService.getUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success("Lấy giỏ hàng thành công", cartService.getCart(userId)));
    }

    /**
     * Thêm sản phẩm vào giỏ.
     *
     * @param request     sản phẩm và số lượng
     * @param userDetails người dùng đăng nhập
     * @return HTTP 200 OK cùng giỏ hàng sau khi thêm
     */
    @PostMapping("/items")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CartResponse>> addItem(
            @Valid @RequestBody AddCartItemRequest request, @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserService.getUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success("Thêm vào giỏ hàng thành công", cartService.addItem(userId, request)));
    }

    /**
     * Đặt lại số lượng một dòng trong giỏ.
     *
     * @param productId   id sản phẩm
     * @param request     số lượng mới
     * @param userDetails người dùng đăng nhập
     * @return HTTP 200 OK cùng giỏ hàng sau khi cập nhật
     */
    @PutMapping("/items/{productId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CartResponse>> updateItem(
            @PathVariable Long productId, @Valid @RequestBody UpdateCartItemRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserService.getUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật giỏ hàng thành công",
                cartService.updateItem(userId, productId, request)));
    }

    /**
     * Xóa một dòng khỏi giỏ.
     *
     * @param productId   id sản phẩm
     * @param userDetails người dùng đăng nhập
     * @return HTTP 200 OK cùng giỏ hàng sau khi xóa
     */
    @DeleteMapping("/items/{productId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(
            @PathVariable Long productId, @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserService.getUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success("Xóa sản phẩm khỏi giỏ hàng thành công",
                cartService.removeItem(userId, productId)));
    }

    /**
     * Xóa toàn bộ giỏ hàng.
     *
     * @param userDetails người dùng đăng nhập
     * @return HTTP 200 OK
     */
    @DeleteMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> clearCart(@AuthenticationPrincipal UserDetails userDetails) {
        cartService.clearCart(currentUserService.getUserId(userDetails));
        return ResponseEntity.ok(ApiResponse.success("Đã xóa toàn bộ giỏ hàng", null));
    }
}
