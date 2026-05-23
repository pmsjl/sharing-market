package com.pmsjl.utils;

import com.pmsjl.model.vo.LoginUserVO;

public class UserHolder {
    private static final ThreadLocal<LoginUserVO> tl = new ThreadLocal<>();

    public static void saveUser(LoginUserVO user){
        tl.set(user);
    }

    public static LoginUserVO getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}
