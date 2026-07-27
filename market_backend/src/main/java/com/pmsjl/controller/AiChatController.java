package com.pmsjl.controller;

import com.pmsjl.common.ErrorCode;
import com.pmsjl.common.Result;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.model.dto.ai.AiChatMessageRequest;
import com.pmsjl.model.dto.ai.AiConversationQueryRequest;
import com.pmsjl.model.dto.ai.AiMessageQueryRequest;
import com.pmsjl.model.enums.AiConversationStatusEnum;
import com.pmsjl.model.vo.AiChatVO;
import com.pmsjl.model.vo.AiConversationVO;
import com.pmsjl.model.vo.AiMessageVO;
import com.pmsjl.model.vo.AiPageVO;
import com.pmsjl.service.AiChatService;
import com.pmsjl.service.AiConversationService;
import com.pmsjl.service.AiMessageService;
import com.pmsjl.utils.ResultUtils;
import com.pmsjl.utils.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai/conversations")
public class AiChatController {

    @Autowired
    private AiChatService aiChatService;
    @Autowired
    private AiMessageService aiMessageService;
    @Autowired
    private AiConversationService aiConversationService;

    /**
     * 创建 AI 会话并同步完成首轮 Agent 问答。
     * Service 会先写入 PENDING 助手消息，再调用 Python，并将同一消息更新为 SUCCESS 或 FAILED。
     */
    @PostMapping
    public Result<AiChatVO> createConversation(@RequestBody AiChatMessageRequest aiChatMessageRequest,
                                                HttpServletRequest request) {
        ThrowUtils.throwIf(aiChatMessageRequest == null, ErrorCode.PARAMS_ERROR);
        AiChatVO aiChatVO = aiChatService.createConversation(aiChatMessageRequest, request);
        return ResultUtils.success(aiChatVO);
    }

    /**
     * 分页查询当前登录用户的 AI 会话，仅访问 Java 业务数据库，不调用 Python Agent。
     */
    @GetMapping
    public Result<AiPageVO<AiConversationVO>> listMyConversations(
            @RequestParam(value = "current", defaultValue = "1") int current,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "sortField", defaultValue = "lastMessageTime") String sortField,
            @RequestParam(value = "sortOrder", defaultValue = "desc") String sortOrder,
            @RequestParam(value = "status", defaultValue = "ACTIVE") String status,
            HttpServletRequest request) {
        AiConversationQueryRequest queryRequest = new AiConversationQueryRequest();
        queryRequest.setCurrent(current);
        queryRequest.setPageSize(pageSize);
        queryRequest.setSortField(sortField);
        queryRequest.setSortOrder(sortOrder);
        queryRequest.setStatus(parseConversationStatus(status));
        AiPageVO<AiConversationVO> page = aiConversationService.listMyConversations(queryRequest, request);
        return ResultUtils.success(page);
    }

    /**
     * 分页查询当前登录用户某个会话的消息，不调用 Python Agent。
     */
    @GetMapping("/{conversationId}/messages")
    public Result<AiPageVO<AiMessageVO>> listConversationMessages(
            @PathVariable("conversationId") Long conversationId,
            @RequestParam(value = "current", defaultValue = "1") int current,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            @RequestParam(value = "sortField", defaultValue = "sequenceNo") String sortField,
            @RequestParam(value = "sortOrder", defaultValue = "desc") String sortOrder,
            HttpServletRequest request) {
        ThrowUtils.throwIf(conversationId == null || conversationId <= 0, ErrorCode.PARAMS_ERROR,
                "conversationId 必须为正整数");
        AiMessageQueryRequest queryRequest = new AiMessageQueryRequest();
        queryRequest.setCurrent(current);
        queryRequest.setPageSize(pageSize);
        queryRequest.setSortField(sortField);
        queryRequest.setSortOrder(sortOrder);
        AiPageVO<AiMessageVO> page = aiMessageService.listConversationMessages(
                conversationId, queryRequest, request);
        return ResultUtils.success(page);
    }

    /**
     * 逻辑删除当前登录用户自己的 AI 会话及其消息，保留 Agent 审计轨迹。
     */
    @DeleteMapping("/{conversationId}")
    public Result<Boolean> deleteConversation(@PathVariable("conversationId") Long conversationId,
                                              HttpServletRequest request) {
        ThrowUtils.throwIf(conversationId == null || conversationId <= 0, ErrorCode.PARAMS_ERROR,
                "conversationId 必须为正整数");
        return ResultUtils.success(aiConversationService.deleteConversation(conversationId, request));
    }

    /**
     * 归档当前登录用户自己的 AI 会话。
     */
    @PostMapping("/{conversationId}/archive")
    public Result<Boolean> archiveConversation(@PathVariable("conversationId") Long conversationId,
                                               HttpServletRequest request) {
        ThrowUtils.throwIf(conversationId == null || conversationId <= 0, ErrorCode.PARAMS_ERROR,
                "conversationId 必须为正整数");
        return ResultUtils.success(aiConversationService.archiveConversation(conversationId, request));
    }

    /**
     * 将当前登录用户自己的已归档 AI 会话恢复为活跃状态。
     */
    @PostMapping("/{conversationId}/restore")
    public Result<Boolean> restoreConversation(@PathVariable("conversationId") Long conversationId,
                                               HttpServletRequest request) {
        ThrowUtils.throwIf(conversationId == null || conversationId <= 0, ErrorCode.PARAMS_ERROR,
                "conversationId 必须为正整数");
        return ResultUtils.success(aiConversationService.restoreConversation(conversationId, request));
    }

    @PostMapping("/{conversationId}/messages")
    public Result<AiChatVO> sendMessage(@PathVariable("conversationId") Long conversationId,
                                        @RequestBody AiChatMessageRequest aiChatMessageRequest,
                                        HttpServletRequest request){
        ThrowUtils.throwIf(conversationId==null||conversationId<=0,ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(aiChatMessageRequest==null,ErrorCode.PARAMS_ERROR);
        AiChatVO aiChatVO=aiMessageService.sendMessage(conversationId,aiChatMessageRequest,request);
        return ResultUtils.success(aiChatVO);

    }

    private AiConversationStatusEnum parseConversationStatus(String status) {
        try {
            return AiConversationStatusEnum.fromValue(status);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "status 仅支持 ACTIVE 或 ARCHIVED");
        }
    }

}
