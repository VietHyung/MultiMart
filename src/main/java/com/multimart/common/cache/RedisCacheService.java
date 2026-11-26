package com.multimart.common.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Dịch vụ cache-aside đơn giản trên Redis dùng cho dữ liệu chỉ phục vụ hiển thị (SA ADR-7).
 * <p>
 * Đặc điểm:
 * <ul>
 *   <li>Chống Cache Stampede bằng khóa theo từng nhóm key (striped lock): chỉ một luồng nạp lại dữ liệu.</li>
 *   <li>Chống Cache Penetration bằng cách cache giá trị rỗng (null) trong thời gian ngắn.</li>
 *   <li>Redis lỗi/sập: ghi log cảnh báo và đọc thẳng từ nguồn gốc (DB), API không bị lỗi.</li>
 * </ul>
 * Lưu ý: tuyệt đối không dùng cache cho dữ liệu tiền (giá, tồn kho khi đặt hàng).
 */
@Slf4j
@Component
public class RedisCacheService {

    /** Giá trị đánh dấu "không tồn tại" được cache để chống Cache Penetration. */
    private static final String NULL_MARKER = "__NULL__";

    /** Thời gian cache giá trị rỗng. */
    private static final Duration NULL_TTL = Duration.ofSeconds(60);

    /** Số lượng khóa dùng chung (striped lock) để tránh phình bộ nhớ theo số key. */
    private static final int LOCK_STRIPES = 64;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final Object[] locks = new Object[LOCK_STRIPES];

    /**
     * Khởi tạo dịch vụ cache.
     *
     * @param stringRedisTemplate template thao tác chuỗi trên Redis
     * @param objectMapper        ObjectMapper hỗ trợ Java Time để (de)serialize DTO
     */
    public RedisCacheService(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        for (int i = 0; i < LOCK_STRIPES; i++) {
            locks[i] = new Object();
        }
    }

    /**
     * Lấy dữ liệu từ cache; nếu chưa có thì gọi loader nạp từ nguồn gốc rồi lưu lại.
     *
     * @param key    khóa cache (đã gồm tiền tố)
     * @param ttl    thời gian sống của cache
     * @param type   kiểu dữ liệu để deserialize
     * @param loader hàm nạp dữ liệu từ nguồn gốc, có thể trả về null nếu không tồn tại
     * @param <T>    kiểu dữ liệu
     * @return dữ liệu (có thể null nếu nguồn gốc không có)
     */
    public <T> T getOrLoad(String key, Duration ttl, TypeReference<T> type, Supplier<T> loader) {
        CacheLookup<T> first = lookup(key, type);
        if (first.hit()) {
            return first.value();
        }
        synchronized (locks[Math.floorMod(key.hashCode(), LOCK_STRIPES)]) {
            // Kiểm tra lại sau khi giành khóa: luồng khác có thể đã nạp xong
            CacheLookup<T> second = lookup(key, type);
            if (second.hit()) {
                return second.value();
            }
            T loaded = loader.get();
            store(key, loaded, ttl);
            return loaded;
        }
    }

    /**
     * Xóa một hoặc nhiều key khỏi cache. Lỗi Redis chỉ ghi log, không ném ngoại lệ.
     *
     * @param keys các khóa cần xóa
     */
    public void evict(String... keys) {
        try {
            for (String key : keys) {
                stringRedisTemplate.delete(key);
            }
        } catch (RuntimeException e) {
            log.warn("Redis evict failed (cache may stay stale until TTL): {}", e.getMessage());
        }
    }

    /**
     * Xóa cache sau khi giao dịch DB hiện tại commit thành công (tránh luồng khác nạp lại dữ liệu cũ
     * trước khi commit). Nếu không có giao dịch đang chạy thì xóa ngay.
     *
     * @param keys các khóa cần xóa
     */
    public void evictAfterCommit(String... keys) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(keys);
                }
            });
        } else {
            evict(keys);
        }
    }

    /** Đọc cache; mọi lỗi Redis/JSON được coi là cache miss. */
    private <T> CacheLookup<T> lookup(String key, TypeReference<T> type) {
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null) {
                return CacheLookup.miss();
            }
            if (NULL_MARKER.equals(json)) {
                return CacheLookup.of(null);
            }
            return CacheLookup.of(objectMapper.readValue(json, type));
        } catch (Exception e) {
            log.warn("Cache read failed for key={}, fallback to source: {}", key, e.getMessage());
            return CacheLookup.miss();
        }
    }

    /** Ghi cache; mọi lỗi được bỏ qua để không ảnh hưởng luồng chính. */
    private void store(String key, Object value, Duration ttl) {
        try {
            if (value == null) {
                stringRedisTemplate.opsForValue().set(key, NULL_MARKER, NULL_TTL);
            } else {
                stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
            }
        } catch (Exception e) {
            log.warn("Cache write failed for key={}: {}", key, e.getMessage());
        }
    }

    /** Kết quả tra cứu cache: phân biệt "miss" với "hit giá trị null". */
    private record CacheLookup<T>(boolean hit, T value) {
        static <T> CacheLookup<T> miss() {
            return new CacheLookup<>(false, null);
        }

        static <T> CacheLookup<T> of(T value) {
            return new CacheLookup<>(true, value);
        }
    }
}
