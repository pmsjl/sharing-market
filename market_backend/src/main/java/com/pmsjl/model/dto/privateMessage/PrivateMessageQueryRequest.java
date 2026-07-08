package com.pmsjl.model.dto.privateMessage;

import com.pmsjl.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
public class PrivateMessageQueryRequest extends PageRequest implements Serializable {

    /**
     * Contact user id in the current user's private conversation.
     */
    private Long contactUserId;

    private static final long serialVersionUID = 1L;
}
