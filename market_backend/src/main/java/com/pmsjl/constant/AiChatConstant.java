package com.pmsjl.constant;

public interface AiChatConstant {
    int MAX_MESSAGE_LENGTH = 1000;
    int MAX_USAGE_SCENE_LENGTH = 300;
    int MAX_PREFERENCE_TAG_COUNT = 8;
    int MAX_PREFERENCE_TAG_LENGTH = 32;
    int MAX_AVOIDANCE_COUNT = 8;
    int MAX_AVOIDANCE_LENGTH = 64;
    int TITLE_LENGTH = 24;
    String PENDING_MESSAGE = "正在为你整理建议…";
    String FAILED_MESSAGE = "AI 服务暂不可用，请稍后重试。";
    String PENDING_TIMEOUT_MESSAGE = "上一条咨询等待超时，请重新发送。";
    String PENDING_TIMEOUT_ERROR_KEY = "AI_AGENT_PENDING_TIMEOUT";


}
