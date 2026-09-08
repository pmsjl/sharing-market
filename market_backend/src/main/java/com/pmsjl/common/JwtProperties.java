package com.pmsjl.common;


import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Data
public class JwtProperties {
    /**
     * Token 所在的请求头
     */
    @Value("${jwt.tokenHeader}")
    private String tokenHeader;

    public JwtProperties() {
    }

    /**
     * Token 前缀
     */
    @Value("${jwt.tokenHead}")
    private String tokenHead;

    public JwtProperties(String tokenHeader, String tokenHead) {
        this.tokenHeader = tokenHeader;
        this.tokenHead = tokenHead;
    }
}
