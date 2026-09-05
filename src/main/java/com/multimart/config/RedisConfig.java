package com.multimart.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Cấu hình hạ tầng Redis cho hệ thống MultiMart.
 * Cung cấp RedisTemplate, StringRedisTemplate và nạp Redis Lua Script thực thi nguyên tử.
 */
@Configuration
public class RedisConfig {

    /**
     * Khởi tạo RedisTemplate tùy biến hỗ trợ Serialization chuẩn JSON.
     *
     * @param connectionFactory Nhà máy tạo kết nối Redis
     * @return RedisTemplate với cấu hình Key dạng String và Value dạng JSON
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();

        // Key và Hash Key sử dụng chuỗi String thuần túy
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value và Hash Value chuyển đổi qua lại dạng JSON
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * Khởi tạo StringRedisTemplate phục vụ các thao tác xử lý chuỗi và số nguyên tốc độ cao.
     *
     * @param connectionFactory Nhà máy tạo kết nối Redis
     * @return StringRedisTemplate
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * Nạp mã Redis Lua Script để thực hiện thao tác kiểm tra và trừ tồn kho nguyên tử (Atomic).
     *
     * @return Đối tượng DefaultRedisScript chứa mã Lua script và kiểu trả về Long
     */
    @Bean
    public DefaultRedisScript<Long> flashSaleStockDeductScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/flash_sale_stock_deduct.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
