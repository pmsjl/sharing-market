package com.pmsjl.controller;

import com.pmsjl.common.Result;
import com.pmsjl.model.entity.User;
import com.pmsjl.model.vo.AiQuotaVO;
import com.pmsjl.service.AiAccessService;
import com.pmsjl.service.UserService;
import com.pmsjl.utils.ResultUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/quota")
@RequiredArgsConstructor
public class AiQuotaController {
    private final AiAccessService aiAccessService;
    private final UserService userService;

    @GetMapping("/me")
    public Result<AiQuotaVO> getMyQuota(HttpServletRequest request) {
        User loginUser = userService.getLoginUser();
        return ResultUtils.success(aiAccessService.getMyQuota(loginUser.getId()));
    }
}
