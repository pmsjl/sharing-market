package com.pmsjl.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.annotation.AuthCheck;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.common.PageRequest;
import com.pmsjl.common.Result;
import com.pmsjl.constant.UserConstant;
import com.pmsjl.model.dto.campusCoin.CampusCoinGrantRequest;
import com.pmsjl.model.entity.User;
import com.pmsjl.model.vo.CampusCoinTransactionVO;
import com.pmsjl.model.vo.CampusCoinWalletVO;
import com.pmsjl.service.CampusCoinService;
import com.pmsjl.service.UserService;
import com.pmsjl.utils.ResultUtils;
import com.pmsjl.utils.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/campusCoin")
@RequiredArgsConstructor
public class CampusCoinController {
    private final CampusCoinService campusCoinService;
    private final UserService userService;

    @GetMapping("/me")
    public Result<CampusCoinWalletVO> getMyWallet(HttpServletRequest request) {
        User loginUser = userService.getLoginUser();
        return ResultUtils.success(campusCoinService.getMyWallet(loginUser.getId()));
    }

    @PostMapping("/my/transactions/page")
    public Result<Page<CampusCoinTransactionVO>> listMyTransactions(
            @RequestBody(required = false) PageRequest pageRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser();
        PageRequest normalized = pageRequest == null ? new PageRequest() : pageRequest;
        return ResultUtils.success(campusCoinService.listMyTransactions(loginUser.getId(), normalized));
    }

    @PostMapping("/admin/grant")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public Result<CampusCoinWalletVO> grantByAdmin(
            @RequestBody CampusCoinGrantRequest grantRequest,
            HttpServletRequest request) {
        ThrowUtils.throwIf(grantRequest == null || grantRequest.getUserId() == null || grantRequest.getUserId() <= 0,
                ErrorCode.PARAMS_ERROR, "用户 ID 非法");
        User operator = userService.getLoginUser();
        return ResultUtils.success(campusCoinService.grantByAdmin(grantRequest, operator.getId()));
    }
}
