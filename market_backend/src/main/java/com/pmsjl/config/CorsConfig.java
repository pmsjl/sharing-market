package com.pmsjl.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * 全局跨域配置
 *
 * @author 程序员小白条
 * @from <a href="https://luoye6.github.io/"> 个人博客
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOriginPatterns;

    public CorsConfig(
            @Value("${app.cors.allowed-origin-patterns}")
            String configuredOriginPatterns
    ) {
        this.allowedOriginPatterns = Arrays.stream(configuredOriginPatterns.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
        if (allowedOriginPatterns.length == 0) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGIN_PATTERNS 至少需要配置一个来源");
        }
        if (Arrays.asList(allowedOriginPatterns).contains("*")) {
            throw new IllegalStateException("生产 CORS 不允许在携带凭据时使用全局通配符 *");
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowCredentials(true)
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization", "Content-Disposition", "X-Request-Id")
                .maxAge(3600);
    }
}
