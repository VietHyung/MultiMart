package com.multimart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Lớp khởi động chính của ứng dụng MultiMart E-Commerce & Flash-Sale Platform.
 */
@SpringBootApplication
public class MultiMartApplication {

    /**
     * Hàm main khởi chạy ứng dụng Spring Boot.
     *
     * @param args tham số dòng lệnh truyền vào khi khởi động
     */
    public static void main(String[] args) {
        SpringApplication.run(MultiMartApplication.class, args);
    }

}
