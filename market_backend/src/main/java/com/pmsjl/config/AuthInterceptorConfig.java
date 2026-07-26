package com.pmsjl.config;

import com.pmsjl.interceptor.AuthInterceptorHandler;
import com.pmsjl.interceptor.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AuthInterceptorConfig implements WebMvcConfigurer {

    // 1. 直接注入已经交给 Spring 管理的拦截器
    @Autowired
    private RefreshTokenInterceptor refreshTokenInterceptor;

    @Autowired
    private AuthInterceptorHandler authInterceptorHandler;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        // 2. 注册第一层拦截器（全局刷新 + 提取 Token 放入 ThreadLocal）
        registry.addInterceptor(refreshTokenInterceptor)
                .addPathPatterns("/**")  // 拦截所有请求
                .order(0);               // 优先级最高 (最先执行)

        // 3. 注册第二层拦截器（真正的登录校验）
        registry.addInterceptor(authInterceptorHandler)
                .addPathPatterns("/**")  // 默认拦截所有
                .excludePathPatterns(    // 排除不需要登录的接口
                        "/user/login",          // 注意：根据你真实的 Controller 路径写，别写 //login
                        "/user/register",
                        "/user/register_verify",
                        // Python Agent 调用的内部工具接口，
                        // 不使用普通用户登录态，由 X-Internal-Token 自行鉴权
                        "/internal/ai/tools/**",
                        // Knife4j / Swagger 接口文档相关路径必须全部放行
                        "/doc.html",
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**"
                )
                .order(1);               // 优先级排第二
    }
}