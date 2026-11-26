package com.multimart.modules.review.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Tiện ích tính điểm đánh giá trung bình từ số liệu tổng hợp.
 */
public final class RatingCalculator {

    private RatingCalculator() {
    }

    /**
     * Tính điểm trung bình, làm tròn HALF_UP đến 1 chữ số thập phân.
     *
     * @param sum   tổng số sao
     * @param count số đánh giá
     * @return điểm trung bình, bằng 0 khi chưa có đánh giá
     */
    public static double average(int sum, int count) {
        if (count <= 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP).doubleValue();
    }
}
