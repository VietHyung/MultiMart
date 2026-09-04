package com.multimart.common.response;

import lombok.*;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Lớp chuẩn hóa cấu trúc phân trang dữ liệu trả về cho client.
 * Giúp client dễ dàng xây dựng thanh điều hướng trang (Pagination Controls).
 *
 * @param <T> Kiểu đối tượng của từng phần tử trong danh sách
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageResponse<T> {

    private List<T> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean isLast;

    /**
     * Chuyển đổi từ đối tượng Page của Spring Data JPA sang đối tượng PageResponse chuẩn hóa.
     *
     * @param <T>  Kiểu dữ liệu của phần tử
     * @param page Đối tượng phân trang nhận được từ truy vấn Spring Data JPA
     * @return Đối tượng PageResponse chứa danh sách dữ liệu và metadata phân trang
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .isLast(page.isLast())
                .build();
    }
}
