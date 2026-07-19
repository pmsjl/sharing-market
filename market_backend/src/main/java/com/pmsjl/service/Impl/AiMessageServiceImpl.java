package com.pmsjl.service.Impl;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
import com.pmsjl.model.dto.ai.internal.AgentHistoryMessage;
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
import com.pmsjl.service.AiConversationService;
import com.pmsjl.service.AiMessageService;
import com.pmsjl.service.UserService;
import com.pmsjl.utils.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

import static com.pmsjl.constant.AiChatConstant.*;
import static com.pmsjl.constant.AiChatConstant.FAILED_MESSAGE;

// TODO AI message service implementation placeholder.
@Service
public class AiMessageServiceImpl extends ServiceImpl<AiMessageMapper, AiMessage> implements AiMessageService {
    @Autowired
    private AiChatService aiChatService;
    @Autowired
    private AiConversationService aiConversationService;
    @Autowired
    private AiConversationMapper aiConversationMapper;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private UserService userService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AiAgentClient aiAgentClient;

    @Override
    public AiChatVO sendMessage(Long conversationId, AiChatMessageRequest aiChatMessageRequest, HttpServletRequest request) {
        //1.校验数据
        AiConversation aiConversation = aiConversationService.getById(conversationId);
        ThrowUtils.throwIf(aiConversation == null ||
                        aiConversation.getIsDelete() == 1 ||
                        !StringUtils.equals(aiConversation.getStatus(), AiConversationStatusEnum.ACTIVE.getValue()),
                ErrorCode.NOT_FOUND_ERROR, "会话不存在，无法继续对话");
        String content = StringUtils.trimToEmpty(aiChatMessageRequest.getContent());
        ThrowUtils.throwIf(StringUtils.isBlank(content), ErrorCode.PARAMS_ERROR, "咨询内容不能为空");
        ThrowUtils.throwIf(content.length() > MAX_MESSAGE_LENGTH, ErrorCode.PARAMS_ERROR,
                "咨询内容不能超过 " + MAX_MESSAGE_LENGTH + " 个字符");
        AiShoppingContext aiShoppingContext = aiChatMessageRequest.getShoppingContext();
        aiChatService.validateShoppingContext(aiShoppingContext);
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(!ObjectUtil.equals(loginUser.getId(), aiConversation.getUserId()),
                ErrorCode.NO_AUTH_ERROR, "对话属于其他用户，无法正常发送");


        String requestId = UUID.randomUUID().toString();
        PendingMessage pendingMessage = transactionTemplate.execute(status -> {
            //这里自定义了一个方法利用FOR UPDATE上了行锁，避免了不必要的并发导致no相同的问题，结束条件是事务提交
            // 获取的conversation不是重点，主要是上锁
            AiConversation conversation =
                    aiConversationMapper.selectByIdForUpdate(conversationId);
            ThrowUtils.throwIf(
                    conversation == null,
                    ErrorCode.NOT_FOUND_ERROR,
                    "会话不存在"
            );
            //1.检查pendingMessage，原则上我们当前所发的消息之前不应该有其他pending的消息，否则要做相应处理
            // 因为锁释放了，可能还在获取agent消息，这时候其他请求再次涌入可能出现多个pending情况，
            //我们要确保如果已存在pending，并且不超时。新的消息无法发送
            //但如果没有pendingMessage，或者有但是已经超时了，那就可以正常发送了
            checkPendingMessage(conversationId);

            //2.更新数据
            String shoppingContext = serializeObject(aiShoppingContext, "购买条件");
            conversation.setShoppingContext(shoppingContext);
            boolean result = aiConversationService.lambdaUpdate().
                    eq(AiConversation::getId, conversationId).
                    set(AiConversation::getShoppingContext, shoppingContext).
                    update();
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            //3.为了给agentRequest添加history信息，同时不被锁释放后的其他消息影响，
            // 所以我们获得的history最好利用锁获取真实的历史消息
            List<AiMessage> historyMessageList = this.lambdaQuery().
                    eq(AiMessage::getConversationId, conversationId).
                    eq(AiMessage::getStatus, AiMessageStatusEnum.SUCCESS.getValue()).
                    orderByDesc(AiMessage::getSequenceNo).
                    last("LIMIT 10").
                    list();
            List<AgentHistoryMessage> agentHistoryMessages = historyMessageList.stream().
                    sorted(Comparator.comparing(AiMessage::getSequenceNo)).
                    map(message -> {
                        String historyContent = message.getContent();
                        String role = message.getRole();
                        AgentHistoryMessage historyMessage = new AgentHistoryMessage();
                        historyMessage.setContent(historyContent);
                        historyMessage.setRole(AiMessageRoleEnum.fromValue(role));
                        return historyMessage;
                    }).toList();
            return this.addPendingMessage(loginUser, content, aiShoppingContext, requestId, conversationId, conversation, agentHistoryMessages);
        });
        ThrowUtils.throwIf(pendingMessage == null, ErrorCode.OPERATION_ERROR, "创建 AI 会话失败");

        //3.构建传入python的请求类
        AgentRunRequest agentRunRequest = buildAgentRunRequest(pendingMessage);
        try {
            AgentRunResponse agentRunResponse = aiAgentClient.runAgent(requestId, agentRunRequest);
            return persistAgentSuccess(pendingMessage, agentRunResponse);
        } catch (AiAgentClientException e) {
            return persistAgentFailure(pendingMessage, e);
        }


    }

    private void checkPendingMessage(Long conversationId) {
        AiMessage oldPendingMessage = this.lambdaQuery().
                select(AiMessage::getCreateTime,AiMessage::getId).
                eq(AiMessage::getRole, AiMessageRoleEnum.ASSISTANT.getValue()).
                eq(AiMessage::getStatus, AiMessageStatusEnum.PENDING.getValue()).
                eq(AiMessage::getConversationId,conversationId).
                orderByDesc(AiMessage::getSequenceNo).
                last("LIMIT 1").
                one();
        if (oldPendingMessage != null) {
            boolean stale = oldPendingMessage.getCreateTime()
                    .before(new Date(
                            System.currentTimeMillis() - PENDING_TIMEOUT_MILLIS
                    ));
            if(stale){
                oldPendingMessage.setStatus(AiMessageStatusEnum.FAILED.getValue());
                oldPendingMessage.setUpdateTime(new Date());
                oldPendingMessage.setRetryable(Boolean.FALSE);
                oldPendingMessage.setContent(
                        "上一条咨询等待超时，请重新发送。"
                );
                oldPendingMessage.setAgentErrorKey(
                        "AI_AGENT_PENDING_TIMEOUT"
                );
                boolean changed = this.lambdaUpdate()
                        .eq(AiMessage::getId, oldPendingMessage.getId())
                        .eq(AiMessage::getStatus, AiMessageStatusEnum.PENDING.getValue())
                        .set(AiMessage::getStatus, oldPendingMessage.getStatus())
                        .set(AiMessage::getUpdateTime, oldPendingMessage.getUpdateTime())
                        .set(AiMessage::getRetryable, oldPendingMessage.getRetryable())
                        .set(AiMessage::getContent, oldPendingMessage.getContent())
                        .set(AiMessage::getAgentErrorKey, oldPendingMessage.getAgentErrorKey())
                        .update();
                ThrowUtils.throwIf(!changed, ErrorCode.CONFLICT_ERROR,
                        "Pending message state has already changed");
            }else{
                throw new BusinessException(ErrorCode.CONFLICT_ERROR,"上一条消息正在回复中，无法发送新消息");
            }

        }
    }

    private AiChatVO persistAgentFailure(PendingMessage pendingMessage, AiAgentClientException e) {
        return transactionTemplate.execute(status -> {
            AiConversation conversation = aiConversationMapper.selectByIdForUpdate(
                    pendingMessage.conversation().getId());
            //这里也加锁，是因为不加锁会导致出现我们这里将assistant的message更新为success或者fail
            //然后我们下一轮消息的第一波消息校验通过因为不是pending，但是下一轮的第二波消息可能比这里更新更快
            //导致这里的第二波消息更新又覆盖回退了conversation。
            //造成这一问题的原因就是我们的这里操作不具备原子性，中间被其他代码插入了，所以这里加锁就可以解决问题
            ThrowUtils.throwIf(conversation == null, ErrorCode.NOT_FOUND_ERROR,
                    "Conversation does not exist");

            Date now = new Date();
            AiMessage assistantMessage = pendingMessage.assistantMessage();
            assistantMessage.setContent(FAILED_MESSAGE);
            assistantMessage.setStatus(AiMessageStatusEnum.FAILED.getValue());
            assistantMessage.setAgentErrorKey(e.getAgentErrorKey());
            assistantMessage.setRetryable(e.isRetryable());
            assistantMessage.setUpdateTime(now);
            ThrowUtils.throwIf(!updateAssistantMessageIfPending(assistantMessage), ErrorCode.CONFLICT_ERROR,
                    "记录 AI 回复失败状态失败");

            conversation.setLastMessagePreview(FAILED_MESSAGE);
            conversation.setLastMessageTime(now);
            conversation.setUpdateTime(now);
            ThrowUtils.throwIf(aiConversationMapper.updateById(conversation) != 1, ErrorCode.OPERATION_ERROR,
                    "更新 AI 会话失败");
            return buildChatVO(pendingMessage.requestId(), conversation, pendingMessage.shoppingContext(),
                    pendingMessage.userMessage(), assistantMessage);
        });
    }

    private AiChatVO persistAgentSuccess(PendingMessage pendingMessage, AgentRunResponse agentRunResponse) {
        return transactionTemplate.execute(status -> {
            AiConversation conversation = aiConversationMapper.selectByIdForUpdate(
                    pendingMessage.conversation().getId());
            ThrowUtils.throwIf(conversation == null, ErrorCode.NOT_FOUND_ERROR,
                    "Conversation does not exist");

            Date now = new Date();
            AiMessage assistantMessage = pendingMessage.assistantMessage();
            assistantMessage.setContent(agentRunResponse.getAnswer().trim());
            assistantMessage.setStructuredContent(serializeObject(agentRunResponse.getOutput(), "AI 结构化结果"));
            assistantMessage.setModelName(agentRunResponse.getModel() == null ? null : agentRunResponse.getModel().getName());
            assistantMessage.setInputTokens(agentRunResponse.getUsage() == null ? null : agentRunResponse.getUsage().getInputTokens());
            assistantMessage.setOutputTokens(agentRunResponse.getUsage() == null ? null : agentRunResponse.getUsage().getOutputTokens());
            assistantMessage.setLatencyMs(agentRunResponse.getLatencyMs());
            assistantMessage.setStatus(AiMessageStatusEnum.SUCCESS.getValue());
            assistantMessage.setAgentErrorKey(null);
            assistantMessage.setRetryable(false);
            assistantMessage.setUpdateTime(now);
            ThrowUtils.throwIf(!updateAssistantMessageIfPending(assistantMessage), ErrorCode.CONFLICT_ERROR,
                    "更新 AI 回复失败");

            conversation.setLastMessagePreview(buildPreview(assistantMessage.getContent()));
            conversation.setLastMessageTime(now);
            conversation.setUpdateTime(now);
            if (agentRunResponse.getOutput() != null) {
                conversation.setMemorySummary(agentRunResponse.getOutput().getSummary());
            }
            ThrowUtils.throwIf(aiConversationMapper.updateById(conversation) != 1, ErrorCode.OPERATION_ERROR,
                    "更新 AI 会话失败");
            return buildChatVO(pendingMessage.requestId(), conversation, pendingMessage.shoppingContext(),
                    pendingMessage.userMessage(), assistantMessage);
        });

    }

    private AiChatVO buildChatVO(String requestId, AiConversation conversation, AiShoppingContext shoppingContext, AiMessage userMessage, AiMessage assistantMessage) {
        AiChatVO chatVO = new AiChatVO();
        chatVO.setRequestId(requestId);
        chatVO.setConversation(toAiConversationVO(conversation, shoppingContext));
        chatVO.setUserMessage(toMessageVO(userMessage));
        chatVO.setAssistantMessage(toMessageVO(assistantMessage));
        return chatVO;
    }

    @NotNull
    private static AiConversationVO toAiConversationVO(AiConversation conversation, AiShoppingContext shoppingContext) {
        AiConversationVO conversationVO = new AiConversationVO();
        conversationVO.setId(conversation.getId());
        conversationVO.setTitle(conversation.getTitle());
        conversationVO.setScene(AiConversationSceneEnum.fromValue(conversation.getScene()));
        conversationVO.setShoppingContext(shoppingContext);
        conversationVO.setStatus(AiConversationStatusEnum.fromValue(conversation.getStatus()));
        conversationVO.setLastMessagePreview(conversation.getLastMessagePreview());
        conversationVO.setLastMessageTime(conversation.getLastMessageTime());
        conversationVO.setCreateTime(conversation.getCreateTime());
        return conversationVO;
    }

    private AiMessageVO toMessageVO(AiMessage message) {
        AiMessageVO messageVO = new AiMessageVO();
        messageVO.setId(message.getId());
        messageVO.setSequenceNo(message.getSequenceNo());
        messageVO.setRole(AiMessageRoleEnum.fromValue(message.getRole()));
        messageVO.setContent(message.getContent());
        messageVO.setStructuredContent(deserializeStructuredContent(message.getStructuredContent()));
        messageVO.setStatus(AiMessageStatusEnum.fromValue(message.getStatus()));
        messageVO.setAgentErrorKey(message.getAgentErrorKey());
        messageVO.setRetryable(message.getRetryable());
        messageVO.setCreateTime(message.getCreateTime());
        return messageVO;
    }

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


    private String buildPreview(String content) {
        String normalizedContent = StringUtils.normalizeSpace(content);
        return normalizedContent.length() <= TITLE_LENGTH ? normalizedContent : normalizedContent.substring(0, TITLE_LENGTH) + "…";

    }

    private AgentRunRequest buildAgentRunRequest(PendingMessage pendingMessage) {
        AgentRunRequest request = new AgentRunRequest();
        request.setUserId(pendingMessage.loginUser().getId());
        request.setMessage(pendingMessage.content());
        request.setConversationId(pendingMessage.conversation().getId());
        request.setShoppingContext(pendingMessage.shoppingContext());
        request.setMemorySummary(pendingMessage.conversation().getMemorySummary());
        request.setHistory(pendingMessage.agentHistoryMessages());
        return request;
    }

    private PendingMessage addPendingMessage(User loginUser, String content,
                                             AiShoppingContext shoppingContext, String requestId,
                                             Long conversationId, AiConversation conversation, List<AgentHistoryMessage> agentHistoryMessages) {
        Long userId = loginUser.getId();
        DateTime now = DateTime.now();

        //插入第一阶段数据，并存储
        AiMessage userMessage = getUserMessage(conversationId, userId, content, shoppingContext, requestId, now);
        AiMessage assistantMessage = getAssistantMessage(conversationId, userId, content, shoppingContext, requestId, now, userMessage.getSequenceNo());
        updateAiConversation(conversation, now);
        return new PendingMessage(requestId, loginUser, content, shoppingContext, conversation, userMessage, assistantMessage, agentHistoryMessages);
    }

    private void updateAiConversation(AiConversation conversation, DateTime now) {
        conversation.setLastMessagePreview(PENDING_MESSAGE);
        conversation.setLastMessageTime(now);
        conversation.setUpdateTime(now);

// 若本轮提供了新条件，conversation.shoppingContext 此时也已是新值
        ThrowUtils.throwIf(
                aiConversationMapper.updateById(conversation) != 1,
                ErrorCode.OPERATION_ERROR,
                "更新会话待回复状态失败"
        );
    }

    private AiMessage getAssistantMessage(Long conversationId, Long userId, String content, AiShoppingContext shoppingContext, String requestId, DateTime now, Integer sequenceNo) {
        AiMessage assistantMessage = new AiMessage();
        assistantMessage.setConversationId(conversationId);
        assistantMessage.setUserId(userId);
        assistantMessage.setRole(AiMessageRoleEnum.ASSISTANT.getValue());
        assistantMessage.setContent(PENDING_MESSAGE);
        assistantMessage.setStatus(AiMessageStatusEnum.PENDING.getValue());
        assistantMessage.setRequestId(requestId);
        assistantMessage.setCreateTime(now);
        assistantMessage.setUpdateTime(now);
        assistantMessage.setIsDelete(0);
        assistantMessage.setSequenceNo(sequenceNo + 1);
        ThrowUtils.throwIf(!this.save(assistantMessage), ErrorCode.OPERATION_ERROR,
                "创建助手消息失败");
        return assistantMessage;
    }

    private AiMessage getUserMessage(Long conversationId, Long userId, String content, AiShoppingContext shoppingContext, String requestId, DateTime now) {
        AiMessage userMessage = new AiMessage();
        userMessage.setUserId(userId);
        userMessage.setContent(content);
        userMessage.setConversationId(conversationId);
        userMessage.setRequestId(requestId);
        userMessage.setIsDelete(0);
        userMessage.setCreateTime(now);
        userMessage.setUpdateTime(now);
        userMessage.setRole(AiMessageRoleEnum.USER.getValue());
        userMessage.setStatus(AiMessageStatusEnum.SUCCESS.getValue());
        AiMessage message = this.lambdaQuery().select(AiMessage::getSequenceNo).
                eq(AiMessage::getConversationId, conversationId).
                orderByDesc(AiMessage::getSequenceNo).
                last("LIMIT 1").
                one();
        Integer sequenceNo = message.getSequenceNo();
        userMessage.setSequenceNo(sequenceNo + 1);
        ThrowUtils.throwIf(!this.save(userMessage), ErrorCode.OPERATION_ERROR,
                "创建用户消息失败");
        return userMessage;

    }


    /**
     * Completes one assistant message only if it is still waiting for this request's result.
     * This prevents a late result from resurrecting a message already marked as failed.
     */
    private boolean updateAssistantMessageIfPending(AiMessage assistantMessage) {
        return this.lambdaUpdate()
                .eq(AiMessage::getId, assistantMessage.getId())
                .eq(AiMessage::getStatus, AiMessageStatusEnum.PENDING.getValue())
                .set(AiMessage::getContent, assistantMessage.getContent())
                .set(AiMessage::getStructuredContent, assistantMessage.getStructuredContent())
                .set(AiMessage::getModelName, assistantMessage.getModelName())
                .set(AiMessage::getStatus, assistantMessage.getStatus())
                .set(AiMessage::getInputTokens, assistantMessage.getInputTokens())
                .set(AiMessage::getOutputTokens, assistantMessage.getOutputTokens())
                .set(AiMessage::getLatencyMs, assistantMessage.getLatencyMs())
                .set(AiMessage::getAgentErrorKey, assistantMessage.getAgentErrorKey())
                .set(AiMessage::getRetryable, assistantMessage.getRetryable())
                .set(AiMessage::getUpdateTime, assistantMessage.getUpdateTime())
                .update();
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

    private record PendingMessage(String requestId, User loginUser, String content, AiShoppingContext shoppingContext,
                                  AiConversation conversation, AiMessage userMessage, AiMessage assistantMessage,
                                  List<AgentHistoryMessage> agentHistoryMessages) {
    }
}
