package com.pmsjl.utils;

import com.pmsjl.common.JwtProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Token 解析工具类
 */
@Component // 必须加 @Component，让 Spring 来初始化它
public class TokenUtils {

    // 注意：这里是 static 变量，不能直接加 @Autowired
    private static JwtProperties jwtProperties;

    // 核心魔法：Spring 初始化这个类时，会自动调用这个普通方法，把 Bean 塞给静态变量
    @Autowired
    public void setJwtProperties(JwtProperties jwtProperties) {
        TokenUtils.jwtProperties = jwtProperties;
    }

    /**
     * 从请求头中安全获取真正的 Token
     */
    public static String getToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        // 1. 获取请求头 (例如：Authorization)
        String header = request.getHeader(jwtProperties.getTokenHeader());

        // 2. 判空
        if (StringUtils.isBlank(header)) {
            return null;
        }

        // 3. 校验前缀并截取 (例如：Bearer )
        String prefix = jwtProperties.getTokenHead();
        if (header.startsWith(prefix)) {
            // 用 substring 截掉前缀，并用 trim() 去掉两端多余的空格，比 +1 更稳妥！
            return header.substring(prefix.length()).trim();
        }

        return null;
    }
}