package com.multimart.modules.review.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.multimart.modules.order.entity.CustomerOrder;
import com.multimart.modules.order.entity.OrderItem;
import com.multimart.modules.order.entity.OrderStatus;
import com.multimart.modules.order.entity.PaymentMethod;
import com.multimart.modules.order.repository.OrderRepository;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Kiểm thử tích hợp đánh giá sản phẩm qua HTTP với Postgres và Redis thật (SRS-REVIEW-001).
 * Mỗi lần chạy tạo dữ liệu riêng (email, sản phẩm, đơn hàng) nên không phụ thuộc dữ liệu cũ.
 */
@SpringBootTest
class ReviewIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    private Product product;
    private Long ownerOrderId;
    private Long otherOrderId;
    private String ownerToken;
    private String otherToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        String run = UUID.randomUUID().toString().substring(0, 8);

        Role roleUser = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_USER").build()));
        Role roleAdmin = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_ADMIN").build()));

        User owner = saveUser("owner-" + run + "@t.com", "Trần Thị Bình", roleUser);
        User other = saveUser("other-" + run + "@t.com", "Lê Văn Cường", roleUser);
        User admin = saveUser("admin-" + run + "@t.com", "Admin Test", roleAdmin);
        ownerToken = token(owner.getEmail(), "ROLE_USER");
        otherToken = token(other.getEmail(), "ROLE_USER");
        adminToken = token(admin.getEmail(), "ROLE_ADMIN");

        product = productRepository.save(Product.builder().name("SP review " + run)
                .originalPrice(new BigDecimal("100000")).totalStock(10).status("ACTIVE").build());
        ownerOrderId = deliveredOrder(owner.getId(), product, OrderStatus.DELIVERED, run + "a");
        otherOrderId = deliveredOrder(other.getId(), product, OrderStatus.SHIPPING, run + "b");
    }

    private User saveUser(String email, String fullName, Role role) {
        return userRepository.save(User.builder().email(email).password(passwordEncoder.encode("pw123456"))
                .fullName(fullName).points(0).roles(Set.of(role)).build());
    }

    private String token(String email, String role) {
        return jwtTokenProvider.generateAccessToken(new org.springframework.security.core.userdetails.User(
                email, "pw", Set.of(new SimpleGrantedAuthority(role))));
    }

    private Long deliveredOrder(Long userId, Product p, OrderStatus status, String suffix) {
        CustomerOrder order = CustomerOrder.builder().orderCode(("RV" + suffix).substring(0, Math.min(16, ("RV" + suffix).length())))
                .userId(userId).status(status).paymentMethod(PaymentMethod.COD).totalAmount(new BigDecimal("100000"))
                .receiverName("A").receiverPhone("0901234567").shippingAddress("addr")
                .idempotencyKey("k-" + suffix).build();
        order.addItem(OrderItem.builder().productId(p.getId()).productName(p.getName())
                .unitPrice(new BigDecimal("100000")).quantity(1).lineTotal(new BigDecimal("100000")).build());
        return orderRepository.save(order).getId();
    }

    private String body(Object... kv) throws Exception {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return objectMapper.writeValueAsString(map);
    }

    private MvcResult send(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private MvcResult postReview(String token, Long orderId, int rating, Boolean anonymous) throws Exception {
        var req = post("/api/v1/products/" + product.getId() + "/reviews").contentType(MediaType.APPLICATION_JSON)
                .content(anonymous == null ? body("orderId", orderId, "rating", rating)
                        : body("orderId", orderId, "rating", rating, "comment", "Tốt", "anonymous", anonymous));
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return send(req);
    }

    private JsonNode productDetail() throws Exception {
        return json(send(get("/api/v1/products/" + product.getId()))).get("data");
    }

    @Test
    @DisplayName("Luồng đầy đủ: gửi, trùng, quyền, sửa, ẩn/hiện, xóa, gửi lại; tổng hợp và cache luôn đúng")
    void fullFlow() throws Exception {
        // Chưa đăng nhập
        assertEquals(401, postReview(null, ownerOrderId, 5, null).getResponse().getStatus());

        // R07: số sao sai
        assertEquals(400, postReview(ownerToken, ownerOrderId, 6, null).getResponse().getStatus());
        assertEquals(400, postReview(ownerToken, ownerOrderId, 0, null).getResponse().getStatus());

        // R03/R04: đơn của người khác, đơn chưa giao
        MvcResult notMine = postReview(otherToken, ownerOrderId, 5, null);
        assertEquals(403, notMine.getResponse().getStatus());
        assertEquals(6003, json(notMine).get("code").asInt());
        assertEquals(403, postReview(otherToken, otherOrderId, 5, null).getResponse().getStatus());

        // Nạp cache chi tiết trước khi có đánh giá (để kiểm chứng cache bị xóa)
        assertEquals(0, productDetail().get("ratingCount").asInt());

        // R01: gửi thành công
        MvcResult created = postReview(ownerToken, ownerOrderId, 5, true);
        assertEquals(201, created.getResponse().getStatus());
        long reviewId = json(created).get("data").get("id").asLong();
        assertEquals("Người dùng ẩn danh", json(created).get("data").get("reviewerName").asText());
        JsonNode detail = productDetail();
        assertEquals(1, detail.get("ratingCount").asInt());
        assertEquals(5.0, detail.get("ratingAverage").asDouble());

        // R05: gửi lần 2
        MvcResult dup = postReview(ownerToken, ownerOrderId, 4, null);
        assertEquals(409, dup.getResponse().getStatus());
        assertEquals(6002, json(dup).get("code").asInt());

        // Danh sách công khai: không lộ orderId, tên ẩn danh
        JsonNode first = json(send(get("/api/v1/products/" + product.getId() + "/reviews"))).get("data").get("content").get(0);
        assertEquals(reviewId, first.get("id").asLong());
        assertEquals(false, first.has("orderId"));
        assertEquals("Người dùng ẩn danh", first.get("reviewerName").asText());

        // R12: người khác sửa
        assertEquals(403, send(put("/api/v1/reviews/" + reviewId).header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON).content(body("rating", 1))).getResponse().getStatus());

        // R11 + R26b: sửa 5 -> 3 và hiện tên
        MvcResult edited = send(put("/api/v1/reviews/" + reviewId).header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content(body("rating", 3, "anonymous", false)));
        assertEquals(200, edited.getResponse().getStatus());
        assertEquals(true, json(edited).get("data").get("edited").asBoolean());
        assertEquals("Trần Thị Bình", json(edited).get("data").get("reviewerName").asText());
        detail = productDetail();
        assertEquals(1, detail.get("ratingCount").asInt());
        assertEquals(3.0, detail.get("ratingAverage").asDouble());

        // R24: summary
        JsonNode summary = json(send(get("/api/v1/products/" + product.getId() + "/reviews/summary"))).get("data");
        assertEquals(3.0, summary.get("ratingAverage").asDouble());
        assertEquals(1, summary.get("distribution").get("3").asInt());
        assertEquals(0, summary.get("distribution").get("5").asInt());

        // R28: user thường không vào được API kiểm duyệt
        assertEquals(403, send(get("/api/v1/admin/reviews").header("Authorization", "Bearer " + ownerToken)).getResponse().getStatus());

        // R18: Admin ẩn
        assertEquals(200, send(put("/api/v1/admin/reviews/" + reviewId + "/visibility")
                .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                .content(body("visible", false))).getResponse().getStatus());
        assertEquals(0, productDetail().get("ratingCount").asInt());
        assertEquals(0, json(send(get("/api/v1/products/" + product.getId() + "/reviews"))).get("data").get("content").size());
        JsonNode mine = json(send(get("/api/v1/reviews/me").header("Authorization", "Bearer " + ownerToken))).get("data");
        assertEquals("HIDDEN", mine.get("content").get(0).get("status").asText());
        assertEquals(ownerOrderId, mine.get("content").get(0).get("orderId").asLong());

        // R19: ẩn lần nữa không trừ hai lần
        send(put("/api/v1/admin/reviews/" + reviewId + "/visibility").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body("visible", false)));
        assertEquals(0, productRepository.findById(product.getId()).orElseThrow().getRatingCount());

        // R20: hiện lại
        send(put("/api/v1/admin/reviews/" + reviewId + "/visibility").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body("visible", true)));
        assertEquals(1, productDetail().get("ratingCount").asInt());

        // R14/R15: Admin và người khác không xóa được đánh giá của chủ
        assertEquals(403, send(delete("/api/v1/reviews/" + reviewId).header("Authorization", "Bearer " + adminToken)).getResponse().getStatus());
        assertEquals(403, send(delete("/api/v1/reviews/" + reviewId).header("Authorization", "Bearer " + otherToken)).getResponse().getStatus());

        // R13: chủ xóa
        assertEquals(200, send(delete("/api/v1/reviews/" + reviewId).header("Authorization", "Bearer " + ownerToken)).getResponse().getStatus());
        assertEquals(0, productDetail().get("ratingCount").asInt());
        assertEquals(404, send(delete("/api/v1/reviews/" + reviewId).header("Authorization", "Bearer " + ownerToken)).getResponse().getStatus());

        // R16: gửi lại sau khi xóa
        assertEquals(201, postReview(ownerToken, ownerOrderId, 4, null).getResponse().getStatus());
        assertEquals(1, productRepository.findById(product.getId()).orElseThrow().getRatingCount());
    }

    @Test
    @DisplayName("R23: tham số danh sách sai bị từ chối; size lớn được chấp nhận")
    void listValidation() throws Exception {
        String base = "/api/v1/products/" + product.getId() + "/reviews";
        assertEquals(400, send(get(base + "?sort=abc")).getResponse().getStatus());
        assertEquals(400, send(get(base + "?rating=7")).getResponse().getStatus());
        assertEquals(200, send(get(base + "?size=1000&sort=ratingDesc&rating=5")).getResponse().getStatus());
        assertEquals(404, send(get("/api/v1/products/999999999/reviews")).getResponse().getStatus());
    }

    @Test
    @DisplayName("R06: hai yêu cầu đồng thời cùng đơn + sản phẩm -> đúng một thành công, tổng hợp chỉ tăng 1 lần")
    void concurrentSubmit_OnlyOneWins() throws Exception {
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Integer> task = () -> {
                start.await();
                return postReview(ownerToken, ownerOrderId, 5, null).getResponse().getStatus();
            };
            futures.add(pool.submit(task));
        }
        start.countDown();
        int created = 0;
        int conflict = 0;
        for (Future<Integer> f : futures) {
            int status = f.get();
            if (status == 201) {
                created++;
            } else if (status == 409) {
                conflict++;
            }
        }
        pool.shutdown();

        assertEquals(1, created);
        assertEquals(threads - 1, conflict);
        Product reloaded = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(1, reloaded.getRatingCount());
        assertEquals(5, reloaded.getRatingSum());
    }
}
