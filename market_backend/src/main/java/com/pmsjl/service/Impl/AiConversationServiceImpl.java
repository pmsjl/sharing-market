package com.pmsjl.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.mapper.AiConversationMapper;
import com.pmsjl.mapper.AiMessageMapper;
import com.pmsjl.model.dto.ai.AiConversationQueryRequest;
import com.pmsjl.model.dto.ai.AiShoppingContext;
import com.pmsjl.model.entity.AiConversation;
import com.pmsjl.model.entity.AiMessage;
import com.pmsjl.model.entity.User;
import com.pmsjl.model.enums.AiConversationSceneEnum;
import com.pmsjl.model.enums.AiConversationStatusEnum;
import com.pmsjl.model.vo.AiConversationVO;
import com.pmsjl.model.vo.AiPageVO;
import com.pmsjl.service.AiConversationService;
import com.pmsjl.service.AiMessageService;
import com.pmsjl.service.UserService;
import com.pmsjl.utils.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class AiConversationServiceImpl extends ServiceImpl<AiConversationMapper, AiConversation>
        implements AiConversationService {

    private static final int MAX_PAGE_SIZE = 20;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "lastMessageTime", "createTime", "updateTime", "id");

    @Autowired
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AiMessageMapper aiMessageMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteConversation(Long conversationId, HttpServletRequest request) {
        ThrowUtils.throwIf(conversationId == null || conversationId <= 0, ErrorCode.PARAMS_ERROR,
                "conversationId 必须为正整数");
        User loginUser = userService.getLoginUser(request);
        AiConversation conversation = baseMapper.selectOwnedByIdForUpdate(conversationId, loginUser.getId());
        ThrowUtils.throwIf(conversation == null, ErrorCode.NOT_FOUND_ERROR, "会话不存在或已删除");

        int deletedRows = aiMessageMapper.delete(
                new LambdaQueryWrapper<AiMessage>()
                        .eq(AiMessage::getConversationId, conversationId)
                        .eq(AiMessage::getUserId, loginUser.getId())
        );
        ThrowUtils.throwIf(deletedRows == 0, ErrorCode.OPERATION_ERROR, "删除AI会话消息失败");
        ThrowUtils.throwIf(baseMapper.deleteById(conversationId) != 1,
                ErrorCode.OPERATION_ERROR, "删除 AI 会话失败");
        return true;
    }

    @Override
    public AiPageVO<AiConversationVO> listMyConversations(AiConversationQueryRequest queryRequest,
                                                          HttpServletRequest request) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        int current = queryRequest.getCurrent();
        int pageSize = queryRequest.getPageSize();
        String sortField = queryRequest.getSortField();
        String sortOrder = queryRequest.getSortOrder();
        if (current <= 0) {
            current = 1;
        }
        if (pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
            pageSize = 10;
        }

        User loginUser = userService.getLoginUser(request);
        Page<AiConversation> page = new Page<>(current, pageSize);
        boolean ascending = "asc".equalsIgnoreCase(sortOrder);
        if (StringUtils.isNotBlank(sortField) && ALLOWED_SORT_FIELDS.contains(sortField)) {
            page.addOrder(ascending ? OrderItem.asc(sortField) : OrderItem.desc(sortField));
            if (!"id".equals(sortField)) {
                page.addOrder(OrderItem.desc("id"));
            }
        } else {
            page.addOrder(OrderItem.desc("lastMessageTime"), OrderItem.desc("id"));
        }
        Page<AiConversation> entityPage = this.lambdaQuery()
                .eq(AiConversation::getUserId, loginUser.getId())
                .eq(queryRequest.getScene() != null, AiConversation::getScene,
                        queryRequest.getScene() == null ? null : queryRequest.getScene().getValue())
                .eq(queryRequest.getStatus() != null, AiConversation::getStatus,
                        queryRequest.getStatus() == null ? null : queryRequest.getStatus().getValue())
                .page(page);


        List<AiConversationVO> records = entityPage.getRecords().stream()
                .map(this::toConversationVO)
                .toList();

        AiPageVO<AiConversationVO> result = new AiPageVO<>();
        result.setCurrent(entityPage.getCurrent());
        result.setPageSize(entityPage.getSize());
        result.setTotal(entityPage.getTotal());
        result.setRecords(records);
        return result;
    }

    private AiConversationVO toConversationVO(AiConversation conversation) {
        AiConversationVO conversationVO = new AiConversationVO();
        BeanUtils.copyProperties(conversation, conversationVO);
        conversationVO.setScene(AiConversationSceneEnum.fromValue(conversation.getScene()));
        conversationVO.setShoppingContext(deserializeShoppingContext(conversation.getShoppingContext()));
        conversationVO.setStatus(AiConversationStatusEnum.fromValue(conversation.getStatus()));
        return conversationVO;
    }

    private AiShoppingContext deserializeShoppingContext(String shoppingContext) {
        if (StringUtils.isBlank(shoppingContext)) {
            return null;
        }
        try {
            return objectMapper.readValue(shoppingContext, AiShoppingContext.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "会话购买条件解析失败");
        }
    }
}
