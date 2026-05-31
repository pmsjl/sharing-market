package com.pmsjl.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateTime;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.mapper.UserMapper;
import com.pmsjl.model.dto.user.*;
import com.pmsjl.model.entity.User;
import com.pmsjl.model.enums.UserRoleEnum;
import com.pmsjl.model.vo.LoginUserVO;
import com.pmsjl.model.vo.UserVO;
import com.pmsjl.service.UserService;
import com.pmsjl.utils.ThrowUtils;
import com.pmsjl.utils.TokenUtils;
import com.pmsjl.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.math.BigDecimal;
import java.util.List;
import static com.pmsjl.constant.RedisConstants.*;
/**
 * <p>
 * 用户 服务实现类
 * </p>
 *
 * @author pmsjl
 * @since 2026-04-04
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    public static final String SALT = "pmsjl";
    public final StringRedisTemplate stringRedisTemplate;


    /**
     * 是否为管理员
     *那你就问了，已经有admin注解加上aop了，为什么还要单独再实现一边逻辑
     * 因为可能出现除了admin还有其他用户也能访问的情况，这时候就不能只通过注解去限制了
     * 而是在类的内部进行条件判断
     * @param request
     * @return
     */
    @Override
    public boolean isAdmin(HttpServletRequest request) {
        // 仅管理员可查询
        User user = getLoginUser(request);
        return UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }



    @Override
    public Long addUser(UserAddRequest userAddRequest) {
        User user = new User();
        BeanUtils.copyProperties(userAddRequest, user);
        // 默认密码 12345678
        String defaultPassword = "12345678";
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + defaultPassword).getBytes());
        // 这里的采取了十六进制加密算法填入表中的并不是简单的12345678
        user.setUserPassword(encryptPassword);
        user.setCreateTime(DateTime.now());
        user.setUpdateTime(DateTime.now());
        boolean result = save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return user.getId();
    }

    @Override
    public boolean updateUser(UserUpdateRequest userUpdateRequest) {
        User user=new User();
        BeanUtil.copyProperties(userUpdateRequest,user);
        boolean result = updateById(user);
        ThrowUtils.throwIf(!result,ErrorCode.OPERATION_ERROR,"数据库操作失败");
        return result;


    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        User user=new User();
        BeanUtil.copyProperties(UserHolder.getUser(),user);
        if(user==null||user.getId()==null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"登录用户不存在");
        }
        ThrowUtils.throwIf(user==null,ErrorCode.NOT_FOUND_ERROR);
        return user;
    }

    @Override
    public boolean updateMyUser(UserUpdateRequest userUpdateRequest, HttpServletRequest request) {
        User loginUser = getLoginUser(request);
        User user=new User();
        BeanUtil.copyProperties(userUpdateRequest,user);
        user.setId(loginUser.getId());
        boolean b = updateById(user);
        ThrowUtils.throwIf(b==false,ErrorCode.OPERATION_ERROR);
        return b;


    }

    @Override
    public Page<User> listUserByPage(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        int current = userQueryRequest.getCurrent();
        int pageSize = userQueryRequest.getPageSize();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        if (current <= 0) current = 1;
        if (pageSize <= 0 || pageSize > 100) pageSize = 10;
        Page<User> page = new Page<>(current, pageSize);
        if (sortField != null && !sortField.trim().isEmpty()) {
            if ("asc".equalsIgnoreCase(sortOrder)) {
                page.addOrder(OrderItem.asc(sortField));
            } else {
                page.addOrder(OrderItem.desc(sortField));
            }
        } else {
            // 默认按更新时间降序
            page.addOrder(OrderItem.desc("updateTime"));
        }
        return lambdaQuery()
                // id 精确匹配（通常用于精确查找）
                .eq(userQueryRequest.getId() != null && userQueryRequest.getId() > 0, User::getId, userQueryRequest.getId())

                // 用户名模糊搜索（最常用）
                .like(StringUtils.isNotBlank(userQueryRequest.getUserName()), User::getUserName, userQueryRequest.getUserName())

                // 用户角色精确匹配
                .eq(StringUtils.isNotBlank(userQueryRequest.getUserRole()), User::getUserRole, userQueryRequest.getUserRole())

                // 用户简介模糊搜索
                .like(StringUtils.isNotBlank(userQueryRequest.getUserProfile()), User::getUserProfile, userQueryRequest.getUserProfile())

                // 其他字段按需加（根据你实际业务决定）
                .eq(StringUtils.isNotBlank(userQueryRequest.getMpOpenId()), User::getMpOpenId, userQueryRequest.getMpOpenId())
                .eq(StringUtils.isNotBlank(userQueryRequest.getUnionId()), User::getUnionId, userQueryRequest.getUnionId())

                // 余额和AI次数一般不做模糊搜索，这里用精确匹配（也可以改成范围查询）
                .eq(userQueryRequest.getBalance() != null && userQueryRequest.getBalance().compareTo(BigDecimal.ZERO) > 0,
                        User::getBalance, userQueryRequest.getBalance())
                .eq(userQueryRequest.getAiRemainNumber() != null && userQueryRequest.getAiRemainNumber() > 0,
                        User::getAiRemainNumber, userQueryRequest.getAiRemainNumber())

                // 执行分页
                .page(page);
    }

    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        List<UserVO> list = userList.stream().map((user) -> {
            UserVO userVO = new UserVO();
            BeanUtil.copyProperties(user, userVO);
            return userVO;
        }).toList();
        return list;

    }

    public <VO> VO getVOFromUser(User user, VO type) {
        BeanUtil.copyProperties(user, type);
        return type;
    }


    @Override
    public LoginUserVO userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request) {
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        //先校验
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请输入完整信息");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号错误");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }
        //然后查询
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        User user = lambdaQuery().eq(User::getUserAccount, userAccount).
                eq(User::getUserPassword, encryptPassword).one();
        if (user == null) {
            log.info("user login failed, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        // 在请求体记录用户的登录态。用于后续查询登录用户信息的判断使用
        //前者为字符串，后者为类封装成json后续提取
        return getVOFromUser(user,new LoginUserVO());

    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        User user=new User();
        LoginUserVO loginUserVO = UserHolder.getUser();
        ThrowUtils.throwIf(ObjectUtils.isNull(loginUserVO)||ObjectUtils.isEmpty(loginUserVO),ErrorCode.NOT_LOGIN_ERROR,"用户信息不存在");
        BeanUtil.copyProperties(loginUserVO,user);
        String token = TokenUtils.getToken(request);
        stringRedisTemplate.delete(LOGIN_USER_KEY+token);
        return true;
    }

    @Override
    public Long userRegister(UserRegisterRequest userRegisterRequest) {
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请输入完整信息");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号错误");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入密码不一致，请重新输入");
        }
        //原表user的account属性并没有做所谓的unique约束，仅通过synchronized的悲观锁进行约束
        //遇到集群就完蛋，同时添加大量useraccount的字符串常量到jvm中
        //修改：在表中加入unique约束，同时在这里直接采取mysql获取成功与否进行判断
        User user = new User();
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        String username=userAccount;
        user.setUserPassword(encryptPassword);
        user.setUserAccount(userAccount);
        user.setUserName(username);
        //这里我自己加上了username，因为后续都要用到，先默认和account一致
        user.setUpdateTime(DateTime.now());
        user.setCreateTime(DateTime.now());
        boolean result = save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "数据库操作失败，请重试");
        return user.getId();



    }



}
