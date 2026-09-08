package com.pmsjl.common;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Redis 登录态 token 的 HTTP 请求头配置。
 */
@Component
@ConfigurationProperties(prefix = "auth-token")
@Data
public class AuthTokenProperties {

    /**
     * Token 所在的请求头。
     */
    private String header = "Authorization";

    /**
     * Token 前缀。
     */
    private String prefix = "Bearer";

    public AuthTokenProperties() {
    }

    public AuthTokenProperties(String header, String prefix) {
        this.header = header;
        this.prefix = prefix;
    }
}
