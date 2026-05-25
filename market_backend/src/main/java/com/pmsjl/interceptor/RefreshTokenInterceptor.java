package com.pmsjl.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.pmsjl.constant.CommonConstant;
import com.pmsjl.model.vo.LoginUserVO;
import com.pmsjl.utils.TokenUtils;
import com.pmsjl.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import static com.pmsjl.constant.RedisConstants.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class RefreshTokenInterceptor implements HandlerInterceptor {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;



    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();

    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ((CommonConstant.OPTIONS).equals(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return true;
        }
        // 1.获取请求头中的token
        String token = TokenUtils.getToken(request);
        // 无敌令牌命中，直接放行，仅测试使用
        if (CommonConstant.INVINCIBLE_TOKEN.equals(token)) {
            return true;
        }
        if (StrUtil.isBlank(token)) {
            return true;
        }
        // 2.基于TOKEN获取redis中的用户
        String key  = LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        // 3.判断用户是否存在
        if (userMap.isEmpty()) {
            return true;
        }
        // 5.将查询到的hash数据转为UserDTO
        LoginUserVO loginUserVO = BeanUtil.fillBeanWithMap(userMap, new LoginUserVO(), false);
        // 6.存在，保存用户信息到 ThreadLocal
        UserHolder.saveUser(loginUserVO);
        // 7.无论是什么路径均刷新token有效期
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 8.放行
        return true;
    }

}
