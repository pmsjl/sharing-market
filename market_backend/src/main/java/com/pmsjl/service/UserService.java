package com.pmsjl.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pmsjl.model.dto.user.*;
import com.pmsjl.model.entity.User;
import com.pmsjl.model.vo.LoginUserVO;
import com.pmsjl.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * <p>
 * 用户 服务类
 * </p>
 *
 * @author pmsjl
 * @since 2026-04-04
 */
public interface UserService extends IService<User> {

    Page<User> listUserByPage(UserQueryRequest userQueryRequest);

    List<UserVO> getUserVOList(List<User> userList);

    LoginUserVO userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request);

    boolean userLogout(HttpServletRequest request);

    Long userRegister(UserRegisterRequest userRegisterRequest);

    Long addUser(UserAddRequest userAddRequest);

    boolean updateUser(UserUpdateRequest userUpdateRequest);

    public <VO> VO getVOFromUser(User user, VO type);

    User getLoginUser(HttpServletRequest request);

    boolean updateMyUser(UserUpdateRequest userUpdateRequest, HttpServletRequest request);

    boolean isAdmin(HttpServletRequest request);


    User getByIdWithLock(Long id);
}
