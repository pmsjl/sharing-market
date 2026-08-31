package com.pmsjl.model.dto.user;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserUpdateMyRequest implements Serializable {
    private String userName;
    private String userAvatar;
    private String userProfile;

    private static final long serialVersionUID = 1L;
}
