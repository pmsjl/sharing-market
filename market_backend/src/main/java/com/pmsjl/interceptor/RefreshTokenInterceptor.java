package com.pmsjl.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import com.pmsjl.common.JwtProperties;
import com.pmsjl.constant.CommonConstant;
import com.pmsjl.model.vo.LoginUserVO;
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
    @Autowired
    private JwtProperties jwtProperties;



    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.获取请求头中的token
        String token = request.getHeader(jwtProperties.getTokenHeader());
        // 无敌令牌命中，直接放行，仅测试使用
        if (CommonConstant.INVINCIBLE_TOKEN.equals(token)) {
            return true;
        }
        // 截取中间payload部分 +1是Bearer + 空格(1)
        // 创建json对象
        JSONObject jsonObject = new JSONObject();
        if (token != null) {
             token= token.substring(jwtProperties.getTokenHead().length() + 1);
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
