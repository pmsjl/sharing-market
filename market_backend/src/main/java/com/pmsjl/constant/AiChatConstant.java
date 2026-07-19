package com.pmsjl.constant;

public interface AiChatConstant {
    int MAX_MESSAGE_LENGTH = 1000;
    int MAX_USAGE_SCENE_LENGTH = 300;
    int MAX_PREFERENCE_TAG_COUNT = 8;
    int MAX_PREFERENCE_TAG_LENGTH = 32;
    int MAX_AVOIDANCE_COUNT = 8;
    int MAX_AVOIDANCE_LENGTH = 64;
    int TITLE_LENGTH = 24;
    long PENDING_TIMEOUT_MILLIS = 60_000L;
    String PENDING_MESSAGE = "正在为你整理建议…";
    String FAILED_MESSAGE = "AI 服务暂不可用，请稍后重试。";


}
