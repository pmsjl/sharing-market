package com.pmsjl.model.dto.notice;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建公告请求
 *
 * @author 程序员小白条
 * @from <a href="https://luoye6.github.io/"> 个人博客
 */
@Data
public class NoticeAddRequest implements Serializable {


    /**
     * 公告标题
     */
    private String noticeTitle;

    /**
     * 公告内容
     */
    private String noticeContent;

// 这里删除了adminId，进行手动传递


    private static final long serialVersionUID = 1L;
}