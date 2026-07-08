package com.pmsjl.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.model.dto.privateMessage.PrivateMessageAddRequest;
import com.pmsjl.model.dto.privateMessage.PrivateMessageQueryRequest;
import com.pmsjl.model.entity.PrivateMessage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pmsjl.model.vo.PrivateMessageVO;
import jakarta.servlet.http.HttpServletRequest;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author pmsjl
 * @since 2026-07-08
 */
public interface PrivateMessageService extends IService<PrivateMessage> {

    Long addPrivateMessage(PrivateMessageAddRequest privateMessageAddRequest, HttpServletRequest request);

    Page<PrivateMessageVO> listMyPrivateMessageVOByPage(PrivateMessageQueryRequest privateMessageQueryRequest, HttpServletRequest request);
}
