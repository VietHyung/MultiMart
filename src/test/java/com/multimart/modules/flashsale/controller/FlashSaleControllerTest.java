package com.multimart.modules.flashsale.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.multimart.modules.flashsale.dto.AddProductToEventRequest;
import com.multimart.modules.flashsale.dto.CreateEventRequest;
import com.multimart.modules.flashsale.entity.FlashSaleEvent;
import com.multimart.modules.flashsale.repository.FlashSaleEventRepository;
import com.multimart.modules.product.entity.Product;
import com.multimart.modules.product.repository.ProductRepository;
import com.multimart.modules.user.entity.Role;
import com.multimart.modules.user.entity.User;
import com.multimart.modules.user.repository.RoleRepository;
import com.multimart.modules.user.repository.UserRepository;
import com.multimart.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class FlashSaleControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FlashSaleEventRepository eventRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.multimart.modules.flashsale.service.FlashSaleEngineService flashSaleEngineService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private String adminToken;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        Role roleAdmin = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_ADMIN").build()));

        if (!userRepository.existsByEmail("admin_fs@multimart.com")) {
            userRepository.save(User.builder()
                    .email("admin_fs@multimart.com")
                    .password(passwordEncoder.encode("admin123"))
                    .fullName("Admin FlashSale")
                    .points(0)
                    .roles(Set.of(roleAdmin))
                    .build());
        }

        org.springframework.security.core.userdetails.User adminDetails = new org.springframework.security.core.userdetails.User(
                "admin_fs@multimart.com", "admin123", Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        this.adminToken = jwtTokenProvider.generateAccessToken(adminDetails);
    }

    @Test
    @DisplayName("Admin tạo sự kiện Flash-Sale thành công -> 201 Created")
    void testCreateEvent_AsAdmin_Success() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(3);

        CreateEventRequest request = CreateEventRequest.builder()
                .name("Flash-Sale Siêu Phẩm Công Nghệ")
                .startTime(start)
                .endTime(end)
                .build();

        mockMvc.perform(post("/api/v1/admin/flash-sales")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", is(1000)))
                .andExpect(jsonPath("$.data.name", is("Flash-Sale Siêu Phẩm Công Nghệ")))
                .andExpect(jsonPath("$.data.status", is("UPCOMING")));
    }

    @Test
    @DisplayName("Đưa sản phẩm vào Flash-Sale -> Giam tồn kho gốc thành công")
    void testAddProductToEvent_InventoryPreallocation_Success() throws Exception {
        // 1. Tạo sự kiện
        FlashSaleEvent event = eventRepository.save(FlashSaleEvent.builder()
                .name("Đợt Sale Mùa Thu")
                .startTime(LocalDateTime.now().plusHours(2))
                .endTime(LocalDateTime.now().plusHours(5))
                .status("UPCOMING")
                .build());

        // 2. Tạo sản phẩm gốc có tồn kho 100
        Product product = productRepository.save(Product.builder()
                .name("Bàn phím cơ Custom")
                .originalPrice(new BigDecimal("2000000"))
                .totalStock(100)
                .status("ACTIVE")
                .build());

        // 3. Admin phân bổ 30 sản phẩm sang đợt Flash-Sale với giá 990.000đ
        AddProductToEventRequest req = AddProductToEventRequest.builder()
                .productId(product.getId())
                .flashPrice(new BigDecimal("990000"))
                .totalAllocated(30)
                .purchaseLimitPerUser(1)
                .build();

        mockMvc.perform(post("/api/v1/admin/flash-sales/" + event.getId() + "/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", is(1000)))
                .andExpect(jsonPath("$.data.availableStock", is(30)))
                .andExpect(jsonPath("$.data.discountPercentage", greaterThan(50)));

        // 4. Kiểm tra kho gốc đã bị giam chính xác 30 sản phẩm (100 - 30 = 70)
        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(70, updatedProduct.getTotalStock());
    }

    @Test
    @DisplayName("Phân bổ số lượng vượt quá kho gốc -> Ném lỗi OUT_OF_STOCK (400)")
    void testAddProductToEvent_ExceedsOriginalStock_Fails() throws Exception {
        FlashSaleEvent event = eventRepository.save(FlashSaleEvent.builder()
                .name("Sale Xả Kho")
                .startTime(LocalDateTime.now().plusHours(1))
                .endTime(LocalDateTime.now().plusHours(4))
                .status("UPCOMING")
                .build());

        // Kho gốc chỉ có 10 cái
        Product product = productRepository.save(Product.builder()
                .name("Chuột Gaming không dây")
                .originalPrice(new BigDecimal("1500000"))
                .totalStock(10)
                .status("ACTIVE")
                .build());

        // Yêu cầu phân bổ 50 cái (vượt quá 10 cái trong kho)
        AddProductToEventRequest req = AddProductToEventRequest.builder()
                .productId(product.getId())
                .flashPrice(new BigDecimal("500000"))
                .totalAllocated(50)
                .purchaseLimitPerUser(1)
                .build();

        mockMvc.perform(post("/api/v1/admin/flash-sales/" + event.getId() + "/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(3004))); // ErrorCode.OUT_OF_STOCK = 3004
    }

    @Test
    @DisplayName("Khách xem các đợt Flash-Sale đang diễn ra -> Có đồng hồ đếm ngược remainingSeconds")
    void testGetActiveEvents_Public_Success() throws Exception {
        // Tạo đợt sale đang diễn ra
        eventRepository.save(FlashSaleEvent.builder()
                .name("Đợt Sale Đang Chạy")
                .startTime(LocalDateTime.now().minusMinutes(30))
                .endTime(LocalDateTime.now().plusMinutes(90))
                .status("ACTIVE")
                .build());

        mockMvc.perform(get("/api/v1/flash-sales/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(1000)))
                .andExpect(jsonPath("$.data[0].remainingSeconds", greaterThan(0)));
    }

    @Test
    @DisplayName("Admin làm nóng dữ liệu Flash Sale lên Redis Cache thành công -> Trả về 200 OK")
    void testWarmUpEvent_Admin_Success() throws Exception {
        org.mockito.Mockito.when(flashSaleEngineService.warmUpEvent(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(com.multimart.modules.flashsale.dto.WarmUpEventResponse.builder()
                        .eventId(1L)
                        .eventName("Sale Test")
                        .warmedUpProductsCount(2)
                        .message("Warm-up thành công")
                        .build());

        mockMvc.perform(post("/api/v1/admin/flash-sales/1/warm-up")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(1000)))
                .andExpect(jsonPath("$.data.warmedUpProductsCount", is(2)));
    }
}
