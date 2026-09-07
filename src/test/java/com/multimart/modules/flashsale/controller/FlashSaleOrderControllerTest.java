package com.multimart.modules.flashsale.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multimart.common.response.PageResponse;
import com.multimart.modules.flashsale.dto.FlashSaleOrderRequest;
import com.multimart.modules.flashsale.dto.FlashSaleOrderResponse;
import com.multimart.modules.flashsale.entity.FlashSaleOrderStatus;
import com.multimart.modules.flashsale.service.FlashSaleEngineService;
import com.multimart.modules.user.entity.User;
import com.multimart.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử Controller cho FlashSaleOrderController.
 * Kiểm tra các tình huống phân quyền bảo mật, validation và phản hồi HTTP của API đặt mua Flash Sale.
 */
@SpringBootTest
class FlashSaleOrderControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @MockitoBean
    private FlashSaleEngineService flashSaleEngineService;

    @MockitoBean
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("Từ chối đặt mua Flash Sale với mã 401 khi chưa đăng nhập")
    void placeOrder_Unauthenticated_Returns401() throws Exception {
        FlashSaleOrderRequest request = FlashSaleOrderRequest.builder()
                .eventId(1L)
                .productId(10L)
                .quantity(1)
                .build();

        mockMvc.perform(post("/api/v1/flash-sales/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Đặt mua Flash Sale bất đồng bộ thành công khi đã đăng nhập (Trả về 202 Accepted)")
    @WithMockUser(username = "buyer@gmail.com", roles = {"USER"})
    void placeOrder_Authenticated_Returns202() throws Exception {
        FlashSaleOrderRequest request = FlashSaleOrderRequest.builder()
                .eventId(1L)
                .productId(10L)
                .quantity(1)
                .build();

        User mockUser = User.builder()
                .email("buyer@gmail.com")
                .fullName("Buyer Test")
                .password("encodedPassword")
                .build();
        mockUser.setId(5L);

        com.multimart.modules.flashsale.dto.AsyncOrderSubmitResponse mockResponse =
                com.multimart.modules.flashsale.dto.AsyncOrderSubmitResponse.builder()
                        .orderTrackingId("tracking-uuid-1234")
                        .status("PENDING_PROCESSING")
                        .eventId(1L)
                        .productId(10L)
                        .quantity(1)
                        .message("Yêu cầu đặt mua Flash Sale đã được tiếp nhận và đang xử lý")
                        .build();

        when(userRepository.findByEmail("buyer@gmail.com")).thenReturn(Optional.of(mockUser));
        when(flashSaleEngineService.submitOrderAsync(eq(5L), any(FlashSaleOrderRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/flash-sales/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.orderTrackingId").value("tracking-uuid-1234"))
                .andExpect(jsonPath("$.data.status").value("PENDING_PROCESSING"));
    }

    @Test
    @DisplayName("Tra cứu trạng thái đơn hàng bất đồng bộ thành công qua trackingId")
    @WithMockUser(username = "buyer@gmail.com", roles = {"USER"})
    void getTrackingStatus_Authenticated_Returns200() throws Exception {
        String trackingId = "tracking-uuid-1234";
        com.multimart.modules.flashsale.dto.OrderTrackingResponse mockResponse =
                com.multimart.modules.flashsale.dto.OrderTrackingResponse.builder()
                        .orderTrackingId(trackingId)
                        .trackingStatus("SUCCESS")
                        .orderId(101L)
                        .userId(5L)
                        .eventId(1L)
                        .productId(10L)
                        .quantity(1)
                        .unitPrice(BigDecimal.valueOf(499000))
                        .totalPrice(BigDecimal.valueOf(499000))
                        .orderStatus(FlashSaleOrderStatus.PENDING)
                        .createdAt(LocalDateTime.now())
                        .build();

        when(flashSaleEngineService.getTrackingStatus(trackingId)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/flash-sales/orders/tracking/" + trackingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.trackingStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.orderId").value(101));
    }

    @Test
    @DisplayName("Từ chối đặt mua với mã 400 khi dữ liệu request không hợp lệ (quantity = 0)")
    @WithMockUser(username = "buyer@gmail.com", roles = {"USER"})
    void placeOrder_InvalidQuantity_Returns400() throws Exception {
        FlashSaleOrderRequest request = FlashSaleOrderRequest.builder()
                .eventId(1L)
                .productId(10L)
                .quantity(0) // Vi phạm @Min(1)
                .build();

        mockMvc.perform(post("/api/v1/flash-sales/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    @DisplayName("Lấy danh sách lịch sử đơn hàng Flash Sale cá nhân thành công")
    @WithMockUser(username = "buyer@gmail.com", roles = {"USER"})
    void getMyOrders_Authenticated_Returns200() throws Exception {
        User mockUser = User.builder()
                .email("buyer@gmail.com")
                .fullName("Buyer Test")
                .password("encodedPassword")
                .build();
        mockUser.setId(5L);

        FlashSaleOrderResponse order1 = FlashSaleOrderResponse.builder()
                .orderId(101L)
                .userId(5L)
                .eventId(1L)
                .productId(10L)
                .quantity(1)
                .unitPrice(BigDecimal.valueOf(499000))
                .totalPrice(BigDecimal.valueOf(499000))
                .status(FlashSaleOrderStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        PageResponse<FlashSaleOrderResponse> pageResponse = PageResponse.<FlashSaleOrderResponse>builder()
                .content(List.of(order1))
                .pageNumber(0)
                .pageSize(10)
                .totalElements(1)
                .totalPages(1)
                .isLast(true)
                .build();

        when(userRepository.findByEmail("buyer@gmail.com")).thenReturn(Optional.of(mockUser));
        when(flashSaleEngineService.getMyOrders(5L, 0, 10)).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/flash-sales/orders/my-orders")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.content[0].orderId").value(101));
    }

    @Test
    @DisplayName("Thanh toán đơn hàng Flash Sale thành công: Trả về HTTP 200 OK cùng trạng thái CONFIRMED")
    @WithMockUser(username = "buyer@gmail.com", roles = "CUSTOMER")
    void payOrder_Success_Returns200() throws Exception {
        User mockUser = User.builder()
                .email("buyer@gmail.com")
                .fullName("Buyer")
                .build();
        mockUser.setId(5L);

        com.multimart.modules.flashsale.dto.FlashSaleOrderPaymentResponse paymentResponse =
                com.multimart.modules.flashsale.dto.FlashSaleOrderPaymentResponse.builder()
                        .orderId(101L)
                        .orderTrackingId("tracking-uuid-pay-1")
                        .status(FlashSaleOrderStatus.CONFIRMED)
                        .totalPrice(BigDecimal.valueOf(499000))
                        .paidAt(LocalDateTime.now())
                        .message("Thanh toán đơn hàng Flash Sale thành công")
                        .build();

        when(userRepository.findByEmail("buyer@gmail.com")).thenReturn(Optional.of(mockUser));
        when(flashSaleEngineService.confirmPayment(5L, 101L)).thenReturn(paymentResponse);

        mockMvc.perform(post("/api/v1/flash-sales/orders/101/pay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.orderId").value(101))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("Từ chối thanh toán đơn hàng với HTTP 401 khi chưa đăng nhập")
    void payOrder_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(post("/api/v1/flash-sales/orders/101/pay"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Thanh toán đơn hàng thất bại với HTTP 400 khi đơn hàng đã hết hạn hoặc bị hủy")
    @WithMockUser(username = "buyer@gmail.com", roles = "CUSTOMER")
    void payOrder_OrderAlreadyCancelled_Returns400() throws Exception {
        User mockUser = User.builder()
                .email("buyer@gmail.com")
                .fullName("Buyer")
                .build();
        mockUser.setId(5L);

        when(userRepository.findByEmail("buyer@gmail.com")).thenReturn(Optional.of(mockUser));
        when(flashSaleEngineService.confirmPayment(5L, 101L))
                .thenThrow(new com.multimart.common.exception.AppException(com.multimart.common.exception.ErrorCode.ORDER_ALREADY_CANCELLED));

        mockMvc.perform(post("/api/v1/flash-sales/orders/101/pay"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3010));
    }
}
