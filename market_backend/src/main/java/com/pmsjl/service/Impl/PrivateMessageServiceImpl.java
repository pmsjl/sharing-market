package com.pmsjl.service.Impl;


import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.model.dto.privateMessage.PrivateMessageAddRequest;
import com.pmsjl.model.dto.privateMessage.PrivateMessageQueryRequest;
import com.pmsjl.model.entity.Notice;
import com.pmsjl.model.entity.PrivateMessage;
import com.pmsjl.mapper.PrivateMessageMapper;
import com.pmsjl.model.entity.User;
import com.pmsjl.model.vo.PrivateMessageVO;
import com.pmsjl.service.PrivateMessageService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pmsjl.service.UserService;
import com.pmsjl.utils.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author pmsjl
 * @since 2026-07-08
 */
@Service
public class PrivateMessageServiceImpl extends ServiceImpl<PrivateMessageMapper, PrivateMessage> implements PrivateMessageService {
    @Autowired
    UserService userService;
    private static final Set<String> ALLOWED_PRIVATE_MESSAGE_SORT_FIELDS = Set.of("id", "createTime");

    @Override
    public Long addPrivateMessage(PrivateMessageAddRequest privateMessageAddRequest, HttpServletRequest request) {
        PrivateMessage privateMessage = new PrivateMessage();
        BeanUtils.copyProperties(privateMessageAddRequest, privateMessage);
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        privateMessage.setSenderId(userId);
        validPrivateMessage(privateMessage);
        privateMessage.setType(loginUser.getUserRole());
        privateMessage.setAlreadyRead(0);
        privateMessage.setIsRecalled(0);
        privateMessage.setContent(privateMessage.getContent().trim());
        boolean result = save(privateMessage);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return privateMessage.getId();

    }

    @Override
    public Page<PrivateMessageVO> listMyPrivateMessageVOByPage(PrivateMessageQueryRequest privateMessageQueryRequest, HttpServletRequest request) {
        int current = privateMessageQueryRequest.getCurrent();
        int pageSize = privateMessageQueryRequest.getPageSize();
        String sortField = privateMessageQueryRequest.getSortField();
        String sortOrder = privateMessageQueryRequest.getSortOrder();
        Long contactUserId = privateMessageQueryRequest.getContactUserId();
        ThrowUtils.throwIf(contactUserId == null || contactUserId <= 0, ErrorCode.PARAMS_ERROR);
        User contactUser = userService.getById(contactUserId);
        ThrowUtils.throwIf(contactUser == null, ErrorCode.NOT_FOUND_ERROR, "联系人不存在");

        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();

        if (current <= 0) current = 1;
        if (pageSize <= 0 || pageSize > 100) pageSize = 10;
        Page<PrivateMessage> page = new Page<>(current, pageSize);
        if (!StringUtils.isBlank(sortField) && ALLOWED_PRIVATE_MESSAGE_SORT_FIELDS.contains(sortField)) {
            if ("asc".equalsIgnoreCase(sortOrder)) {
                page.addOrder(OrderItem.asc(sortField));
            } else {
                page.addOrder(OrderItem.desc(sortField));
            }
        } else {
            page.addOrder(OrderItem.desc("createTime"));
        }
        LambdaQueryWrapper<PrivateMessage> queryWrapper = new LambdaQueryWrapper<>();
        LambdaQueryWrapper<PrivateMessage> privateMessageWrapper = queryWrapper.
                and(wrapper ->wrapper.
                        and(
                                w->w.eq(PrivateMessage::getSenderId,contactUserId).
                                        eq(PrivateMessage::getRecipientId,userId)).
                        or(w->w.eq(PrivateMessage::getRecipientId,contactUserId).
                                        eq(PrivateMessage::getSenderId,userId)));

        Page<PrivateMessage> privateMessagePage = this.page(page, privateMessageWrapper);
        List<PrivateMessage> records = privateMessagePage.getRecords();
        long total = privateMessagePage.getTotal();
        Page<PrivateMessageVO> privateMessageVOPage = new Page<>(current, pageSize, total);
        List<PrivateMessageVO> list = records.stream().map(PrivateMessageVO::objToVo).toList();
        privateMessageVOPage.setRecords(list);
        return privateMessageVOPage;
    }

    private void validPrivateMessage(PrivateMessage privateMessage) {
        String content = privateMessage.getContent();
        Long recipientId = privateMessage.getRecipientId();
        Long senderId = privateMessage.getSenderId();
        ThrowUtils.throwIf(StringUtils.isBlank(content), ErrorCode.PARAMS_ERROR, "发送消息不可为空");
        ThrowUtils.throwIf(content.length() > 1024, ErrorCode.PARAMS_ERROR, "发送消息过长，不可超过1024个字符");
        ThrowUtils.throwIf(recipientId == null || recipientId <= 0, ErrorCode.PARAMS_ERROR);
        User recipient = userService.getById(recipientId);
        ThrowUtils.throwIf(recipient == null, ErrorCode.NOT_FOUND_ERROR, "接收者不存在");
        ThrowUtils.throwIf(recipientId.equals(senderId), ErrorCode.PARAMS_ERROR, "不可给自己私信");
    }
}
