package com.pmsjl.model.dto.notice;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建公告请求
 *
 * @author 
 * @from <a href=""> 
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