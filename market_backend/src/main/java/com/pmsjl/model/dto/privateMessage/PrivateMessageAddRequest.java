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
     * 接收者 ID
     */
    private Long recipientId;

    /**
     * 消息内容
     */
    private String content;




    private static final long serialVersionUID = 1L;
}