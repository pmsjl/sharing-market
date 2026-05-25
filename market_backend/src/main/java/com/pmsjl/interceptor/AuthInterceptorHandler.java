package com.pmsjl.interceptor;

import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.pmsjl.common.JwtProperties;
import com.pmsjl.model.vo.LoginUserVO;
import com.pmsjl.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;




@Component
public class AuthInterceptorHandler implements HandlerInterceptor {


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        LoginUserVO loginUserVO = UserHolder.getUser();
        if(ObjectUtils.isNull(loginUserVO)||ObjectUtils.isEmpty(loginUserVO)){
            response.setStatus(401);
            return false;
        }
        return true;

    }
}

