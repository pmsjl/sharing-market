package com.pmsjl.common;

public enum ErrorCode {

    SUCCESS(0, "ok"),
    PARAMS_ERROR(40000, "请求参数错误"),
    NOT_LOGIN_ERROR(40100, "未登录"),
    NO_AUTH_ERROR(40101, "无权限"),
    NOT_FOUND_ERROR(40400, "请求数据不存在"),
    FORBIDDEN_ERROR(40300, "禁止访问"),
    SYSTEM_ERROR(50000, "系统内部异常"),
    OPERATION_ERROR(50001, "操作失败"),
    WORD_FORBIDDEN_ERROR(422200, "包含违禁词，多次违禁将封禁账号"),
    CONFLICT_ERROR(40900, "请求冲突"),
    AI_USER_DAILY_QUOTA_EXCEEDED(42901, "你今日的 AI 咨询额度已用完"),
    AI_GLOBAL_DAILY_QUOTA_EXCEEDED(42902, "今日平台 AI 体验额度已用完");
    /**
     * 状态码
     */
    private final int code;

    /**
     * 信息
     */
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

}

