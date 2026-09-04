package com.multimart.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

/**
 * Thành phần chịu trách nhiệm tạo, ký số, giải mã và xác thực JSON Web Token (JWT).
 * Sử dụng thuật toán HMAC-SHA256 với thư viện JJWT 0.12.x.
 */
@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiration-ms}")
    private long accessTokenExpirationMs;

    @Value("${jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    private SecretKey key;

    /**
     * Khởi tạo khóa ký bí mật (SecretKey) từ chuỗi cấu hình sau khi Bean được tạo.
     */
    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Sinh JWT Access Token có thời hạn ngắn (15 phút) phục vụ các request cần xác thực.
     * Đính kèm thông tin email (subject) và danh sách quyền hạn (roles).
     *
     * @param userDetails thông tin người dùng được Spring Security quản lý
     * @return Chuỗi JWT Access Token đã ký số
     */
    public String generateAccessToken(UserDetails userDetails) {
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpirationMs))
                .signWith(key)
                .compact();
    }

    /**
     * Sinh JWT Refresh Token có thời hạn dài (7 ngày) dùng để cấp lại Access Token mới
     * mà không bắt người dùng phải nhập lại mật khẩu.
     *
     * @param userDetails thông tin người dùng
     * @return Chuỗi JWT Refresh Token đã ký số
     */
    public String generateRefreshToken(UserDetails userDetails) {
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpirationMs))
                .signWith(key)
                .compact();
    }

    /**
     * Trích xuất username (trong hệ thống này là email) từ trường 'sub' của Token.
     *
     * @param token chuỗi JWT
     * @return email định danh của người dùng
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Trích xuất một Claim cụ thể từ Payload của Token thông qua một hàm resolver.
     *
     * @param <T> Kiểu dữ liệu của Claim cần lấy
     * @param token chuỗi JWT
     * @param claimsResolver hàm xử lý trích xuất giá trị mong muốn
     * @return Giá trị của Claim
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Kiểm tra tính hợp lệ của Token: Chữ ký số có khớp với SecretKey không và còn hạn sử dụng không.
     *
     * @param token chuỗi JWT cần kiểm tra
     * @return true nếu token hoàn toàn hợp lệ, false nếu bị sửa đổi hoặc đã hết hạn
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Giải mã toàn bộ Claims từ phần Payload của Token sau khi xác minh chữ ký hợp lệ.
     *
     * @param token chuỗi JWT
     * @return Đối tượng Claims chứa mọi dữ liệu của payload
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
