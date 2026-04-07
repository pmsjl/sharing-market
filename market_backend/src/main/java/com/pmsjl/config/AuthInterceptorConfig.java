package com.pmsjl.config;

import com.pmsjl.aop.AuthInterceptorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 */
@Configuration
public class AuthInterceptorConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptorHandler()).addPathPatterns("/**").excludePathPatterns("/**/login","/**/register","/**/register_verify").excludePathPatterns("/doc.html");
    }

    //对intercepter进行路径定义导入
    @Bean
    public AuthInterceptorHandler authInterceptorHandler(){
        return new AuthInterceptorHandler();
    }
}
