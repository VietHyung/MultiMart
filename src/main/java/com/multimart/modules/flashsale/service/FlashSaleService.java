package com.multimart.modules.flashsale.service;

import com.multimart.common.exception.AppException;
import com.multimart.common.exception.ErrorCode;
import com.multimart.modules.flashsale.dto.*;
import com.multimart.modules.flashsale.entity.FlashSaleEvent;
import com.multimart.modules.flashsale.entity.FlashSaleProduct;
import com.multimart.modules.flashsale.repository.FlashSaleEventRepository;
import com.multimart.modules.flashsale.repository.FlashSaleProductRepository;
import com.multimart.modules.product.entity.Product;
import com.multimart.modules.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service xử lý toàn bộ logic nghiệp vụ của phân hệ Flash-Sale:
 * - Tạo đợt sự kiện khuyến mãi.
 * - Cơ chế giam kho gốc (Inventory Pre-allocation) khi đưa sản phẩm vào sự kiện.
 * - Lấy danh sách sự kiện đang hoạt động kèm đồng hồ đếm ngược (Countdown Timer).
 * - Xem chi tiết sự kiện và danh sách sản phẩm giảm giá.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlashSaleService {

    private final FlashSaleEventRepository eventRepository;
    private final FlashSaleProductRepository flashSaleProductRepository;
    private final ProductRepository productRepository;

    /**
     * Tạo một sự kiện Flash-Sale mới với trạng thái mặc định là UPCOMING.
     * Validate thời gian bắt đầu phải trước thời gian kết thúc.
     *
     * @param request dữ liệu tạo sự kiện (tên sự kiện, thời gian bắt đầu, thời gian kết thúc)
     * @return FlashSaleEventResponse chứa thông tin sự kiện vừa tạo
     */
    @Transactional
    public FlashSaleEventResponse createEvent(CreateEventRequest request) {
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Thời gian kết thúc phải sau thời gian bắt đầu");
        }

        FlashSaleEvent event = FlashSaleEvent.builder()
                .name(request.getName())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status("UPCOMING")
                .build();

        FlashSaleEvent savedEvent = eventRepository.save(event);
        log.info("Created FlashSale event: id={}, name={}", savedEvent.getId(), savedEvent.getName());

        return mapToEventResponse(savedEvent);
    }

    /**
     * Đưa sản phẩm vào sự kiện Flash-Sale và thực hiện cơ chế Giam Kho Tồn (Ring-fencing):
     * 1. Kiểm tra sự kiện chưa kết thúc.
     * 2. Kiểm tra sản phẩm chưa có trong sự kiện.
     * 3. Kiểm tra giá sale < giá gốc.
     * 4. Kiểm tra kho gốc đủ số lượng phân bổ.
     * 5. Trừ kho gốc (giam kho) và cấp sang kho FlashSaleProduct.
     *
     * @param eventId mã sự kiện Flash-Sale
     * @param request thông tin phân bổ (productId, flashPrice, totalAllocated, purchaseLimitPerUser)
     * @return FlashSaleProductResponse thông tin sản phẩm giảm giá
     */
    @Transactional
    public FlashSaleProductResponse addProductToEvent(Long eventId, AddProductToEventRequest request) {
        FlashSaleEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new AppException(ErrorCode.EVENT_NOT_FOUND, "Không tìm thấy sự kiện Flash-Sale: " + eventId));

        LocalDateTime now = LocalDateTime.now();
        if (event.getEndTime().isBefore(now)) {
            throw new AppException(ErrorCode.EVENT_NOT_ACTIVE, "Sự kiện Flash-Sale này đã kết thúc, không thể thêm sản phẩm");
        }

        if (flashSaleProductRepository.existsByEventIdAndProductId(eventId, request.getProductId())) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Sản phẩm này đã tồn tại trong sự kiện Flash-Sale");
        }

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND, "Không tìm thấy sản phẩm gốc: " + request.getProductId()));

        if (request.getFlashPrice().compareTo(product.getOriginalPrice()) >= 0) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Giá Flash-Sale phải nhỏ hơn giá gốc của sản phẩm");
        }

        // Kiểm tra tồn kho gốc: Phải đủ số lượng để trích xuất ra đợt sale
        if (product.getTotalStock() < request.getTotalAllocated()) {
            throw new AppException(ErrorCode.OUT_OF_STOCK,
                    String.format("Kho sản phẩm gốc không đủ số lượng (còn %d, yêu cầu %d)",
                            product.getTotalStock(), request.getTotalAllocated()));
        }

        // Giam kho (Inventory Pre-allocation): Trừ kho gốc để chuyển sang kho Flash-Sale
        product.setTotalStock(product.getTotalStock() - request.getTotalAllocated());
        productRepository.save(product);

        FlashSaleProduct flashSaleProduct = FlashSaleProduct.builder()
                .event(event)
                .product(product)
                .flashPrice(request.getFlashPrice())
                .totalAllocated(request.getTotalAllocated())
                .availableStock(request.getTotalAllocated())
                .purchaseLimitPerUser(request.getPurchaseLimitPerUser())
                .build();

        FlashSaleProduct saved = flashSaleProductRepository.save(flashSaleProduct);
        log.info("Added product to FlashSale: eventId={}, productId={}, allocated={}",
                eventId, product.getId(), request.getTotalAllocated());

        return mapToProductResponse(saved);
    }

    /**
     * Lấy danh sách tất cả các sự kiện Flash-Sale đang trong khung giờ diễn ra.
     * Tự động chuyển đổi trạng thái sự kiện từ UPCOMING sang ACTIVE nếu đến giờ.
     *
     * @return danh sách FlashSaleEventResponse kèm số giây đếm ngược remainingSeconds
     */
    @Transactional
    public List<FlashSaleEventResponse> getActiveEvents() {
        LocalDateTime now = LocalDateTime.now();
        List<FlashSaleEvent> events = eventRepository.findActiveEvents(now);

        // Cập nhật trạng thái thành ACTIVE nếu đang là UPCOMING
        for (FlashSaleEvent event : events) {
            if ("UPCOMING".equals(event.getStatus())) {
                event.setStatus("ACTIVE");
                eventRepository.save(event);
            }
        }

        return events.stream()
                .map(event -> {
                    FlashSaleEventResponse response = mapToEventResponse(event);
                    List<FlashSaleProduct> products = flashSaleProductRepository.findByEventId(event.getId());
                    response.setProducts(products.stream().map(this::mapToProductResponse).collect(Collectors.toList()));
                    return response;
                })
                .collect(Collectors.toList());
    }

    /**
     * Lấy thông tin chi tiết một sự kiện Flash-Sale cùng toàn bộ danh sách sản phẩm giảm giá bên trong.
     *
     * @param eventId mã sự kiện Flash-Sale
     * @return FlashSaleEventResponse chứa thông tin sự kiện và danh sách FlashSaleProductResponse
     */
    public FlashSaleEventResponse getEventDetails(Long eventId) {
        FlashSaleEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new AppException(ErrorCode.EVENT_NOT_FOUND, "Không tìm thấy sự kiện Flash-Sale: " + eventId));

        List<FlashSaleProduct> products = flashSaleProductRepository.findByEventId(eventId);
        FlashSaleEventResponse response = mapToEventResponse(event);
        response.setProducts(products.stream().map(this::mapToProductResponse).collect(Collectors.toList()));

        return response;
    }

    /**
     * Helper chuyển đổi thực thể FlashSaleEvent sang DTO FlashSaleEventResponse.
     * Tự động tính toán số giây đếm ngược còn lại (remainingSeconds) cho client.
     *
     * @param event đối tượng thực thể FlashSaleEvent
     * @return DTO FlashSaleEventResponse
     */
    private FlashSaleEventResponse mapToEventResponse(FlashSaleEvent event) {
        LocalDateTime now = LocalDateTime.now();
        long remainingSeconds = 0;
        if (event.getEndTime().isAfter(now)) {
            remainingSeconds = Duration.between(now, event.getEndTime()).getSeconds();
        }

        return FlashSaleEventResponse.builder()
                .id(event.getId())
                .name(event.getName())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .status(event.getStatus())
                .remainingSeconds(remainingSeconds)
                .build();
    }

    /**
     * Helper chuyển đổi thực thể FlashSaleProduct sang DTO FlashSaleProductResponse.
     * Tự động tính toán % giảm giá (discountPercentage) và cờ hết hàng (soldOut).
     *
     * @param fsp đối tượng thực thể FlashSaleProduct
     * @return DTO FlashSaleProductResponse
     */
    private FlashSaleProductResponse mapToProductResponse(FlashSaleProduct fsp) {
        Product p = fsp.getProduct();
        int discount = 0;
        if (p.getOriginalPrice() != null && p.getOriginalPrice().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal diff = p.getOriginalPrice().subtract(fsp.getFlashPrice());
            discount = diff.multiply(BigDecimal.valueOf(100))
                    .divide(p.getOriginalPrice(), 0, RoundingMode.HALF_UP)
                    .intValue();
        }

        return FlashSaleProductResponse.builder()
                .id(fsp.getId())
                .productId(p.getId())
                .productName(p.getName())
                .imageUrl(p.getImageUrl())
                .originalPrice(p.getOriginalPrice())
                .flashPrice(fsp.getFlashPrice())
                .discountPercentage(discount)
                .totalAllocated(fsp.getTotalAllocated())
                .availableStock(fsp.getAvailableStock())
                .purchaseLimitPerUser(fsp.getPurchaseLimitPerUser())
                .soldOut(fsp.getAvailableStock() <= 0)
                .build();
    }
}
