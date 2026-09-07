package com.multimart.config;

import com.multimart.modules.flashsale.entity.FlashSaleEvent;
import com.multimart.modules.flashsale.entity.FlashSaleProduct;
import com.multimart.modules.flashsale.repository.FlashSaleEventRepository;
import com.multimart.modules.flashsale.repository.FlashSaleProductRepository;
import com.multimart.modules.flashsale.service.FlashSaleEngineService;
import com.multimart.modules.product.entity.Product;
import com.multimart.modules.product.repository.ProductRepository;
import com.multimart.modules.user.entity.Role;
import com.multimart.modules.user.entity.User;
import com.multimart.modules.user.repository.RoleRepository;
import com.multimart.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Lớp khởi tạo dữ liệu mẫu (Database Seeder) khi hệ thống MultiMart khởi động.
 * Đảm bảo sẵn sàng tài khoản Quản trị, tài khoản Khách mua, Danh mục sản phẩm công nghệ
 * và Đợt Flash Sale đang diễn ra kèm Cache Warm-up trên Redis để trải nghiệm giao diện người dùng ngay lập tức.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ProductRepository productRepository;
    private final FlashSaleEventRepository eventRepository;
    private final FlashSaleProductRepository flashSaleProductRepository;
    private final FlashSaleEngineService flashSaleEngineService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        try {
            log.info("Checking and seeding initial data for MultiMart...");

            // 1. Khởi tạo Roles
            Role roleUser = roleRepository.findByName("ROLE_USER")
                    .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_USER").build()));

            Role roleAdmin = roleRepository.findByName("ROLE_ADMIN")
                    .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_ADMIN").build()));

            // 2. Khởi tạo tài khoản Quản trị (Admin)
            if (userRepository.findByEmail("admin@multimart.com").isEmpty()) {
                User admin = User.builder()
                        .email("admin@multimart.com")
                        .password(passwordEncoder.encode("Admin@123456"))
                        .fullName("MultiMart Quản Trị Viên")
                        .phone("0988888888")
                        .points(1000)
                        .roles(Set.of(roleAdmin, roleUser))
                        .build();
                userRepository.save(admin);
                log.info("Seeded Admin user: admin@multimart.com / Admin@123456");
            }

            // 3. Khởi tạo tài khoản Khách mua mẫu (Buyer)
            if (userRepository.findByEmail("buyer@multimart.com").isEmpty()) {
                User buyer = User.builder()
                        .email("buyer@multimart.com")
                        .password(passwordEncoder.encode("123456"))
                        .fullName("Nguyễn Văn Mua")
                        .phone("0912345678")
                        .points(100)
                        .roles(Set.of(roleUser))
                        .build();
                userRepository.save(buyer);
                log.info("Seeded Buyer user: buyer@multimart.com / 123456");
            }

            // 4. Khởi tạo sản phẩm mẫu
            Product p1 = null;
            Product p2 = null;
            if (productRepository.count() == 0) {
                p1 = productRepository.save(Product.builder()
                        .name("iPhone 16 Pro Max 256GB Titan Tự Nhiên")
                        .description("Siêu phẩm công nghệ Apple với chip A18 Pro, camera 48MP và khung viền Titan siêu nhẹ.")
                        .imageUrl("https://images.unsplash.com/photo-1695048133142-1a20484d2569?w=500&q=80")
                        .originalPrice(new BigDecimal("34990000"))
                        .totalStock(200)
                        .status("ACTIVE")
                        .build());

                p2 = productRepository.save(Product.builder()
                        .name("Tai nghe Sony WH-1000XM5 Chống Ồn Đỉnh Cao")
                        .description("Tai nghe chống ồn chủ động không dây hàng đầu, thời lượng pin 30 giờ, âm thanh Hi-Res.")
                        .imageUrl("https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=500&q=80")
                        .originalPrice(new BigDecimal("7490000"))
                        .totalStock(150)
                        .status("ACTIVE")
                        .build());

                productRepository.save(Product.builder()
                        .name("Bàn phím cơ không dây Keychron K2 Pro QMK/VIA")
                        .description("Bàn phím cơ layout 75% switch cơ học cao cấp, hỗ trợ Mac/Windows, đèn nền RGB.")
                        .imageUrl("https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=500&q=80")
                        .originalPrice(new BigDecimal("2390000"))
                        .totalStock(100)
                        .status("ACTIVE")
                        .build());

                productRepository.save(Product.builder()
                        .name("Đồng hồ Apple Watch Ultra 2 GPS + Cellular 49mm")
                        .description("Đồng hồ thể thao chuyên nghiệp với vỏ titan chuẩn quân đội, màn hình sáng 3000 nits.")
                        .imageUrl("https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=500&q=80")
                        .originalPrice(new BigDecimal("21990000"))
                        .totalStock(80)
                        .status("ACTIVE")
                        .build());

                log.info("Seeded 4 sample products successfully.");
            }

            // 5. Khởi tạo sự kiện Flash Sale mẫu và nạp Cache Warm-up vào Redis
            if (eventRepository.count() == 0 && p1 != null && p2 != null) {
                LocalDateTime now = LocalDateTime.now();
                FlashSaleEvent event = FlashSaleEvent.builder()
                        .name("⚡ ĐẤU TRƯỜNG CÔNG NGHỆ FLASH SALE")
                        .startTime(now.minusHours(1))
                        .endTime(now.plusHours(24))
                        .status("ACTIVE")
                        .build();
                FlashSaleEvent savedEvent = eventRepository.save(event);

                // Thêm sản phẩm 1 vào Flash Sale
                FlashSaleProduct fp1 = FlashSaleProduct.builder()
                        .event(savedEvent)
                        .product(p1)
                        .flashPrice(new BigDecimal("24990000"))
                        .totalAllocated(50)
                        .availableStock(50)
                        .purchaseLimitPerUser(1)
                        .build();
                flashSaleProductRepository.save(fp1);

                // Thêm sản phẩm 2 vào Flash Sale
                FlashSaleProduct fp2 = FlashSaleProduct.builder()
                        .event(savedEvent)
                        .product(p2)
                        .flashPrice(new BigDecimal("4490000"))
                        .totalAllocated(30)
                        .availableStock(30)
                        .purchaseLimitPerUser(2)
                        .build();
                flashSaleProductRepository.save(fp2);

                log.info("Seeded sample FlashSaleEvent with 2 discounted products: eventId={}", savedEvent.getId());

                // Thực hiện Cache Warm-up tự động lên Redis
                try {
                    flashSaleEngineService.warmUpEvent(savedEvent.getId());
                    log.info("Successfully performed initial Cache Warm-up to Redis for eventId={}", savedEvent.getId());
                } catch (Exception ex) {
                    log.warn("Notice: Initial Redis warm-up skipped during startup (Redis might not be available or in test profile): {}", ex.getMessage());
                }
            }

            log.info("DataInitializer completed successfully.");
        } catch (Exception e) {
            log.error("Error during DataInitializer execution: {}", e.getMessage(), e);
        }
    }
}
