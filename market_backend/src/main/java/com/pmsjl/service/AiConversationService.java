package com.pmsjl.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pmsjl.model.dto.ai.AiConversationQueryRequest;
import com.pmsjl.model.entity.AiConversation;
import com.pmsjl.model.vo.AiConversationVO;
import com.pmsjl.model.vo.AiPageVO;
import jakarta.servlet.http.HttpServletRequest;

public interface AiConversationService extends IService<AiConversation> {

    /**
     * 分页查询当前登录用户未删除的会话。
     */
    AiPageVO<AiConversationVO> listMyConversations(AiConversationQueryRequest queryRequest,
                                                    HttpServletRequest request);

    /**
     * 逻辑删除当前登录用户自己的会话和所属消息，保留 Agent 审计轨迹。
     */
    Boolean deleteConversation(Long conversationId, HttpServletRequest request);

    /**
     * 归档当前登录用户自己的会话。
     */
    Boolean archiveConversation(Long conversationId, HttpServletRequest request);

    /**
     * 恢复当前登录用户自己的已归档会话。
     */
    Boolean restoreConversation(Long conversationId, HttpServletRequest request);
}
