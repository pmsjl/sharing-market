package com.pmsjl.common;


import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Data
public class JwtProperties {
    //这个类的作用就是在存储jwt令牌生成时所需要的两个自定义值
    //一个是加密算法需要的自定义字符串secret
    //一个是超时时间的设置
    //这里的值读取的是yml文件，而不是硬编码
    /**
     * JWT存储的请求头
     */
    @Value("${jwt.tokenHeader}")
    private String tokenHeader;

    /**
     * JWT的超时时间
     */
    @Value("${jwt.expiration}")
    private long expiration;

    public JwtProperties() {
    }

    /**
     * JWT负载中拿到的开头
     */
    @Value("${jwt.tokenHead}")
    private String tokenHead;

    public JwtProperties(String tokenHeader, String secret, long expiration, String tokenHead) {
        this.tokenHeader = tokenHeader;
        this.expiration = expiration;
        this.tokenHead = tokenHead;
    }
}
