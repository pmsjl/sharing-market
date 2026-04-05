package com.pmsjl.common;

import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;

@Data
public class Result<T> implements Serializable {

    private int code;

    private T data;

    private String message;

    private HashMap<String,Object> hashMap = new HashMap<>();



    public Result(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public Result(int code, T data) {
        this(code, data, "");
    }

    public Result(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }

    public Result(int code, T data, String message, HashMap<String,Object> hashMap) {
        this.code = code;
        this.data = data;
        this.message = message;
        this.hashMap = hashMap;
    }
}

