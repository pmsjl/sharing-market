package com.pmsjl.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.common.Result;
import com.pmsjl.model.dto.privateMessage.PrivateMessageAddRequest;
import com.pmsjl.model.dto.privateMessage.PrivateMessageQueryRequest;
import com.pmsjl.model.vo.PrivateMessageVO;
import com.pmsjl.service.PrivateMessageService;
import com.pmsjl.utils.ResultUtils;
import com.pmsjl.utils.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author pmsjl
 * @since 2026-07-08
 */
@RestController
@RequestMapping("/privateMessage")
public class PrivateMessageController {
    @Autowired
    private PrivateMessageService privateMessageService;

    @PostMapping("/add")
    public Result<Long> addPrivateMessage(@RequestBody PrivateMessageAddRequest privateMessageAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(privateMessageAddRequest==null, ErrorCode.PARAMS_ERROR);
        Long messageId=privateMessageService.addPrivateMessage(privateMessageAddRequest,request);
        return ResultUtils.success(messageId);
    }

    @PostMapping("/my/list/page/vo")
    public Result<Page<PrivateMessageVO>> listMyPrivateMessageVOByPage(@RequestBody PrivateMessageQueryRequest privateMessageQueryRequest,
                                                                             HttpServletRequest request) {
        ThrowUtils.throwIf(privateMessageQueryRequest==null,ErrorCode.PARAMS_ERROR);
        Page<PrivateMessageVO>page=privateMessageService.listMyPrivateMessageVOByPage(privateMessageQueryRequest,request);
        return ResultUtils.success(page);
    }

    }