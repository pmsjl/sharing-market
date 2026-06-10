package com.pmsjl.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.common.DeleteRequest;
import com.pmsjl.model.dto.notice.NoticeAddRequest;
import com.pmsjl.model.dto.notice.NoticeQueryRequest;
import com.pmsjl.model.dto.notice.NoticeUpdateRequest;
import com.pmsjl.model.entity.Notice;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pmsjl.model.vo.NoticeVO;
import jakarta.servlet.http.HttpServletRequest;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author pmsjl
 * @since 2026-06-09
 */
public interface NoticeService extends IService<Notice> {

    Long addNotice(NoticeAddRequest noticeAddRequest, HttpServletRequest request);

    void validNotice(Notice notice);

    Boolean deleteNotice(DeleteRequest deleteRequest, HttpServletRequest request);

    Boolean updateNotice(NoticeUpdateRequest noticeUpdateRequest, HttpServletRequest request);

    NoticeVO getNoticeVO(long id, HttpServletRequest request);

    Page<NoticeVO> listNoticeVOByPage(NoticeQueryRequest noticeQueryRequest, HttpServletRequest request);

}
