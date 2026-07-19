package com.pmsjl.controller;

import com.pmsjl.common.ErrorCode;
import com.pmsjl.common.Result;
import com.pmsjl.model.dto.ai.AiChatMessageRequest;
import com.pmsjl.model.vo.AiChatVO;
import com.pmsjl.service.AiChatService;
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
    @PostMapping("/{conversationId}/messages")
    public Result<AiChatVO> sendMessage(@PathVariable("conversationId") Long conversationId,
                                        @RequestBody AiChatMessageRequest aiChatMessageRequest,
                                        HttpServletRequest request){
        ThrowUtils.throwIf(conversationId==null||conversationId<=0,ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(aiChatMessageRequest==null,ErrorCode.PARAMS_ERROR);
        AiChatVO aiChatVO=aiMessageService.sendMessage(conversationId,aiChatMessageRequest,request);
        return ResultUtils.success(aiChatVO);

    }

}
