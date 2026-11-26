package com.multimart.modules.review.entity;

/**
 * Trạng thái hiển thị của một đánh giá sản phẩm.
 */
public enum ReviewStatus {
    /** Đánh giá hiển thị công khai và được tính vào điểm trung bình. */
    VISIBLE,
    /** Đánh giá bị Admin ẩn, không hiển thị công khai và không tính vào điểm trung bình. */
    HIDDEN
}
