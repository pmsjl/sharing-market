package com.pmsjl.service.Impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.manager.AiAgentClient;
import com.pmsjl.manager.AiAgentClientException;
import com.pmsjl.mapper.AiConversationMapper;
import com.pmsjl.mapper.AiMessageMapper;
import com.pmsjl.model.dto.ai.AiChatMessageRequest;
import com.pmsjl.model.dto.ai.AiShoppingContext;
import com.pmsjl.model.dto.ai.internal.AgentRunRequest;
import com.pmsjl.model.dto.ai.internal.AgentRunResponse;
import com.pmsjl.model.entity.AiConversation;
import com.pmsjl.model.entity.AiMessage;
import com.pmsjl.model.entity.User;
import com.pmsjl.model.enums.AiConversationSceneEnum;
import com.pmsjl.model.enums.AiConversationStatusEnum;
import com.pmsjl.model.enums.AiMessageRoleEnum;
import com.pmsjl.model.enums.AiMessageStatusEnum;
import com.pmsjl.model.vo.AiChatVO;
import com.pmsjl.model.vo.AiConversationVO;
import com.pmsjl.model.vo.AiMessageVO;
import com.pmsjl.model.vo.AiStructuredContentVO;
import com.pmsjl.service.AiChatService;
import com.pmsjl.service.UserService;
import com.pmsjl.utils.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.pmsjl.constant.AiChatConstant.*;

/** AI 首条消息的会话创建、Python 调用与消息结果回写实现。 */
@Service
public class AiChatServiceImpl implements AiChatService {


    @Autowired
    private UserService userService;

    @Autowired
    private AiConversationMapper aiConversationMapper;

    @Autowired
    private AiMessageMapper aiMessageMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private AiAgentClient aiAgentClient;

    /**
     * 先在短事务中写入会话、USER 和 PENDING ASSISTANT 消息；事务提交后才调用 Python。
     * 网络请求完成后，再使用另一段短事务把同一条 ASSISTANT 消息更新成 SUCCESS 或 FAILED。
     */
    @Override
    public AiChatVO createConversation(AiChatMessageRequest aiChatMessageRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(aiChatMessageRequest == null, ErrorCode.PARAMS_ERROR);

        String content = StringUtils.trimToEmpty(aiChatMessageRequest.getContent());
        ThrowUtils.throwIf(StringUtils.isBlank(content), ErrorCode.PARAMS_ERROR, "咨询内容不能为空");
        ThrowUtils.throwIf(content.length() > MAX_MESSAGE_LENGTH, ErrorCode.PARAMS_ERROR,
                "咨询内容不能超过 " + MAX_MESSAGE_LENGTH + " 个字符");

        AiShoppingContext shoppingContext = aiChatMessageRequest.getShoppingContext();
        validateShoppingContext(shoppingContext);
        User loginUser = userService.getLoginUser(request);
        String requestId = UUID.randomUUID().toString();

        PendingChat pendingChat = transactionTemplate.execute(status -> persistPendingChat(
                loginUser, content, shoppingContext, requestId));
        ThrowUtils.throwIf(pendingChat == null, ErrorCode.OPERATION_ERROR, "创建 AI 会话失败");

        AgentRunRequest agentRunRequest = buildAgentRunRequest(pendingChat);
        try {
            AgentRunResponse agentRunResponse = aiAgentClient.runAgent(requestId, agentRunRequest);
            return persistAgentSuccess(pendingChat, agentRunResponse);
        } catch (AiAgentClientException e) {
            return persistAgentFailure(pendingChat, e);
        }
    }

    /** 第一段数据库事务：保证三条初始记录要么全部存在，要么全部回滚。 */
    private PendingChat persistPendingChat(User loginUser, String content, AiShoppingContext shoppingContext,
                                            String requestId) {
        Date now = new Date();
        AiConversation conversation = getAiConversation(loginUser, content, shoppingContext, now);
        AiMessage userMessage = getUserMessage(conversation, loginUser, content, requestId, now);
        AiMessage assistantMessage = getAssistantMessage(conversation, loginUser, requestId, now);
        return new PendingChat(requestId, loginUser, shoppingContext, conversation, userMessage, assistantMessage);
    }

    /** 将 Java 已鉴权的安全上下文组装成 Python 的内部请求，不传浏览器身份或数据库连接信息。 */
    private AgentRunRequest buildAgentRunRequest(PendingChat pendingChat) {
        AgentRunRequest agentRunRequest = new AgentRunRequest();
        agentRunRequest.setUserId(pendingChat.loginUser().getId());
        agentRunRequest.setConversationId(pendingChat.conversation().getId());
        agentRunRequest.setMessage(pendingChat.userMessage().getContent());
        agentRunRequest.setShoppingContext(pendingChat.shoppingContext());
        agentRunRequest.setMemorySummary(pendingChat.conversation().getMemorySummary());
        return agentRunRequest;
    }

    /** 第二段数据库事务：成功后只更新先前创建的那一条助手消息。 */
    private AiChatVO persistAgentSuccess(PendingChat pendingChat, AgentRunResponse agentRunResponse) {
        return transactionTemplate.execute(status -> {
            Date now = new Date();
            AiMessage assistantMessage = pendingChat.assistantMessage();
            assistantMessage.setContent(agentRunResponse.getAnswer().trim());
            assistantMessage.setStructuredContent(serializeObject(agentRunResponse.getOutput(), "AI 结构化结果"));
            assistantMessage.setModelName(agentRunResponse.getModel() == null ? null : agentRunResponse.getModel().getName());
            assistantMessage.setInputTokens(agentRunResponse.getUsage() == null ? null : agentRunResponse.getUsage().getInputTokens());
            assistantMessage.setOutputTokens(agentRunResponse.getUsage() == null ? null : agentRunResponse.getUsage().getOutputTokens());
            assistantMessage.setLatencyMs(agentRunResponse.getLatencyMs());
            assistantMessage.setStatus(AiMessageStatusEnum.SUCCESS.getValue());
            assistantMessage.setErrorCode(null);
            assistantMessage.setRetryable(false);
            assistantMessage.setUpdateTime(now);
            ThrowUtils.throwIf(aiMessageMapper.updateById(assistantMessage) != 1, ErrorCode.OPERATION_ERROR,
                    "更新 AI 回复失败");

            AiConversation conversation = pendingChat.conversation();
            conversation.setLastMessagePreview(buildPreview(assistantMessage.getContent()));
            conversation.setLastMessageTime(now);
            conversation.setUpdateTime(now);
            if (agentRunResponse.getOutput() != null) {
                conversation.setMemorySummary(agentRunResponse.getOutput().getSummary());
            }
            ThrowUtils.throwIf(aiConversationMapper.updateById(conversation) != 1, ErrorCode.OPERATION_ERROR,
                    "更新 AI 会话失败");
            return buildChatVO(pendingChat.requestId(), conversation, pendingChat.shoppingContext(),
                    pendingChat.userMessage(), assistantMessage);
        });
    }

    /** 第二段数据库事务：Python 或模型失败时将 PENDING 消息落为可展示、可重试的 FAILED。 */
    private AiChatVO persistAgentFailure(PendingChat pendingChat, AiAgentClientException exception) {
        return transactionTemplate.execute(status -> {
            Date now = new Date();
            AiMessage assistantMessage = pendingChat.assistantMessage();
            assistantMessage.setContent(FAILED_MESSAGE);
            assistantMessage.setStatus(AiMessageStatusEnum.FAILED.getValue());
            assistantMessage.setErrorCode(exception.getErrorCode());
            assistantMessage.setRetryable(exception.isRetryable());
            assistantMessage.setUpdateTime(now);
            ThrowUtils.throwIf(aiMessageMapper.updateById(assistantMessage) != 1, ErrorCode.OPERATION_ERROR,
                    "记录 AI 回复失败状态失败");

            AiConversation conversation = pendingChat.conversation();
            conversation.setLastMessagePreview(FAILED_MESSAGE);
            conversation.setLastMessageTime(now);
            conversation.setUpdateTime(now);
            ThrowUtils.throwIf(aiConversationMapper.updateById(conversation) != 1, ErrorCode.OPERATION_ERROR,
                    "更新 AI 会话失败");
            return buildChatVO(pendingChat.requestId(), conversation, pendingChat.shoppingContext(),
                    pendingChat.userMessage(), assistantMessage);
        });
    }

    @NotNull
    private AiMessage getAssistantMessage(AiConversation conversation, User loginUser, String requestId, Date now) {
        AiMessage assistantMessage = new AiMessage();
        assistantMessage.setConversationId(conversation.getId());
        assistantMessage.setUserId(loginUser.getId());
        assistantMessage.setSequenceNo(2);
        assistantMessage.setRole(AiMessageRoleEnum.ASSISTANT.getValue());
        assistantMessage.setContent(PENDING_MESSAGE);
        assistantMessage.setStatus(AiMessageStatusEnum.PENDING.getValue());
        assistantMessage.setRequestId(requestId);
        assistantMessage.setCreateTime(now);
        assistantMessage.setUpdateTime(now);
        assistantMessage.setIsDelete(0);
        ThrowUtils.throwIf(aiMessageMapper.insert(assistantMessage) != 1, ErrorCode.OPERATION_ERROR,
                "创建助手消息失败");
        return assistantMessage;
    }

    @NotNull
    private AiMessage getUserMessage(AiConversation conversation, User loginUser, String content, String requestId, Date now) {
        AiMessage userMessage = new AiMessage();
        userMessage.setConversationId(conversation.getId());
        userMessage.setUserId(loginUser.getId());
        userMessage.setSequenceNo(1);
        userMessage.setRole(AiMessageRoleEnum.USER.getValue());
        userMessage.setContent(content);
        userMessage.setStatus(AiMessageStatusEnum.SUCCESS.getValue());
        userMessage.setRequestId(requestId);
        userMessage.setCreateTime(now);
        userMessage.setUpdateTime(now);
        userMessage.setIsDelete(0);
        ThrowUtils.throwIf(aiMessageMapper.insert(userMessage) != 1, ErrorCode.OPERATION_ERROR,
                "保存用户消息失败");
        return userMessage;
    }

    @NotNull
    private AiConversation getAiConversation(User loginUser, String content, AiShoppingContext shoppingContext, Date now) {
        AiConversation conversation = new AiConversation();
        conversation.setUserId(loginUser.getId());
        conversation.setTitle(buildConversationTitle(content));
        conversation.setScene(AiConversationSceneEnum.SHOPPING_GUIDE.getValue());
        conversation.setShoppingContext(serializeObject(shoppingContext, "购买条件"));
        conversation.setStatus(AiConversationStatusEnum.ACTIVE.getValue());
        conversation.setLastMessagePreview(PENDING_MESSAGE);
        conversation.setLastMessageTime(now);
        conversation.setCreateTime(now);
        conversation.setUpdateTime(now);
        conversation.setIsDelete(0);
        ThrowUtils.throwIf(aiConversationMapper.insert(conversation) != 1, ErrorCode.OPERATION_ERROR,
                "创建 AI 会话失败");
        return conversation;
    }

    private void validateShoppingContext(AiShoppingContext shoppingContext) {
        if (shoppingContext == null) {
            return;
        }
        BigDecimal budgetMin = shoppingContext.getBudgetMin();
        BigDecimal budgetMax = shoppingContext.getBudgetMax();
        ThrowUtils.throwIf(budgetMin != null && budgetMin.compareTo(BigDecimal.ZERO) < 0,
                ErrorCode.PARAMS_ERROR, "最低预算不能小于 0");
        ThrowUtils.throwIf(budgetMax != null && budgetMax.compareTo(BigDecimal.ZERO) < 0,
                ErrorCode.PARAMS_ERROR, "最高预算不能小于 0");
        ThrowUtils.throwIf(budgetMin != null && budgetMax != null && budgetMax.compareTo(budgetMin) < 0,
                ErrorCode.PARAMS_ERROR, "最高预算不能低于最低预算");
        ThrowUtils.throwIf(StringUtils.length(shoppingContext.getUsageScene()) > MAX_USAGE_SCENE_LENGTH,
                ErrorCode.PARAMS_ERROR, "使用场景不能超过 " + MAX_USAGE_SCENE_LENGTH + " 个字符");
        validateStringList(shoppingContext.getPreferenceTags(), MAX_PREFERENCE_TAG_COUNT,
                MAX_PREFERENCE_TAG_LENGTH, "偏好标签");
        validateStringList(shoppingContext.getAvoidances(), MAX_AVOIDANCE_COUNT,
                MAX_AVOIDANCE_LENGTH, "避雷项");
    }

    private void validateStringList(List<String> values, int maxCount, int maxItemLength, String fieldName) {
        if (values == null) {
            return;
        }
        ThrowUtils.throwIf(values.size() > maxCount, ErrorCode.PARAMS_ERROR, fieldName + "最多 " + maxCount + " 项");
        for (String value : values) {
            ThrowUtils.throwIf(StringUtils.isBlank(value) || value.trim().length() > maxItemLength,
                    ErrorCode.PARAMS_ERROR, fieldName + "内容不能为空且长度不能超过 " + maxItemLength + " 个字符");
        }
    }

    private String serializeObject(Object value, String fieldName) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, fieldName + "序列化失败");
        }
    }

    private String buildConversationTitle(String content) {
        return content.length() <= TITLE_LENGTH ? content : content.substring(0, TITLE_LENGTH) + "…";
    }

    private String buildPreview(String content) {
        String normalizedContent = StringUtils.normalizeSpace(content);
        return normalizedContent.length() <= TITLE_LENGTH ? normalizedContent : normalizedContent.substring(0, TITLE_LENGTH) + "…";
    }

    private AiChatVO buildChatVO(String requestId, AiConversation conversation, AiShoppingContext shoppingContext,
                                 AiMessage userMessage, AiMessage assistantMessage) {
        AiConversationVO conversationVO = new AiConversationVO();
        conversationVO.setId(conversation.getId());
        conversationVO.setTitle(conversation.getTitle());
        conversationVO.setScene(AiConversationSceneEnum.fromValue(conversation.getScene()));
        conversationVO.setShoppingContext(shoppingContext);
        conversationVO.setStatus(AiConversationStatusEnum.fromValue(conversation.getStatus()));
        conversationVO.setLastMessagePreview(conversation.getLastMessagePreview());
        conversationVO.setLastMessageTime(conversation.getLastMessageTime());
        conversationVO.setCreateTime(conversation.getCreateTime());

        AiChatVO chatVO = new AiChatVO();
        chatVO.setRequestId(requestId);
        chatVO.setConversation(conversationVO);
        chatVO.setUserMessage(toMessageVO(userMessage));
        chatVO.setAssistantMessage(toMessageVO(assistantMessage));
        return chatVO;
    }

    private AiMessageVO toMessageVO(AiMessage message) {
        AiMessageVO messageVO = new AiMessageVO();
        messageVO.setId(message.getId());
        messageVO.setSequenceNo(message.getSequenceNo());
        messageVO.setRole(AiMessageRoleEnum.fromValue(message.getRole()));
        messageVO.setContent(message.getContent());
        messageVO.setStructuredContent(deserializeStructuredContent(message.getStructuredContent()));
        messageVO.setStatus(AiMessageStatusEnum.fromValue(message.getStatus()));
        messageVO.setErrorCode(message.getErrorCode());
        messageVO.setRetryable(message.getRetryable());
        messageVO.setCreateTime(message.getCreateTime());
        return messageVO;
    }

    /** 第一阶段没有商品工具，结构化内容只含文本建议；商品推荐的二次校验在下一步加入。 */
    private AiStructuredContentVO deserializeStructuredContent(String structuredContent) {
        if (StringUtils.isBlank(structuredContent)) {
            return null;
        }
        try {
            return objectMapper.readValue(structuredContent, AiStructuredContentVO.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private record PendingChat(String requestId, User loginUser, AiShoppingContext shoppingContext,
                               AiConversation conversation, AiMessage userMessage, AiMessage assistantMessage) {
    }
}
