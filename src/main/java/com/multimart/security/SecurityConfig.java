package com.multimart.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Lớp cấu hình bảo mật chính của toàn hệ thống (Spring Security 6).
 * Thiết lập cơ chế Stateless Session, mã hóa BCrypt, phân quyền endpoint và chuỗi bộ lọc JWT.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;

    /**
     * Cung cấp Bean mã hóa mật khẩu theo chuẩn thuật toán BCrypt với độ muối (salt) tự sinh an toàn.
     *
     * @return đối tượng PasswordEncoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Cung cấp Bean AuthenticationManager dùng để thực hiện xác thực thông tin đăng nhập trong AuthService.
     *
     * @param configuration cấu hình xác thực từ Spring Security
     * @return đối tượng AuthenticationManager
     * @throws Exception khi không khởi tạo được AuthenticationManager
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * Cấu hình chuỗi lọc bảo mật SecurityFilterChain:
     * - Tắt CSRF (vì kiến trúc REST API Stateless dùng Token).
     * - Kích hoạt CORS.
     * - Không lưu trạng thái phiên đăng nhập vào Session trên RAM server (SessionCreationPolicy.STATELESS).
     * - Phân định rõ các URL công khai (Auth, GET Products, GET FlashSale) và URL yêu cầu đăng nhập.
     * - Chèn JwtAuthenticationFilter vào trước UsernamePasswordAuthenticationFilter.
     *
     * @param http đối tượng HttpSecurity để tùy biến bảo mật
     * @return đối tượng SecurityFilterChain đã hoàn thiện
     * @throws Exception lỗi cấu hình bảo mật
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(customAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        // Cho phép tài nguyên tĩnh Frontend và trang chủ truy cập công khai
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/favicon.ico",
                                "/css/**",
                                "/js/**",
                                "/images/**"
                        ).permitAll()
                        // Chỉ cho phép đăng ký, đăng nhập và h2-console công khai
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/h2-console/**"
                        ).permitAll()
                        // Cho phép xem danh sách và chi tiết sản phẩm công khai
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/products/**").permitAll()
                        // Cho phép xem các đợt Flash-Sale công khai
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/flash-sales/**").permitAll()
                        // Các endpoint còn lại bắt buộc phải có JWT
                        .anyRequest().authenticated()
                )
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Cấu hình CORS toàn cục cho phép Frontend kết nối mượt mà tới các REST API.
     *
     * @return đối tượng CorsConfigurationSource
     */
    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();
        configuration.setAllowedOriginPatterns(java.util.List.of("*"));
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(java.util.List.of("*"));
        configuration.setAllowCredentials(true);
        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
