package com.pmsjl.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.annotation.AuthCheck;
import com.pmsjl.common.DeleteRequest;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.common.Result;
import com.pmsjl.constant.UserConstant;
import com.pmsjl.model.dto.notice.NoticeAddRequest;
import com.pmsjl.model.dto.notice.NoticeQueryRequest;
import com.pmsjl.model.dto.notice.NoticeUpdateRequest;
import com.pmsjl.model.vo.NoticeVO;
import com.pmsjl.service.NoticeService;
import com.pmsjl.utils.ResultUtils;
import com.pmsjl.utils.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author pmsjl
 * @since 2026-06-09
 */
@RestController
@RequestMapping("/notice")
public class NoticeController {
    @Autowired
    NoticeService noticeService;

    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public Result<Long> addNotice(@RequestBody NoticeAddRequest noticeAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(noticeAddRequest==null, ErrorCode.PARAMS_ERROR);
        Long id=noticeService.addNotice(noticeAddRequest,request);
        return ResultUtils.success(id);
    }

    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public Result<Boolean> deleteNotice(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request){
        ThrowUtils.throwIf(deleteRequest==null,ErrorCode.PARAMS_ERROR);
        Boolean result=noticeService.deleteNotice(deleteRequest,request);
        return ResultUtils.success(result);
    }

    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public Result<Boolean> updateNotice(@RequestBody NoticeUpdateRequest noticeUpdateRequest,HttpServletRequest request) {
        ThrowUtils.throwIf(noticeUpdateRequest==null,ErrorCode.PARAMS_ERROR);
        Boolean result=noticeService.updateNotice(noticeUpdateRequest,request);
        return ResultUtils.success(result);
    }

    @GetMapping("/get/vo")
    public Result<NoticeVO> getNoticeVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        NoticeVO noticeVO=noticeService.getNoticeVO(id, request);
        // 获取封装类
        return ResultUtils.success(noticeVO);
    }


    @PostMapping("list/page/vo")
    public Result<Page<NoticeVO>> listNoticeVOByPage(@RequestBody NoticeQueryRequest noticeQueryRequest,
                                                           HttpServletRequest request) {
        ThrowUtils.throwIf(noticeQueryRequest==null,ErrorCode.PARAMS_ERROR);
        Page<NoticeVO>page=noticeService.listNoticeVOByPage(noticeQueryRequest,request);
        return ResultUtils.success(page);
    }

}
