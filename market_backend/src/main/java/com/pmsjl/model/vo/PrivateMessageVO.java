package com.pmsjl.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.pmsjl.model.entity.PrivateMessage;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Date;

/**
 * 私信表视图
 *
 * @author 
 * @from <a href=""> 
 */
@Data
public class PrivateMessageVO implements Serializable {
    /**
     * 消息 ID
     */
    private Long id;

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
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
    private Date createTime;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
    private Date updateTime;


    private static final long serialVersionUID = 1L;
    /**
     * 封装类转对象
     *
     * @param privateMessageVO
     * @return
     */
    public static PrivateMessage voToObj(PrivateMessageVO privateMessageVO) {
        if (privateMessageVO == null) {
            return null;
        }
        PrivateMessage privateMessage = new PrivateMessage();
        BeanUtils.copyProperties(privateMessageVO, privateMessage);
        return privateMessage;
    }

    /**
     * 对象转封装类
     *
     * @param privateMessage
     * @return
     */
    public static PrivateMessageVO objToVo(PrivateMessage privateMessage) {
        if (privateMessage == null) {
            return null;
        }
        PrivateMessageVO privateMessageVO = new PrivateMessageVO();
        BeanUtils.copyProperties(privateMessage, privateMessageVO);
        return privateMessageVO;
    }
}
