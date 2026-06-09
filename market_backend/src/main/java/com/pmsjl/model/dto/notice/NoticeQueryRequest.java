package com.pmsjl.model.dto.notice;

import com.pmsjl.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 查询公告请求
 *
 * @author 
 * @from <a href=""> 
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class NoticeQueryRequest extends PageRequest implements Serializable {

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

    /**
     * 发布公告的管理员 id
     */
    private Long noticeAdminId;

    private static final long serialVersionUID = 1L;
}