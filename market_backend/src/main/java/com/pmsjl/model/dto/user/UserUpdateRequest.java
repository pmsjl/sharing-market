package com.pmsjl.model.dto.user;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class UserUpdateRequest implements Serializable {
    /**
     * id
     */
    private Long id;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 简介
     */
    private String userProfile;

    /**
     * 用户角色：user/admin/ban
     */
    private String userRole;
    /**
     * 用户余额
     */
    private BigDecimal balance;
    private static final long serialVersionUID = 1L;
}
