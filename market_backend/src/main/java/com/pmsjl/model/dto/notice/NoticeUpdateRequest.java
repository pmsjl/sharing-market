package com.pmsjl.model.dto.notice;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新公告请求
 *
 * @author 程序员小白条
 * @from <a href="https://luoye6.github.io/"> 个人博客
 */
@Data
public class NoticeUpdateRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 公告标题
     */
    private String noticeTitle;

    /**
     * 公告内容
     */
    private String noticeContent;

//我删除了这里的adminId，谁更新就应该赋什么值
    private static final long serialVersionUID = 1L;
}