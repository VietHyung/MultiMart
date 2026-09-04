package com.multimart.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Cấu hình kích hoạt cơ chế JPA Auditing tự động.
 * Cho phép các annotation @CreatedDate và @LastModifiedDate trong BaseEntity
 * tự động điền thời gian tạo và cập nhật mỗi khi thực thể được lưu xuống cơ sở dữ liệu.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
