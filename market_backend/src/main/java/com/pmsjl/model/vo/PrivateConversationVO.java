package com.pmsjl.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.util.Date;

/** A conversation derived from existing messages; no persisted read status. */
@Data
public class PrivateConversationVO {
    private Long contactUserId;
    private String userName;
    private String userAvatar;
    private Long lastMessageId;
    private String lastMessageContent;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lastMessageTime;
    private Long lastReceivedMessageId;
}
