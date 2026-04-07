package com.pmsjl.aop;

import com.alibaba.fastjson.JSONObject;

import com.pmsjl.common.JwtKit;
import com.pmsjl.common.JwtProperties;
import com.pmsjl.constant.CommonConstant;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;


import java.io.PrintWriter;

/**
 * **前端发送请求：**
 * ```
 * GET /api/user/list
 * Headers:
 *     Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.xxxx.yyyy
 * ```

 *
 * ```java
 * @Override
 * public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
 *
 *     // ======================== 正式进入 Token 校验流程 ========================
 *
 *     // 第①步：从请求头中获取完整的 Token
 *     // 前端传来的值是： "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.xxxx.yyyy"
 *     String jwtToken = request.getHeader(jwtProperties.getTokenHeader());
 *     // 假设 jwtProperties.getTokenHeader() = "Authorization"
 *     // 所以 jwtToken = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.xxxx.yyyy"
 *
 *     // 第②步：判断是否是“无敌令牌”（仅用于测试）
 *     if (CommonConstant.INVINCIBLE_TOKEN.equals(jwtToken)) {
 *         return true;        // 测试时直接跳过所有校验
 *     }
 *
 *     // 第③步：准备解析用的变量
 *     String payloadToken = null;           // 真正要解析的 Token
 *     JSONObject jsonObject = new JSONObject();   // 用于返回错误信息
 *
 *     // 第④步：截取真正的 Token（去掉 "Bearer " 前缀）
 *     if (jwtToken != null) {
 *         // jwtProperties.getTokenHead() 一般配置为 "Bearer"
 *         // length() + 1 是因为要跳过 "Bearer" 后面的空格
 *         payloadToken = jwtToken.substring(jwtProperties.getTokenHead().length() + 1);
 *         // 这儿输入的是起始索引，执行后：payloadToken = "<jwt-placeholder>"
 *     }
 *
 *     // 第⑤步：判断 Token 是否存在且不为空
 *     if (payloadToken != null && !StringUtils.isBlank(payloadToken)) {
 *
 *         Claims claims = null;
 *         try {
 *             // 第⑥步：调用 JwtKit 解析 Token
 *             claims = jwtKit.parseJwtToken(payloadToken);
 *             // 如果解析成功，claims 里面会包含用户id、用户名、角色、过期时间等信息
 *         }
 *         catch (Exception e) {
 *             // 第⑦步：解析失败（Token过期、签名错误、格式错误等都会进入这里）
 *             jsonObject.put("status", 401);
 *             jsonObject.put("msg", "登录过期,请重新登录");
 *             renderJson(response, jsonObject.toJSONString());
 *             return false;           // 拦截请求，不再往下走
 *         }
 *
 *         // 第⑧步：解析成功
 *         return claims != null;      // 返回 true，放行请求，进入 Controller
 *     }
 *
 *     // ======================== Token 不存在的情况 ========================
 *
 *     // 第⑨步：完全没有传 Token 或 Token 为空
 *     jsonObject.put("status", 401);
 *     jsonObject.put("msg", "登录非法，无有效全局 Token");
 *     renderJson(response, jsonObject.toJSONString());
 *
 *     return false;   // 拦截请求
 * }
 * ```
 *
 * ---
 */
@Component
public class AuthInterceptorHandler implements HandlerInterceptor {
    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private JwtKit jwtKit;

    /**
     * 前置拦截器
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getRequestURI().equals("/api/") && "GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        // 放行 /api/sse 路径 SSE 的路径
        if (request.getRequestURI().startsWith("/api/sse/")) {
            return true; // 直接放行
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        //判断如果请求的类是swagger的控制器，直接通行。
        if (("springfox.documentation.swagger.web.ApiResourceController").equals(handlerMethod.getBean().getClass().getName())) {
            return true;
        }

        if ((CommonConstant.OPTIONS).equals(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return true;
        }
        // 获取到JWT的Token
        String jwtToken = request.getHeader(jwtProperties.getTokenHeader());
        // 无敌令牌命中，直接放行，仅测试使用
        if (CommonConstant.INVINCIBLE_TOKEN.equals(jwtToken)) {
            return true;
        }
        // 截取中间payload部分 +1是Bearer + 空格(1)
        String payloadToken = null;
        // 创建json对象
        JSONObject jsonObject = new JSONObject();

        if (jwtToken != null) {
            payloadToken = jwtToken.substring(jwtProperties.getTokenHead().length() + 1);
        }
        if (payloadToken != null && (!StringUtils.isBlank(payloadToken))) {

            // 解析Token，获取Claims = Map
            Claims claims = null;
            try {
                claims = jwtKit.parseJwtToken(payloadToken);
            } catch (Exception e) {
                //token过期会捕捉到异常
                jsonObject.put("status", 401);
                jsonObject.put("msg", "登录过期,请重新登录");
                String json1 = jsonObject.toJSONString();
                renderJson(response, json1);
            }
            return claims != null;
            // 获取payload中的报文，
        }
        // 如果token不存在
        jsonObject.put("status", 401);
        jsonObject.put("msg", "登录非法，无有效全局 Token");
        String json2 = jsonObject.toJSONString();
        renderJson(response, json2);

        return false;
    }

    private void renderJson(HttpServletResponse response, String json) {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        try (PrintWriter printWriter = response.getWriter()) {
            printWriter.print(json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
