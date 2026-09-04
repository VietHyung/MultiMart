package com.multimart.modules.product.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multimart.modules.product.dto.CreateProductRequest;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.Set;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ProductControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        // Tạo roles nếu chưa có
        Role roleAdmin = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_ADMIN").build()));
        Role roleUser = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_USER").build()));

        // Tạo user Admin và cấp token
        if (!userRepository.existsByEmail("admin@multimart.com")) {
            userRepository.save(User.builder()
                    .email("admin@multimart.com")
                    .password(passwordEncoder.encode("admin123"))
                    .fullName("Admin MultiMart")
                    .points(0)
                    .roles(Set.of(roleAdmin))
                    .build());
        }
        org.springframework.security.core.userdetails.User adminDetails = new org.springframework.security.core.userdetails.User(
                "admin@multimart.com", "admin123", Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        this.adminToken = jwtTokenProvider.generateAccessToken(adminDetails);

        // Tạo user thông thường và cấp token
        if (!userRepository.existsByEmail("normal_user@multimart.com")) {
            userRepository.save(User.builder()
                    .email("normal_user@multimart.com")
                    .password(passwordEncoder.encode("user123"))
                    .fullName("Normal User")
                    .points(0)
                    .roles(Set.of(roleUser))
                    .build());
        }
        org.springframework.security.core.userdetails.User userDetails = new org.springframework.security.core.userdetails.User(
                "normal_user@multimart.com", "user123", Set.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        this.userToken = jwtTokenProvider.generateAccessToken(userDetails);
    }

    @Test
    @DisplayName("GET /api/v1/products là Public, không cần đăng nhập vẫn xem được")
    void testGetProducts_PublicAccess_Success() throws Exception {
        productRepository.save(Product.builder()
                .name("iPhone 15 Pro Max")
                .description("Titan tự nhiên")
                .originalPrice(new BigDecimal("30000000"))
                .totalStock(50)
                .status("ACTIVE")
                .build());

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(1000)))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("POST /api/v1/products với User thường -> Bị chặn 403 Forbidden")
    void testCreateProduct_WithNormalUser_Returns403() throws Exception {
        CreateProductRequest req = CreateProductRequest.builder()
                .name("MacBook Pro M3")
                .originalPrice(new BigDecimal("45000000"))
                .totalStock(20)
                .build();

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/products với Admin -> Tạo thành công 201 Created")
    void testCreateProduct_WithAdmin_Returns201() throws Exception {
        CreateProductRequest req = CreateProductRequest.builder()
                .name("iPad Pro M4 2026")
                .description("Chip M4 cực mạnh")
                .originalPrice(new BigDecimal("25000000"))
                .totalStock(100)
                .build();

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", is(1000)))
                .andExpect(jsonPath("$.data.name", is("iPad Pro M4 2026")))
                .andExpect(jsonPath("$.data.totalStock", is(100)));
    }
}
