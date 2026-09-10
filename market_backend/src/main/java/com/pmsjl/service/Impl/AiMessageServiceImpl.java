package com.pmsjl.service.Impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.config.AiAgentProperties;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.manager.AiAgentClient;
import com.pmsjl.manager.AiAgentClientException;
import com.pmsjl.manager.AiStructuredContentAssembler;
import com.pmsjl.mapper.AiConversationMapper;
import com.pmsjl.mapper.AiMessageMapper;
import com.pmsjl.model.dto.ai.AiChatMessageRequest;
import com.pmsjl.model.dto.ai.AiMessageQueryRequest;
import com.pmsjl.model.dto.ai.AiShoppingContext;
import com.pmsjl.model.dto.ai.AiUsageDate;
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
import com.pmsjl.model.vo.AiPageVO;
import com.pmsjl.model.vo.AiStructuredContentVO;
import com.pmsjl.service.AiAgentTraceService;
import com.pmsjl.service.AiAccessService;
import com.pmsjl.service.AiChatService;
import com.pmsjl.service.AiConversationService;
import com.pmsjl.service.AiMessageService;
import com.pmsjl.service.UserService;
import com.pmsjl.utils.ThrowUtils;
import com.pmsjl.utils.PersistenceTime;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

import static com.pmsjl.constant.AiChatConstant.*;
import static com.pmsjl.constant.AiChatConstant.FAILED_MESSAGE;

@Service
@Slf4j
public class AiMessageServiceImpl extends ServiceImpl<AiMessageMapper, AiMessage> implements AiMessageService {
    private static final int MAX_MESSAGE_PAGE_SIZE = 50;
    private static final int RECENT_HISTORY_TURN_LIMIT = 5;
    private static final Set<String> ALLOWED_MESSAGE_SORT_FIELDS = Set.of("sequenceNo");

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
    @Autowired
    private AiAgentTraceService aiAgentTraceService;
    @Autowired
    private AiStructuredContentAssembler aiStructuredContentAssembler;
    @Autowired
    private AiAgentProperties aiAgentProperties;
    @Autowired
    private AiAccessService aiAccessService;

    @Override
    public AiPageVO<AiMessageVO> listConversationMessages(Long conversationId,
                                                           AiMessageQueryRequest queryRequest,
                                                           HttpServletRequest request) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        int current = queryRequest.getCurrent();
        int pageSize = queryRequest.getPageSize();
        String sortField = queryRequest.getSortField();
        String sortOrder = queryRequest.getSortOrder();
        if (current <= 0) {
            current = 1;
        }
        if (pageSize <= 0 || pageSize > MAX_MESSAGE_PAGE_SIZE) {
            pageSize = 20;
        }

        User loginUser = userService.getLoginUser();
        AiConversation conversation = aiConversationService.getById(conversationId);
        ThrowUtils.throwIf(conversation == null, ErrorCode.NOT_FOUND_ERROR, "会话不存在或已删除");
        ThrowUtils.throwIf(!ObjectUtil.equals(conversation.getUserId(), loginUser.getId()),
                ErrorCode.NO_AUTH_ERROR, "会话属于其他用户");

        Page<AiMessage> page = new Page<>(current, pageSize);
        boolean ascending = "asc".equalsIgnoreCase(sortOrder);
        if (StringUtils.isNotBlank(sortField) && ALLOWED_MESSAGE_SORT_FIELDS.contains(sortField)) {
            page.addOrder(ascending ? OrderItem.asc(sortField) : OrderItem.desc(sortField));
            page.addOrder(OrderItem.desc("id"));
        } else {
            page.addOrder(OrderItem.desc("sequenceNo"), OrderItem.desc("id"));
        }
        LambdaQueryWrapper<AiMessage> queryWrapper = new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId)
                .eq(AiMessage::getUserId, loginUser.getId());
        Page<AiMessage> entityPage = baseMapper.selectPage(page, queryWrapper);
        List<AiMessageVO> records = entityPage.getRecords().stream()
                .sorted(Comparator.comparing(AiMessage::getSequenceNo)
                        .thenComparing(AiMessage::getId))
                .map(this::toMessageVO)
                .toList();

        AiPageVO<AiMessageVO> result = new AiPageVO<>();
        result.setCurrent(entityPage.getCurrent());
        result.setPageSize(entityPage.getSize());
        result.setTotal(entityPage.getTotal());
        result.setRecords(records);
        return result;
    }

    @Override
    public AiChatVO sendMessage(Long conversationId, AiChatMessageRequest aiChatMessageRequest, HttpServletRequest request) {
        //1.校验数据
        String content = StringUtils.trimToEmpty(aiChatMessageRequest.getContent());
        ThrowUtils.throwIf(StringUtils.isBlank(content), ErrorCode.PARAMS_ERROR, "咨询内容不能为空");
        ThrowUtils.throwIf(content.length() > MAX_MESSAGE_LENGTH, ErrorCode.PARAMS_ERROR,
                "咨询内容不能超过 " + MAX_MESSAGE_LENGTH + " 个字符");
        AiShoppingContext aiShoppingContext = aiChatMessageRequest.getShoppingContext();
        aiChatService.validateShoppingContext(aiShoppingContext);
        User loginUser = userService.getLoginUser();

        String requestId = UUID.randomUUID().toString();
        PendingMessage pendingMessage = transactionTemplate.execute(status -> {
            AiConversation conversation =
                    aiConversationMapper.selectOwnedByIdForUpdate(conversationId, loginUser.getId());
            ThrowUtils.throwIf(
                    conversation == null,
                    ErrorCode.NOT_FOUND_ERROR,
                    "会话不存在，无法继续对话"
            );
            ThrowUtils.throwIf(
                    !StringUtils.equals(conversation.getStatus(), AiConversationStatusEnum.ACTIVE.getValue()),
                    ErrorCode.NOT_FOUND_ERROR,
                    "会话不存在，无法继续对话"
            );
            //1.会话行锁内检查 pending，确保并发发送不会产生多个未完成回复
            // 因为锁释放了，可能还在获取agent消息，这时候其他请求再次涌入可能出现多个pending情况，
            //我们要确保如果已存在pending，并且不超时。新的消息无法发送
            //但如果没有pendingMessage，或者有但是已经超时了，那就可以正常发送了
            checkPendingMessage(conversationId);

            //2.会话锁已持有，再预占用量；所有既有会话事务都遵循“会话锁 -> 用量锁”
            AiUsageDate usageReservation = aiAccessService.reserveUsage(loginUser.getId());

            //3.更新消息和会话
            String shoppingContext = serializeObject(aiShoppingContext, "购买条件");
            conversation.setShoppingContext(shoppingContext);
            //4.为了给 agentRequest 添加 history 信息，同时不被锁释放后的其他消息影响，
            // 所以我们获得的history最好利用锁获取真实的历史消息
            List<AgentHistoryMessage> agentHistoryMessages = baseMapper
                    .selectRecentSuccessfulHistory(
                            conversationId,
                            RECENT_HISTORY_TURN_LIMIT
                    )
                    .stream()
                    .map(message -> {
                        AgentHistoryMessage historyMessage = new AgentHistoryMessage();
                        historyMessage.setRole(
                                AiMessageRoleEnum.fromValue(message.getRole())
                        );
                        historyMessage.setContent(message.getContent());
                        return historyMessage;
                    })
                    .toList();

            return this.addPendingMessage(loginUser, content, aiShoppingContext, requestId,
                    conversationId, conversation, agentHistoryMessages, usageReservation);
        });
        ThrowUtils.throwIf(pendingMessage == null, ErrorCode.OPERATION_ERROR, "创建 AI 会话失败");

        //5.构建传入Python的请求类
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
                select(AiMessage::getCreateTime, AiMessage::getId).
                eq(AiMessage::getRole, AiMessageRoleEnum.ASSISTANT.getValue()).
                eq(AiMessage::getStatus, AiMessageStatusEnum.PENDING.getValue()).
                eq(AiMessage::getConversationId, conversationId).
                orderByDesc(AiMessage::getSequenceNo).
                last("LIMIT 1").
                one();
        if (oldPendingMessage != null) {
            Date expireBefore = new Date(
                    System.currentTimeMillis() - aiAgentProperties.getPendingTimeoutMs());
            if (oldPendingMessage.getCreateTime().before(expireBefore)) {
                int changed = baseMapper.markPendingMessageTimedOut(
                        oldPendingMessage.getId(),
                        expireBefore,
                        PENDING_TIMEOUT_MESSAGE,
                        PENDING_TIMEOUT_ERROR_KEY
                );
                ThrowUtils.throwIf(changed != 1, ErrorCode.CONFLICT_ERROR,
                        "Pending message state has already changed");
            } else {
                throw new BusinessException(ErrorCode.CONFLICT_ERROR, "上一条消息正在回复中，无法发送新消息");
            }

        }
    }

    @Override
    public int expireStalePendingMessages(Date expireBefore, int batchSize) {
        ThrowUtils.throwIf(expireBefore == null, ErrorCode.PARAMS_ERROR, "expireBefore 不能为空");
        ThrowUtils.throwIf(batchSize <= 0 || batchSize > 1000, ErrorCode.PARAMS_ERROR,
                "batchSize 必须在 1 到 1000 之间");

        List<AiMessage> candidates = baseMapper.selectStalePendingMessages(expireBefore, batchSize);
        if (candidates == null || candidates.isEmpty()) {
            return 0;
        }

        int expiredCount = 0;
        for (AiMessage candidate : candidates) {
            try {
                Boolean expired = transactionTemplate.execute(
                        status -> expireOnePendingMessage(candidate, expireBefore));
                if (Boolean.TRUE.equals(expired)) {
                    expiredCount++;
                }
            } catch (Exception exception) {
                log.error("清理超时 AI 消息失败，messageId={}, conversationId={}",
                        candidate.getId(), candidate.getConversationId(), exception);
            }
        }
        return expiredCount;
    }

    private Boolean expireOnePendingMessage(AiMessage candidate, Date expireBefore) {
        AiConversation conversation = aiConversationMapper.selectByIdForUpdate(candidate.getConversationId());
        if (conversation == null) {
            log.warn("超时 AI 消息对应会话不存在，跳过清理，messageId={}, conversationId={}",
                    candidate.getId(), candidate.getConversationId());
            return false;
        }

        Date now = PersistenceTime.now();
        int changed = baseMapper.markPendingMessageTimedOut(
                candidate.getId(),
                expireBefore,
                PENDING_TIMEOUT_MESSAGE,
                PENDING_TIMEOUT_ERROR_KEY
        );
        if (changed != 1) {
            return false;
        }

        conversation.setLastMessagePreview(PENDING_TIMEOUT_MESSAGE);
        conversation.setLastMessageTime(now);
        conversation.setUpdateTime(now);
        ThrowUtils.throwIf(aiConversationMapper.updateById(conversation) != 1,
                ErrorCode.OPERATION_ERROR, "更新超时 AI 会话失败");
        return true;
    }

    private AiChatVO persistAgentFailure(PendingMessage pendingMessage, AiAgentClientException e) {
        return transactionTemplate.execute(status -> {
            AiConversation conversation = aiConversationMapper.selectByIdForUpdate(
                    pendingMessage.conversation().getId());
            // 完成结果必须基于当前会话行和消息状态，不能使用请求开始时缓存的旧会话对象覆盖并发更新。
            ThrowUtils.throwIf(conversation == null, ErrorCode.NOT_FOUND_ERROR,
                    "Conversation does not exist");

            Date now = PersistenceTime.now();
            AiMessage assistantMessage = pendingMessage.assistantMessage();
            assistantMessage.setUpdateTime(now);
            assistantMessage.setContent(FAILED_MESSAGE);
            assistantMessage.setStatus(AiMessageStatusEnum.FAILED.getValue());
            assistantMessage.setAgentErrorKey(e.getAgentErrorKey());
            assistantMessage.setRetryable(e.isRetryable());
            ThrowUtils.throwIf(baseMapper.updatePendingAssistantMessage(assistantMessage) != 1,
                    ErrorCode.CONFLICT_ERROR,
                    "记录 AI 回复失败状态失败");

            aiAccessService.recordFailure(
                    pendingMessage.loginUser().getId(),
                    pendingMessage.usageReservation()
            );

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

            Date now = PersistenceTime.now();
            AiStructuredContentVO structuredContent = aiStructuredContentAssembler.assemble(
                    agentRunResponse.getOutput());
            AiMessage assistantMessage = pendingMessage.assistantMessage();
            assistantMessage.setUpdateTime(now);
            assistantMessage.setContent(agentRunResponse.getAnswer().trim());
            assistantMessage.setStructuredContent(serializeObject(structuredContent, "AI 结构化结果"));
            assistantMessage.setModelName(agentRunResponse.getModel() == null ? null : agentRunResponse.getModel().getName());
            assistantMessage.setInputTokens(agentRunResponse.getUsage() == null ? null : agentRunResponse.getUsage().getInputTokens());
            assistantMessage.setOutputTokens(agentRunResponse.getUsage() == null ? null : agentRunResponse.getUsage().getOutputTokens());
            assistantMessage.setLatencyMs(agentRunResponse.getLatencyMs());
            assistantMessage.setStatus(AiMessageStatusEnum.SUCCESS.getValue());
            assistantMessage.setAgentErrorKey(null);
            assistantMessage.setRetryable(false);
            ThrowUtils.throwIf(baseMapper.updatePendingAssistantMessage(assistantMessage) != 1,
                    ErrorCode.CONFLICT_ERROR,
                    "更新 AI 回复失败");

            aiAgentTraceService.saveAgentTraces(
                    pendingMessage.requestId(),
                    conversation.getId(),
                    assistantMessage.getId(),
                    agentRunResponse.getTraces()
            );

            aiAccessService.recordSuccess(
                    pendingMessage.loginUser().getId(),
                    pendingMessage.usageReservation(),
                    agentRunResponse.getUsage()
            );

            conversation.setMemorySummary(agentRunResponse.getOutput().getMemorySummary());
            conversation.setLastMessagePreview(buildPreview(assistantMessage.getContent()));
            conversation.setLastMessageTime(now);
            conversation.setUpdateTime(now);
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
        BeanUtils.copyProperties(message, messageVO);
        messageVO.setRole(AiMessageRoleEnum.fromValue(message.getRole()));
        messageVO.setStructuredContent(deserializeStructuredContent(message.getStructuredContent()));
        messageVO.setStatus(AiMessageStatusEnum.fromValue(message.getStatus()));
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
                                             Long conversationId, AiConversation conversation,
                                             List<AgentHistoryMessage> agentHistoryMessages,
                                             AiUsageDate usageReservation) {
        Long userId = loginUser.getId();
        Date now = PersistenceTime.now();

        //插入第一阶段数据，并存储
        AiMessage userMessage = getUserMessage(conversationId, userId, content, shoppingContext, requestId);
        AiMessage assistantMessage = getAssistantMessage(conversationId, userId, content, shoppingContext, requestId, userMessage.getSequenceNo());
        updateAiConversation(conversation, now);
        return new PendingMessage(requestId, loginUser, content, shoppingContext, conversation,
                userMessage, assistantMessage, agentHistoryMessages, usageReservation);
    }

    private void updateAiConversation(AiConversation conversation, Date now) {
        conversation.setLastMessagePreview(PENDING_MESSAGE);
        conversation.setLastMessageTime(now);
        conversation.setUpdateTime(now);
// 若本轮提供了新条件，conversation.shoppingContext 此时也已是新值
        // Explicitly set shoppingContext so a null value still clears previous conditions.
        boolean updated = aiConversationService.lambdaUpdate()
                .eq(AiConversation::getId, conversation.getId())
                .set(AiConversation::getShoppingContext, conversation.getShoppingContext())
                .set(AiConversation::getLastMessagePreview, conversation.getLastMessagePreview())
                .set(AiConversation::getLastMessageTime, now)
                .set(AiConversation::getUpdateTime, now)
                .update();
        ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "更新会话待回复状态失败");
    }

    private AiMessage getAssistantMessage(Long conversationId, Long userId, String content, AiShoppingContext shoppingContext, String requestId, Integer sequenceNo) {
        AiMessage assistantMessage = new AiMessage();
        assistantMessage.setConversationId(conversationId);
        assistantMessage.setUserId(userId);
        assistantMessage.setRole(AiMessageRoleEnum.ASSISTANT.getValue());
        assistantMessage.setContent(PENDING_MESSAGE);
        assistantMessage.setStatus(AiMessageStatusEnum.PENDING.getValue());
        assistantMessage.setRequestId(requestId);
        assistantMessage.setIsDelete(0);
        Date now = PersistenceTime.now();
        assistantMessage.setCreateTime(now);
        assistantMessage.setUpdateTime(now);
        assistantMessage.setSequenceNo(sequenceNo + 1);
        ThrowUtils.throwIf(!this.save(assistantMessage), ErrorCode.OPERATION_ERROR,
                "创建助手消息失败");
        return assistantMessage;
    }

    private AiMessage getUserMessage(Long conversationId, Long userId, String content, AiShoppingContext shoppingContext, String requestId) {
        AiMessage userMessage = new AiMessage();
        userMessage.setUserId(userId);
        userMessage.setContent(content);
        userMessage.setConversationId(conversationId);
        userMessage.setRequestId(requestId);
        userMessage.setIsDelete(0);
        Date now = PersistenceTime.now();
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
                                  List<AgentHistoryMessage> agentHistoryMessages,
                                  AiUsageDate usageReservation) {
    }

}
