package com.multimart.modules.review.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Kiểm thử tính điểm trung bình (SRS-REVIEW-001: BR-R8, R24, R25).
 */
class RatingCalculatorTest {

    @Test
    @DisplayName("R25: Chưa có đánh giá thì điểm trung bình bằng 0")
    void noReviews_ReturnsZero() {
        assertEquals(0.0, RatingCalculator.average(0, 0));
    }

    @Test
    @DisplayName("R24: Làm tròn HALF_UP đến 1 chữ số thập phân")
    void roundsHalfUp() {
        assertEquals(4.3, RatingCalculator.average(13, 3));
        assertEquals(4.5, RatingCalculator.average(9, 2));
        assertEquals(1.8, RatingCalculator.average(7, 4));
        assertEquals(0.6, RatingCalculator.average(5, 8));
        assertEquals(5.0, RatingCalculator.average(25, 5));
    }
}
