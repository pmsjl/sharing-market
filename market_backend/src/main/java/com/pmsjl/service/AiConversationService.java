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
}
