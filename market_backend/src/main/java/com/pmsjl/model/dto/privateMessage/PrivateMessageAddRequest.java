package com.pmsjl.model.dto.privateMessage;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建私信表请求
 *
 * @author 程序员小白条
 * @from <a href="https://luoye6.github.io/"> 个人博客
 */
@Data
public class PrivateMessageAddRequest implements Serializable {

    /**
     * 发送者 ID
     */
    private Long senderId;

    /**
     * 接收者 ID
     */
    private Long recipientId;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 0-未阅读 1-已阅读
     */
    private Integer alreadyRead;

    /**
     * 消息发送类型（用户发送还是管理员发送,user Or admin)枚举
     */
    private String type;

    /**
     * 是否撤回  0-未撤回 1-已撤回
     */
    private Integer isRecalled;



    private static final long serialVersionUID = 1L;
}