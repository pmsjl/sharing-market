package com.pmsjl.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.common.JwtKit;
import com.pmsjl.common.JwtProperties;
import com.pmsjl.common.Result;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.model.dto.user.*;
import com.pmsjl.model.entity.User;
import com.pmsjl.model.vo.LoginUserVO;
import com.pmsjl.model.vo.UserVO;
import com.pmsjl.service.UserService;
import com.pmsjl.utils.ResultUtils;
import com.pmsjl.utils.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;

/**
 * <p>
 * 用户 前端控制器
 * </p>
 *
 * @author pmsjl
 * @since 2026-04-04
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final JwtProperties jwtProperties;

    /***
     * 添加用户
     *
     * @param userAddRequest
     * @return
     */
    // TODO 添加用户，实际前端尚未添加此功能组件
    @PostMapping("/add")
    public Result<Long> addUser(@RequestBody UserAddRequest userAddRequest) {
        if (userAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (userAddRequest.getUserAccount().length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名长度少于4位，无法创建");
        }
        Long id = userService.addUser(userAddRequest);
        return ResultUtils.success(id);
    }

    /***
     * //删除用户
     *
     * @param deleteRequest
     * @return
     */
    @PostMapping("/delete")
    public Result<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) {
        Long id = deleteRequest.getId();
        if (id == null || id < 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean result = userService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /***
     * //根据id查询用户user
     *
     * @param id
     * @return
     */
    @GetMapping("/get")
    public Result<User> getUserById(@RequestParam("id") Long id) {
        if (id == null || id < 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(user);
    }

    /***
     * //获取当前登录的用户
     *
     * @return
     */
    @GetMapping("/get/login")
    public Result<LoginUserVO> getLoginUser(HttpServletRequest request) {
        ThrowUtils.throwIf(request==null,ErrorCode.PARAMS_ERROR);
        User user=userService.getLoginUser(request);
        LoginUserVO loginUserVO = userService.getVOFromUser(user, new LoginUserVO());
        return ResultUtils.success(loginUserVO);
    }

    /***
     * //根据id查询用户部分信息userVO
     *
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    public Result<UserVO> getUserVOById(Long id) {
        if (id == null || id < 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return ResultUtils.success(userVO);

    }

    /***
     * 分页获取用户列表
     */
    @PostMapping("/list/page")
    public Result<Page<User>> listUserByPage(@RequestBody UserQueryRequest userQueryRequest) {
        Page<User> userPage = userService.listUserByPage(userQueryRequest);
        return ResultUtils.success(userPage);
    }

    /***
     * 分页获取部分用户信息的列表
     */

    @PostMapping("/list/page/vo")
    public Result<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest) {
        Page<User> userPage = userService.listUserByPage(userQueryRequest);
        List<User> userList = userPage.getRecords();
        List<UserVO> userVOList = userService.getUserVOList(userList);
        Page<UserVO> userVOPage = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        userVOPage.setPages(userPage.getPages());
        userVOPage.setRecords(userVOList);
        return ResultUtils.success(userVOPage);
    }

    /***
     * 用户登录
     */
    @PostMapping("/login")
    public Result<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        if (userLoginRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        //登录校验
        LoginUserVO loginuserVO = userService.userLogin(userLoginRequest, request);
        //登陆状态字段设置完成
        //获取token记录到哈希表返回
        JwtKit jwtKit = new JwtKit(jwtProperties);
        String token = jwtKit.generateToken(loginuserVO);
        HashMap<String, Object> hashMap = new HashMap<>(0);
        hashMap.put("token", token);
        //由于用户登录需要作各种校验功能所以需要自定义方法
        //这里调用第二种返回方式，包含hashmap
        return ResultUtils.successDynamic(loginuserVO, hashMap);
    }

    /***
     * 用户注销
     * @return
     */
    @PostMapping("/logout")
    public Result<Boolean> userLogout(HttpServletRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        boolean result = userService.userLogout(request);
        return ResultUtils.success(result);

    }

    /***
     * 用户注册
     */
    @PostMapping("/register")
    public Result<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        ThrowUtils.throwIf(userRegisterRequest == null, ErrorCode.PARAMS_ERROR);
        Long result = userService.userRegister(userRegisterRequest);
        return ResultUtils.success(result);
    }

    /***
     * 更新用户信息
     *
     */
    @PostMapping("/update")
    public Result<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest) {
        if (userUpdateRequest == null || userUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean result = userService.updateUser(userUpdateRequest);
        return ResultUtils.success(result);

    }

    /***
     * 更新个人信息
     */
    @PostMapping("/update/my")
    public Result<Boolean> updateMyUser(@RequestBody UserUpdateRequest userUpdateRequest,HttpServletRequest request) {
        if (userUpdateRequest == null||request==null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean result = userService.updateMyUser(userUpdateRequest,request);
        return ResultUtils.success(result);

    }
}
