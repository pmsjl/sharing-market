package com.pmsjl.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/***
 * jwt令牌生成
 */
@Component
public class JwtKit {

    private final JwtProperties jwtProperties;

    @Autowired
    public JwtKit(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * 生成 Token（使用和解析完全一致的密钥处理方式）
     */
    public <T> String generateToken(T user) {
        Map<String, Object> claims = new HashMap<>(10);
        claims.put("username", user.toString());
        claims.put("createdate", new Date());
        claims.put("time", System.currentTimeMillis());

        // 统一处理 Base64 secret
        byte[] keyBytes = java.util.Base64.getDecoder().decode(jwtProperties.getSecret());
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);

        return Jwts.builder()
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getExpiration()))
                .signWith(key)                 // 使用 SecretKey
                .compact();
    }

    /**
     * 解析 Token
     */
    public Claims parseJwtToken(String token) {
        try {
            byte[] keyBytes = java.util.Base64.getDecoder().decode(jwtProperties.getSecret());
            SecretKey key = Keys.hmacShaKeyFor(keyBytes);

            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

        } catch (Exception e) {
            System.out.println("JWT 解析失败: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}