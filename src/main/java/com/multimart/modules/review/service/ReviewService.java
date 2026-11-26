package com.multimart.modules.review.service;

import com.multimart.common.cache.RedisCacheService;
import com.multimart.common.exception.AppException;
import com.multimart.common.exception.ErrorCode;
import com.multimart.common.response.PageResponse;
import com.multimart.modules.order.repository.OrderRepository;
import com.multimart.modules.product.entity.Product;
import com.multimart.modules.product.repository.ProductRepository;
import com.multimart.modules.product.service.ProductService;
import com.multimart.modules.review.dto.CreateReviewRequest;
import com.multimart.modules.review.dto.ReviewResponse;
import com.multimart.modules.review.dto.ReviewSummaryResponse;
import com.multimart.modules.review.dto.UpdateReviewRequest;
import com.multimart.modules.review.entity.ProductReview;
import com.multimart.modules.review.entity.ReviewStatus;
import com.multimart.modules.review.repository.ProductReviewRepository;
import com.multimart.modules.user.entity.User;
import com.multimart.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Nghiệp vụ đánh giá và xếp hạng sản phẩm (SRS-REVIEW-001).
 * Điểm tổng hợp trên sản phẩm luôn được cập nhật nguyên tử trong cùng giao dịch với đánh giá.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final int MAX_COMMENT_LENGTH = 1000;
    private static final int MAX_PAGE_SIZE = 50;
    private static final String ANONYMOUS_NAME = "Người dùng ẩn danh";

    private final ProductReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final RedisCacheService cacheService;

    // =====================================================================
    // NGƯỜI DÙNG
    // =====================================================================

    /**
     * Gửi đánh giá cho một sản phẩm thuộc đơn hàng đã giao của chính người dùng.
     *
     * @param userId    id người dùng
     * @param productId id sản phẩm
     * @param request   dữ liệu đánh giá
     * @return đánh giá vừa tạo
     * @throws AppException PRODUCT_NOT_FOUND, PRODUCT_NOT_AVAILABLE, REVIEW_NOT_ELIGIBLE, REVIEW_ALREADY_EXISTS
     */
    @Transactional
    public ReviewResponse createReview(Long userId, Long productId, CreateReviewRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        if (!"ACTIVE".equals(product.getStatus())) {
            throw new AppException(ErrorCode.PRODUCT_NOT_AVAILABLE);
        }
        if (!orderRepository.existsDeliveredOrderWithProduct(request.getOrderId(), userId, productId)) {
            throw new AppException(ErrorCode.REVIEW_NOT_ELIGIBLE);
        }
        if (reviewRepository.existsByOrderIdAndProductIdAndDeletedFalse(request.getOrderId(), productId)) {
            throw new AppException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        ProductReview review = ProductReview.builder()
                .productId(productId)
                .userId(userId)
                .orderId(request.getOrderId())
                .rating(request.getRating())
                .comment(normalizeComment(request.getComment()))
                .anonymous(request.getAnonymous() == null || request.getAnonymous())
                .build();
        try {
            review = reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException e) {
            // Hai yêu cầu đồng thời cùng đơn + sản phẩm: ràng buộc UNIQUE ở DB chặn yêu cầu thứ hai
            throw new AppException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        productRepository.adjustRating(productId, 1, review.getRating());
        evictProductDetail(productId);
        log.info("Review created: id={}, productId={}, userId={}, rating={}",
                review.getId(), productId, userId, review.getRating());
        return toResponse(review, nameOf(userId), true);
    }

    /**
     * Sửa đánh giá của chính mình (không giới hạn thời gian).
     *
     * @param userId   id người dùng
     * @param reviewId id đánh giá
     * @param request  dữ liệu cần sửa (ít nhất một trường)
     * @return đánh giá sau khi sửa
     * @throws AppException REVIEW_NOT_FOUND, REVIEW_ACCESS_DENIED, INVALID_REQUEST
     */
    @Transactional
    public ReviewResponse updateReview(Long userId, Long reviewId, UpdateReviewRequest request) {
        if (request.getRating() == null && request.getComment() == null && request.getAnonymous() == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        ProductReview review = loadForUpdate(reviewId);
        assertOwner(review, userId);

        if (request.getRating() != null) {
            int diff = request.getRating() - review.getRating();
            if (diff != 0 && review.getStatus() == ReviewStatus.VISIBLE) {
                productRepository.adjustRating(review.getProductId(), 0, diff);
            }
            review.setRating(request.getRating());
        }
        if (request.getComment() != null) {
            review.setComment(normalizeComment(request.getComment()));
        }
        if (request.getAnonymous() != null) {
            review.setAnonymous(request.getAnonymous());
        }
        review.setEdited(true);
        reviewRepository.save(review);

        evictProductDetail(review.getProductId());
        return toResponse(review, nameOf(userId), true);
    }

    /**
     * Xóa mềm đánh giá của chính mình.
     *
     * @param userId   id người dùng
     * @param reviewId id đánh giá
     * @throws AppException REVIEW_NOT_FOUND, REVIEW_ACCESS_DENIED
     */
    @Transactional
    public void deleteReview(Long userId, Long reviewId) {
        ProductReview review = loadForUpdate(reviewId);
        assertOwner(review, userId);

        review.setDeleted(true);
        reviewRepository.save(review);
        if (review.getStatus() == ReviewStatus.VISIBLE) {
            productRepository.adjustRating(review.getProductId(), -1, -review.getRating());
        }
        evictProductDetail(review.getProductId());
        log.info("Review deleted by owner: id={}, productId={}", reviewId, review.getProductId());
    }

    /**
     * Danh sách đánh giá của tôi (gồm cả đánh giá đang bị ẩn).
     *
     * @param userId id người dùng
     * @param page   số trang (từ 0)
     * @param size   kích thước trang (tối đa 50)
     * @return trang đánh giá
     */
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getMyReviews(Long userId, int page, int size) {
        Pageable pageable = buildPageable(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return toPage(reviewRepository.findByUserIdAndDeletedFalse(userId, pageable), true);
    }

    // =====================================================================
    // CÔNG KHAI
    // =====================================================================

    /**
     * Danh sách đánh giá đang hiển thị của một sản phẩm.
     *
     * @param productId id sản phẩm
     * @param rating    lọc theo số sao (tùy chọn, 1 đến 5)
     * @param sort      newest (mặc định), ratingDesc hoặc ratingAsc
     * @param page      số trang (từ 0)
     * @param size      kích thước trang (vượt 50 thì cắt về 50)
     * @return trang đánh giá
     * @throws AppException PRODUCT_NOT_FOUND, INVALID_REQUEST
     */
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getProductReviews(Long productId, Integer rating, String sort, int page, int size) {
        if (!productRepository.existsById(productId)) {
            throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        Pageable pageable = buildPageable(page, size, resolveSort(sort));
        Page<ProductReview> result = rating == null
                ? reviewRepository.findByProductIdAndStatusAndDeletedFalse(productId, ReviewStatus.VISIBLE, pageable)
                : reviewRepository.findByProductIdAndStatusAndRatingAndDeletedFalse(
                        productId, ReviewStatus.VISIBLE, rating, pageable);
        return toPage(result, false);
    }

    /**
     * Thống kê đánh giá của một sản phẩm.
     *
     * @param productId id sản phẩm
     * @return điểm trung bình, tổng số đánh giá và phân bố theo sao
     * @throws AppException PRODUCT_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public ReviewSummaryResponse getSummary(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        Map<Integer, Long> distribution = new HashMap<>();
        for (int star = 1; star <= 5; star++) {
            distribution.put(star, 0L);
        }
        for (Object[] row : reviewRepository.countVisibleByRating(productId)) {
            distribution.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        int count = product.getRatingCount() == null ? 0 : product.getRatingCount();
        int sum = product.getRatingSum() == null ? 0 : product.getRatingSum();
        return ReviewSummaryResponse.builder()
                .ratingAverage(RatingCalculator.average(sum, count))
                .ratingCount(count)
                .distribution(new java.util.TreeMap<>(distribution))
                .build();
    }

    // =====================================================================
    // ADMIN
    // =====================================================================

    /**
     * Ẩn hoặc hiện một đánh giá. Đặt lại đúng trạng thái hiện tại thì không làm gì.
     *
     * @param reviewId id đánh giá
     * @param visible  {@code true} để hiện, {@code false} để ẩn
     * @return đánh giá sau khi đổi trạng thái
     * @throws AppException REVIEW_NOT_FOUND
     */
    @Transactional
    public ReviewResponse setVisibility(Long reviewId, boolean visible) {
        ProductReview review = loadForUpdate(reviewId);
        ReviewStatus target = visible ? ReviewStatus.VISIBLE : ReviewStatus.HIDDEN;
        if (review.getStatus() != target) {
            review.setStatus(target);
            reviewRepository.save(review);
            int sign = visible ? 1 : -1;
            productRepository.adjustRating(review.getProductId(), sign, sign * review.getRating());
            evictProductDetail(review.getProductId());
            log.info("Review visibility changed by admin: id={}, status={}", reviewId, target);
        }
        return toResponse(review, nameOf(review.getUserId()), true);
    }

    /**
     * Danh sách đánh giá để Admin kiểm duyệt.
     *
     * @param status lọc theo trạng thái (tùy chọn)
     * @param page   số trang (từ 0)
     * @param size   kích thước trang (tối đa 50)
     * @return trang đánh giá
     */
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getAllReviews(ReviewStatus status, int page, int size) {
        Pageable pageable = buildPageable(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<ProductReview> result = status == null
                ? reviewRepository.findByDeletedFalse(pageable)
                : reviewRepository.findByStatusAndDeletedFalse(status, pageable);
        return toPage(result, true);
    }

    // =====================================================================
    // TIỆN ÍCH NỘI BỘ
    // =====================================================================

    /** Tải đánh giá có khóa dòng, ném REVIEW_NOT_FOUND nếu không có hoặc đã xóa. */
    private ProductReview loadForUpdate(Long reviewId) {
        return reviewRepository.findByIdForUpdate(reviewId)
                .orElseThrow(() -> new AppException(ErrorCode.REVIEW_NOT_FOUND));
    }

    /** Chỉ chủ đánh giá mới được thao tác. */
    private void assertOwner(ProductReview review, Long userId) {
        if (!review.getUserId().equals(userId)) {
            throw new AppException(ErrorCode.REVIEW_ACCESS_DENIED);
        }
    }

    /** Cắt khoảng trắng đầu cuối; chuỗi rỗng coi như không có nhận xét. */
    private String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String trimmed = comment.trim();
        if (trimmed.length() > MAX_COMMENT_LENGTH) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Ánh xạ tham số sort sang Sort của Spring Data (whitelist), luôn có thứ tự phụ theo id giảm dần. */
    private Sort resolveSort(String sort) {
        String key = sort == null || sort.isBlank() ? "newest" : sort;
        return switch (key) {
            case "newest" -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
            case "ratingDesc" -> Sort.by(Sort.Order.desc("rating"), Sort.Order.desc("id"));
            case "ratingAsc" -> Sort.by(Sort.Order.asc("rating"), Sort.Order.desc("id"));
            default -> throw new AppException(ErrorCode.INVALID_REQUEST);
        };
    }

    /** Kiểm tra tham số phân trang và cắt size về tối đa 50. */
    private Pageable buildPageable(int page, int size, Sort sort) {
        if (page < 0 || size < 1) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        return PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), sort);
    }

    /** Xóa cache chi tiết sản phẩm sau khi giao dịch commit để điểm mới hiển thị ngay. */
    private void evictProductDetail(Long productId) {
        cacheService.evictAfterCommit(ProductService.DETAIL_CACHE_PREFIX + productId);
    }

    /** Lấy họ tên người dùng; null nếu không tìm thấy. */
    private String nameOf(Long userId) {
        return userRepository.findById(userId).map(User::getFullName).orElse(null);
    }

    /** Chuyển trang entity sang trang DTO, tải họ tên người viết bằng một truy vấn. */
    private PageResponse<ReviewResponse> toPage(Page<ProductReview> page, boolean ownerView) {
        Set<Long> userIds = new HashSet<>();
        page.getContent().forEach(r -> userIds.add(r.getUserId()));
        Map<Long, String> names = loadNames(userIds);
        return PageResponse.from(page.map(r -> toResponse(r, names.get(r.getUserId()), ownerView)));
    }

    /** Tải họ tên của nhiều người dùng. */
    private Map<Long, String> loadNames(Collection<Long> userIds) {
        Map<Long, String> names = new HashMap<>();
        if (userIds.isEmpty()) {
            return names;
        }
        List<User> users = userRepository.findAllById(userIds);
        for (User user : users) {
            names.put(user.getId(), user.getFullName());
        }
        return names;
    }

    /** Chuyển entity sang DTO; chỉ lộ orderId khi dành cho chủ đánh giá hoặc Admin. */
    private ReviewResponse toResponse(ProductReview review, String fullName, boolean ownerView) {
        boolean showName = !review.isAnonymous() && fullName != null && !fullName.isBlank();
        return ReviewResponse.builder()
                .id(review.getId())
                .productId(review.getProductId())
                .orderId(ownerView ? review.getOrderId() : null)
                .rating(review.getRating())
                .comment(review.getComment())
                .reviewerName(showName ? fullName : ANONYMOUS_NAME)
                .anonymous(review.isAnonymous())
                .status(review.getStatus().name())
                .edited(review.isEdited())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}
