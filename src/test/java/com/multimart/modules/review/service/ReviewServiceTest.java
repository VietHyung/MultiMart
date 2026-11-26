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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm thử nghiệp vụ đánh giá sản phẩm bằng Mockito (SRS-REVIEW-001: R01-R25).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewServiceTest {

    private static final Long USER = 1L;
    private static final Long PRODUCT = 5L;
    private static final Long ORDER = 31L;

    @Mock
    private ProductReviewRepository reviewRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RedisCacheService cacheService;
    @InjectMocks
    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        Product product = Product.builder().name("SP").status("ACTIVE").build();
        product.setId(PRODUCT);
        when(productRepository.findById(PRODUCT)).thenReturn(Optional.of(product));
        when(productRepository.existsById(PRODUCT)).thenReturn(true);

        User user = User.builder().fullName("Nguyễn Văn An").build();
        user.setId(USER);
        when(userRepository.findById(USER)).thenReturn(Optional.of(user));
        when(userRepository.findAllById(any())).thenReturn(List.of(user));

        when(orderRepository.existsDeliveredOrderWithProduct(ORDER, USER, PRODUCT)).thenReturn(true);
        when(reviewRepository.saveAndFlush(any(ProductReview.class))).thenAnswer(inv -> {
            ProductReview r = inv.getArgument(0);
            r.setId(100L);
            return r;
        });
        when(reviewRepository.save(any(ProductReview.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private CreateReviewRequest create(int rating, String comment, Boolean anonymous) {
        return CreateReviewRequest.builder().orderId(ORDER).rating(rating).comment(comment).anonymous(anonymous).build();
    }

    private ProductReview existing(int rating, ReviewStatus status) {
        ProductReview r = ProductReview.builder().productId(PRODUCT).userId(USER).orderId(ORDER)
                .rating(rating).status(status).build();
        r.setId(100L);
        when(reviewRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(r));
        return r;
    }

    private ErrorCode errorOf(Runnable action) {
        return assertThrows(AppException.class, action::run).getErrorCode();
    }

    // ------------------------------ gửi đánh giá ------------------------------

    @Test
    @DisplayName("R01: Gửi đánh giá hợp lệ -> cộng tổng hợp +1 và +số sao, xóa cache chi tiết sản phẩm")
    void create_Success() {
        ReviewResponse response = reviewService.createReview(USER, PRODUCT, create(5, "  Tốt  ", false));

        assertEquals(5, response.getRating());
        assertEquals("Tốt", response.getComment());
        assertEquals(ORDER, response.getOrderId());
        assertEquals("VISIBLE", response.getStatus());
        verify(productRepository).adjustRating(PRODUCT, 1, 5);
        verify(cacheService).evictAfterCommit(ProductService.DETAIL_CACHE_PREFIX + PRODUCT);
    }

    @Test
    @DisplayName("R02: Không có nhận xét hoặc nhận xét rỗng -> comment null")
    void create_BlankComment_StoredAsNull() {
        assertNull(reviewService.createReview(USER, PRODUCT, create(4, null, null)).getComment());
        assertNull(reviewService.createReview(USER, PRODUCT, create(4, "   ", null)).getComment());
    }

    @Test
    @DisplayName("R26: anonymous bỏ trống hoặc true -> tên ẩn danh; false -> hiện họ tên")
    void create_AnonymousHandling() {
        assertEquals("Người dùng ẩn danh", reviewService.createReview(USER, PRODUCT, create(5, null, null)).getReviewerName());
        assertEquals("Người dùng ẩn danh", reviewService.createReview(USER, PRODUCT, create(5, null, true)).getReviewerName());
        ReviewResponse shown = reviewService.createReview(USER, PRODUCT, create(5, null, false));
        assertEquals("Nguyễn Văn An", shown.getReviewerName());
        assertFalse(shown.isAnonymous());
    }

    @Test
    @DisplayName("R03/R04: Đơn không phải của mình, chưa giao hoặc không chứa sản phẩm -> REVIEW_NOT_ELIGIBLE")
    void create_NotEligible() {
        when(orderRepository.existsDeliveredOrderWithProduct(ORDER, USER, PRODUCT)).thenReturn(false);

        assertEquals(ErrorCode.REVIEW_NOT_ELIGIBLE,
                errorOf(() -> reviewService.createReview(USER, PRODUCT, create(5, null, null))));
        verify(reviewRepository, never()).saveAndFlush(any());
        verify(productRepository, never()).adjustRating(anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("R05: Đã có đánh giá cho đơn + sản phẩm -> REVIEW_ALREADY_EXISTS")
    void create_AlreadyExists() {
        when(reviewRepository.existsByOrderIdAndProductIdAndDeletedFalse(ORDER, PRODUCT)).thenReturn(true);

        assertEquals(ErrorCode.REVIEW_ALREADY_EXISTS,
                errorOf(() -> reviewService.createReview(USER, PRODUCT, create(5, null, null))));
        verify(productRepository, never()).adjustRating(anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("R06: Vi phạm UNIQUE do hai yêu cầu đồng thời -> REVIEW_ALREADY_EXISTS và không cộng tổng hợp")
    void create_ConcurrentDuplicate_MapsToAlreadyExists() {
        when(reviewRepository.saveAndFlush(any(ProductReview.class)))
                .thenThrow(new DataIntegrityViolationException("uq_review_order_product"));

        assertEquals(ErrorCode.REVIEW_ALREADY_EXISTS,
                errorOf(() -> reviewService.createReview(USER, PRODUCT, create(5, null, null))));
        verify(productRepository, never()).adjustRating(anyLong(), anyInt(), anyInt());
        verify(cacheService, never()).evictAfterCommit(any());
    }

    @Test
    @DisplayName("R09: Sản phẩm không tồn tại -> PRODUCT_NOT_FOUND; INACTIVE -> PRODUCT_NOT_AVAILABLE")
    void create_InvalidProduct() {
        when(productRepository.findById(404L)).thenReturn(Optional.empty());
        Product inactive = Product.builder().name("SP").status("INACTIVE").build();
        inactive.setId(6L);
        when(productRepository.findById(6L)).thenReturn(Optional.of(inactive));

        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, errorOf(() -> reviewService.createReview(USER, 404L, create(5, null, null))));
        assertEquals(ErrorCode.PRODUCT_NOT_AVAILABLE, errorOf(() -> reviewService.createReview(USER, 6L, create(5, null, null))));
    }

    @Test
    @DisplayName("R08: Nhận xét đúng 1000 ký tự được chấp nhận")
    void create_CommentAtLimit() {
        assertEquals(1000, reviewService.createReview(USER, PRODUCT, create(5, "x".repeat(1000), null)).getComment().length());
    }

    // ------------------------------ sửa ------------------------------

    @Test
    @DisplayName("R11: Sửa 5 -> 3 khi đang hiển thị: tổng sao giảm 2, số lượng giữ nguyên, edited=true")
    void update_RatingChange_AdjustsDiffOnly() {
        existing(5, ReviewStatus.VISIBLE);

        ReviewResponse response = reviewService.updateReview(USER, 100L, UpdateReviewRequest.builder().rating(3).build());

        assertEquals(3, response.getRating());
        assertTrue(response.isEdited());
        verify(productRepository).adjustRating(PRODUCT, 0, -2);
        verify(cacheService).evictAfterCommit(ProductService.DETAIL_CACHE_PREFIX + PRODUCT);
    }

    @Test
    @DisplayName("Sửa nhưng số sao không đổi -> không đụng tổng hợp")
    void update_SameRating_NoAdjust() {
        existing(4, ReviewStatus.VISIBLE);

        reviewService.updateReview(USER, 100L, UpdateReviewRequest.builder().rating(4).comment("ok").build());

        verify(productRepository, never()).adjustRating(anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("R21: Sửa đánh giá đang bị ẩn -> vẫn HIDDEN, không đổi tổng hợp")
    void update_HiddenReview_NoAdjustAndStaysHidden() {
        existing(5, ReviewStatus.HIDDEN);

        ReviewResponse response = reviewService.updateReview(USER, 100L, UpdateReviewRequest.builder().rating(1).build());

        assertEquals("HIDDEN", response.getStatus());
        verify(productRepository, never()).adjustRating(anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("R26b: Đổi anonymous khi sửa; comment rỗng nghĩa là xóa nhận xét")
    void update_AnonymousAndClearComment() {
        ProductReview r = existing(5, ReviewStatus.VISIBLE);
        r.setComment("cũ");

        ReviewResponse response = reviewService.updateReview(USER, 100L,
                UpdateReviewRequest.builder().anonymous(false).comment("").build());

        assertNull(response.getComment());
        assertEquals("Nguyễn Văn An", response.getReviewerName());
    }

    @Test
    @DisplayName("Sửa không gửi trường nào -> INVALID_REQUEST")
    void update_EmptyRequest_Throws() {
        existing(5, ReviewStatus.VISIBLE);
        assertEquals(ErrorCode.INVALID_REQUEST,
                errorOf(() -> reviewService.updateReview(USER, 100L, new UpdateReviewRequest())));
    }

    @Test
    @DisplayName("R12: Sửa đánh giá của người khác -> REVIEW_ACCESS_DENIED")
    void update_NotOwner_Throws() {
        existing(5, ReviewStatus.VISIBLE);
        assertEquals(ErrorCode.REVIEW_ACCESS_DENIED,
                errorOf(() -> reviewService.updateReview(2L, 100L, UpdateReviewRequest.builder().rating(1).build())));
        verify(productRepository, never()).adjustRating(anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("R17: Sửa/xóa đánh giá không tồn tại hoặc đã xóa -> REVIEW_NOT_FOUND")
    void modify_NotFound() {
        when(reviewRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

        assertEquals(ErrorCode.REVIEW_NOT_FOUND,
                errorOf(() -> reviewService.updateReview(USER, 404L, UpdateReviewRequest.builder().rating(1).build())));
        assertEquals(ErrorCode.REVIEW_NOT_FOUND, errorOf(() -> reviewService.deleteReview(USER, 404L)));
        assertEquals(ErrorCode.REVIEW_NOT_FOUND, errorOf(() -> reviewService.setVisibility(404L, false)));
    }

    // ------------------------------ xóa ------------------------------

    @Test
    @DisplayName("R13: Xóa đánh giá đang hiển thị -> xóa mềm, tổng hợp -1 và -số sao")
    void delete_Visible_AdjustsAggregate() {
        ProductReview r = existing(4, ReviewStatus.VISIBLE);

        reviewService.deleteReview(USER, 100L);

        assertTrue(r.isDeleted());
        verify(productRepository).adjustRating(PRODUCT, -1, -4);
        verify(cacheService).evictAfterCommit(ProductService.DETAIL_CACHE_PREFIX + PRODUCT);
    }

    @Test
    @DisplayName("Xóa đánh giá đang bị ẩn -> không trừ tổng hợp lần nữa")
    void delete_Hidden_NoAdjust() {
        ProductReview r = existing(4, ReviewStatus.HIDDEN);

        reviewService.deleteReview(USER, 100L);

        assertTrue(r.isDeleted());
        verify(productRepository, never()).adjustRating(anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("R14/R15: Người khác (kể cả Admin) xóa đánh giá của người dùng -> REVIEW_ACCESS_DENIED")
    void delete_NotOwner_Throws() {
        ProductReview r = existing(4, ReviewStatus.VISIBLE);

        assertEquals(ErrorCode.REVIEW_ACCESS_DENIED, errorOf(() -> reviewService.deleteReview(2L, 100L)));
        assertFalse(r.isDeleted());
    }

    // ------------------------------ ẩn / hiện ------------------------------

    @Test
    @DisplayName("R18: Ẩn đánh giá đang hiển thị -> trừ khỏi tổng hợp")
    void hide_Visible() {
        existing(5, ReviewStatus.VISIBLE);

        ReviewResponse response = reviewService.setVisibility(100L, false);

        assertEquals("HIDDEN", response.getStatus());
        verify(productRepository).adjustRating(PRODUCT, -1, -5);
    }

    @Test
    @DisplayName("R19: Ẩn lại đánh giá đã ẩn hoặc hiện lại đánh giá đang hiển thị -> không đổi tổng hợp")
    void setVisibility_SameState_NoOp() {
        existing(5, ReviewStatus.HIDDEN);
        reviewService.setVisibility(100L, false);
        existing(5, ReviewStatus.VISIBLE);
        reviewService.setVisibility(100L, true);

        verify(productRepository, never()).adjustRating(anyLong(), anyInt(), anyInt());
        verify(cacheService, never()).evictAfterCommit(any());
    }

    @Test
    @DisplayName("R20: Hiện lại đánh giá đã ẩn -> cộng lại vào tổng hợp")
    void show_Hidden() {
        existing(3, ReviewStatus.HIDDEN);

        ReviewResponse response = reviewService.setVisibility(100L, true);

        assertEquals("VISIBLE", response.getStatus());
        verify(productRepository).adjustRating(PRODUCT, 1, 3);
    }

    // ------------------------------ truy vấn ------------------------------

    @Test
    @DisplayName("R22: Danh sách công khai lọc theo sao dùng truy vấn có rating; response không lộ orderId")
    void list_FilteredByRating() {
        ProductReview r = ProductReview.builder().productId(PRODUCT).userId(USER).orderId(ORDER).rating(4).build();
        r.setId(7L);
        when(reviewRepository.findByProductIdAndStatusAndRatingAndDeletedFalse(
                eq(PRODUCT), eq(ReviewStatus.VISIBLE), eq(4), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(r)));

        PageResponse<ReviewResponse> page = reviewService.getProductReviews(PRODUCT, 4, "ratingDesc", 0, 10);

        assertEquals(1, page.getContent().size());
        assertNull(page.getContent().get(0).getOrderId());
        assertEquals("Người dùng ẩn danh", page.getContent().get(0).getReviewerName());
    }

    @Test
    @DisplayName("R22: Không lọc sao dùng truy vấn theo trạng thái VISIBLE")
    void list_NoRatingFilter() {
        when(reviewRepository.findByProductIdAndStatusAndDeletedFalse(
                eq(PRODUCT), eq(ReviewStatus.VISIBLE), any(Pageable.class))).thenReturn(Page.empty());

        assertTrue(reviewService.getProductReviews(PRODUCT, null, null, 0, 10).getContent().isEmpty());
    }

    @Test
    @DisplayName("R23: size > 50 bị cắt về 50; sort lạ, rating ngoài 1-5, page âm, size < 1 -> INVALID_REQUEST")
    void list_ParameterValidation() {
        when(reviewRepository.findByProductIdAndStatusAndDeletedFalse(any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        reviewService.getProductReviews(PRODUCT, null, "newest", 0, 1000);
        org.mockito.ArgumentCaptor<Pageable> captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(reviewRepository).findByProductIdAndStatusAndDeletedFalse(any(), any(), captor.capture());
        assertEquals(50, captor.getValue().getPageSize());

        assertEquals(ErrorCode.INVALID_REQUEST, errorOf(() -> reviewService.getProductReviews(PRODUCT, null, "abc", 0, 10)));
        assertEquals(ErrorCode.INVALID_REQUEST, errorOf(() -> reviewService.getProductReviews(PRODUCT, 7, null, 0, 10)));
        assertEquals(ErrorCode.INVALID_REQUEST, errorOf(() -> reviewService.getProductReviews(PRODUCT, null, null, -1, 10)));
        assertEquals(ErrorCode.INVALID_REQUEST, errorOf(() -> reviewService.getProductReviews(PRODUCT, null, null, 0, 0)));
        assertEquals(ErrorCode.INVALID_REQUEST, errorOf(() -> reviewService.getMyReviews(USER, -1, 10)));
    }

    @Test
    @DisplayName("Danh sách/thống kê của sản phẩm không tồn tại -> PRODUCT_NOT_FOUND")
    void list_ProductNotFound() {
        when(productRepository.existsById(404L)).thenReturn(false);
        when(productRepository.findById(404L)).thenReturn(Optional.empty());

        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, errorOf(() -> reviewService.getProductReviews(404L, null, null, 0, 10)));
        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, errorOf(() -> reviewService.getSummary(404L)));
    }

    @Test
    @DisplayName("R24: Thống kê lấy trung bình từ tổng hợp, phân bố đủ 5 mức")
    void summary_Computed() {
        Product product = Product.builder().name("SP").status("ACTIVE").build();
        product.setId(PRODUCT);
        // 12 đánh giá: 0x1, 1x2, 1x3, 3x4, 7x5 -> tổng 2+3+12+35 = 52 -> 4.3
        org.springframework.test.util.ReflectionTestUtils.setField(product, "ratingCount", 12);
        org.springframework.test.util.ReflectionTestUtils.setField(product, "ratingSum", 52);
        when(productRepository.findById(PRODUCT)).thenReturn(Optional.of(product));
        when(reviewRepository.countVisibleByRating(PRODUCT)).thenReturn(List.of(
                new Object[]{2, 1L}, new Object[]{3, 1L}, new Object[]{4, 3L}, new Object[]{5, 7L}));

        ReviewSummaryResponse summary = reviewService.getSummary(PRODUCT);

        assertEquals(4.3, summary.getRatingAverage());
        assertEquals(12, summary.getRatingCount());
        assertEquals(List.of(1, 2, 3, 4, 5), List.copyOf(summary.getDistribution().keySet()));
        assertEquals(0L, summary.getDistribution().get(1));
        assertEquals(7L, summary.getDistribution().get(5));
    }

    @Test
    @DisplayName("R25: Sản phẩm chưa có đánh giá -> 0 và phân bố toàn 0")
    void summary_Empty() {
        when(reviewRepository.countVisibleByRating(PRODUCT)).thenReturn(List.of());

        ReviewSummaryResponse summary = reviewService.getSummary(PRODUCT);

        assertEquals(0.0, summary.getRatingAverage());
        assertEquals(0, summary.getRatingCount());
        assertTrue(summary.getDistribution().values().stream().allMatch(v -> v == 0L));
    }
}
