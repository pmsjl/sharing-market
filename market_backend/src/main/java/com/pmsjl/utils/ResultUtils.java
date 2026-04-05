package com.pmsjl.utils;


import com.pmsjl.common.ErrorCode;
import com.pmsjl.common.Result;

import java.util.HashMap;

public class ResultUtils {
    //因为在我们的result中只初始化了构造方法，我们需要返回各种情况的result，
    //所以将这各种方法进行封装

    /**
     * 成功
     *
     * @param data
     * @param <T>
     * @returns
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, data, "success");
    }

    /**
     * 失败
     *
     * @param errorCode
     * @return
     */
    public static Result error(ErrorCode errorCode) {
        return new Result<>(errorCode);
    }

    /**
     * 失败
     *
     * @param code
     * @param message
     * @return
     */
    public static Result error(int code, String message) {
        return new Result<>(code, null, message);
    }

    /**
     * 失败
     *
     * @param errorCode
     * @return
     */
    public static Result error(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), null, message);
    }

    /**
     * 成功并添加动态参数
     *
     * @param data
     * @param <T>
     * @return
     */
    public static <T> Result<T> successDynamic(T data, HashMap<String, Object> hashMap) {
        return new Result<>(200, data, "sucess", hashMap);
    }
}