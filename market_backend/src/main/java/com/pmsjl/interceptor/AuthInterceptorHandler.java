package com.pmsjl.interceptor;

import com.alibaba.fastjson.JSONObject;

import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.common.JwtProperties;
import com.pmsjl.constant.CommonConstant;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.model.vo.LoginUserVO;
import com.pmsjl.utils.ThrowUtils;
import com.pmsjl.utils.UserHolder;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;


import java.io.PrintWriter;

@Component
public class AuthInterceptorHandler implements HandlerInterceptor {
    @Autowired
    private JwtProperties jwtProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        LoginUserVO loginUserVO = UserHolder.getUser();
        if(ObjectUtils.isNull(loginUserVO)||ObjectUtils.isEmpty(loginUserVO)){
            response.setStatus(401);
            return false;
        }
        return true;

    }
}

